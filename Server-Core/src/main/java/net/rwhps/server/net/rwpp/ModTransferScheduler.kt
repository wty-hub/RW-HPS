package net.rwhps.server.net.rwpp

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.rwhps.server.io.packet.Packet
import net.rwhps.server.net.rwpp.packet.RwppModPacket
import java.io.InputStream
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

enum class ModTransferReadyState { NOT_REQUIRED, WAITING_REQUEST, TRANSFERRING, WAITING_RELOAD, READY, FAILED }

/**
 * 与 TXJS [io.github.rwpp.net.HostModTransferScheduler] 对齐的流控参数。
 *
 * [requestDebounceMs]：TXJS 客户端可能在短时间内多次 ModCheckEvent/500。
 * 每次请求会清空其接收序号；若服务端立刻灌包，上一轮残留分块会打到新代次上引发 out of order。
 * 因此每次 500 先停发，防抖后再从 chunk 0 开传（只改 HPS，不改 TXJS）。
 */
data class ModTransferConfig(
    /** TXJS 客户端对每个分块并行 handleChunk 且要求严格按序；窗口>1 易出现 expected N got N+1。默认 1。 */
    val windowSize: Int = 1,
    val ackTimeoutMs: Long = 10_000,
    val sessionTimeoutMs: Long = 300_000,
    val maxConcurrent: Int = 4,
    val pollWhenBlockedMs: Long = 3,
    val requestDebounceMs: Long = 250,
)

fun interface ModChunkSender { suspend fun send(connectionId: String, packet: Packet) }
fun interface ModSource { fun open(entry: ModCatalogEntry): InputStream }

class ModTransferScheduler(
    private val sender: ModChunkSender,
    private val config: ModTransferConfig,
    private val source: ModSource = ModSource { Files.newInputStream(it.path).buffered() },
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val onFailure: (String, String) -> Unit = { _, _ -> },
    startLoop: Boolean = true,
) : AutoCloseable {
    private data class Outstanding(val modIndex: Int, val chunkIndex: Int, val sentAt: Long)
    private data class Pending(
        val playerName: String,
        val snapshot: ModCatalogSnapshot,
        val mods: List<ModCatalogEntry>,
        val startAt: Long,
    )
    private class Session(val id: String, val snapshot: ModCatalogSnapshot, val mods: List<ModCatalogEntry>) {
        var modIndex = 0
        var nextChunk = 0
        var input: InputStream? = null
        val outstanding = ArrayDeque<Outstanding>()
    }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Mutex()
    private val sessions = LinkedHashMap<String, Session>()
    private val pending = LinkedHashMap<String, Pending>()
    private val states = ConcurrentHashMap<String, ModTransferReadyState>()
    private val names = ConcurrentHashMap<String, String>()
    private val stateEnteredAt = ConcurrentHashMap<String, Long>()
    private var cursor = 0

    init {
        if (startLoop) {
            scope.launch {
                while (isActive) {
                    val sent = tick()
                    pause(if (sent) 0 else config.pollWhenBlockedMs.coerceAtLeast(1))
                }
            }
        }
    }

    fun markWaitingRequest(id: String, playerName: String) {
        names[id] = playerName
        states.compute(id) { _, old ->
            if (old == ModTransferReadyState.READY) old else ModTransferReadyState.WAITING_REQUEST.also { stateEnteredAt[id] = now() }
        }
    }

    /**
     * 收到下载请求。
     * - 已在 [ModTransferReadyState.TRANSFERRING] 且 Mod 列表相同：忽略（TXJS 中途再发 500 时若重启会停发并把进度打回 0）。
     * - 防抖等待中同列表：只刷新开传时间。
     * - 否则停发并防抖后从 chunk 0 开传。
     *
     * 配合：传输未完成时暂停 CallTeamTask，减少客户端反复 ModCheckEvent。
     */
    suspend fun replace(id: String, playerName: String, snapshot: ModCatalogSnapshot, mods: List<ModCatalogEntry>): Boolean = lock.withLock {
        names[id] = playerName
        if (mods.isEmpty()) {
            stopSessionLocked(id)
            clearPendingLocked(id)
            setState(id, ModTransferReadyState.WAITING_RELOAD)
            return true
        }
        val sameMods: (List<ModCatalogEntry>) -> Boolean = { current ->
            current.map { it.logicalName } == mods.map { it.logicalName }
        }
        val session = sessions[id]
        if (session != null && states[id] == ModTransferReadyState.TRANSFERRING && sameMods(session.mods)) {
            return true
        }
        val existingPending = pending[id]
        if (existingPending != null && sameMods(existingPending.mods)) {
            ModCatalogManager.release(existingPending.snapshot)
            ModCatalogManager.pin(snapshot)
            pending[id] = Pending(playerName, snapshot, mods, now() + config.requestDebounceMs.coerceAtLeast(0))
            setState(id, ModTransferReadyState.WAITING_REQUEST)
            return true
        }
        stopSessionLocked(id)
        clearPendingLocked(id)
        val others = (sessions.keys + pending.keys).count { it != id }
        if (others >= config.maxConcurrent) {
            setState(id, ModTransferReadyState.FAILED)
            return false
        }
        ModCatalogManager.pin(snapshot)
        pending[id] = Pending(playerName, snapshot, mods, now() + config.requestDebounceMs.coerceAtLeast(0))
        setState(id, ModTransferReadyState.WAITING_REQUEST)
        true
    }

    /** 是否仍有玩家处于等待/传输/重载中（用于暂停大厅周期性同步，避免反复触发客户端 ModCheck）。 */
    fun hasBusyTransfers(): Boolean = states.values.any {
        it == ModTransferReadyState.WAITING_REQUEST ||
            it == ModTransferReadyState.TRANSFERRING ||
            it == ModTransferReadyState.WAITING_RELOAD
    }

    /**
     * 收到 ACK 后释放对应在途块。仅精确匹配才放窗口，避免误放导致超前发送（expected N got N+1）。
     * 无匹配的陈旧 ACK 忽略，不断开连接。
     */
    suspend fun acknowledge(id: String, modName: String, chunkIndex: Int): Boolean = lock.withLock {
        val session = sessions[id] ?: return true
        val matched = session.outstanding.firstOrNull {
            it.chunkIndex == chunkIndex && session.mods.getOrNull(it.modIndex)?.logicalName == modName
        } ?: return true
        session.outstanding.remove(matched)
        advanceCompletedModLocked(session)
        true
    }

    suspend fun finishReload(id: String): Boolean = lock.withLock {
        if (states[id] != ModTransferReadyState.WAITING_RELOAD) return false
        setState(id, ModTransferReadyState.READY)
        true
    }

    suspend fun cancel(id: String) = lock.withLock { cancelLocked(id, true) }
    fun state(id: String) = states[id] ?: ModTransferReadyState.NOT_REQUIRED
    fun canStart() = states.values.all { it == ModTransferReadyState.READY || it == ModTransferReadyState.NOT_REQUIRED }
    fun pendingPlayerNames() = states.filterValues { it != ModTransferReadyState.READY && it != ModTransferReadyState.NOT_REQUIRED }.keys.mapNotNull { names[it] }.sorted()

    /** @return 本轮是否发出了分块 */
    internal suspend fun tick(): Boolean = lock.withLock {
        val time = now()
        promotePendingLocked(time)
        states.entries.toList().forEach { (id, state) ->
            if (state == ModTransferReadyState.WAITING_REQUEST || state == ModTransferReadyState.WAITING_RELOAD) {
                if (pending.containsKey(id)) return@forEach // 防抖等待不算会话超时
                val enteredAt = stateEnteredAt[id] ?: time.also { stateEnteredAt[id] = it }
                if (time - enteredAt >= config.sessionTimeoutMs) failLocked(id, "session timeout")
            }
        }
        sessions.values.toList().forEach { s ->
            val ackExpired = s.outstanding.any { time - it.sentAt >= config.ackTimeoutMs }
            val enteredAt = stateEnteredAt[s.id] ?: time.also { stateEnteredAt[s.id] = it }
            if (ackExpired || time - enteredAt >= config.sessionTimeoutMs) {
                failLocked(s.id, if (ackExpired) "ACK timeout" else "session timeout")
            }
        }
        val active = sessions.values.toList()
        if (active.isEmpty()) return@withLock false
        cursor %= active.size
        for (offset in active.indices) {
            val s = active[(cursor + offset) % active.size]
            if (s.outstanding.size >= config.windowSize) continue
            val item = try {
                readOneLocked(s)
            } catch (e: Exception) {
                failLocked(s.id, "read failed: ${e.javaClass.simpleName}")
                null
            }
            cursor = (cursor + offset + 1) % active.size
            if (item != null) {
                try {
                    sender.send(item.first, RwppModPacket.writeDownloadModChunk(item.second, item.third))
                    return@withLock true
                } catch (e: Exception) {
                    failLocked(item.first, "send failed: ${e.javaClass.simpleName}")
                }
            }
            break
        }
        false
    }

    private fun promotePendingLocked(time: Long) {
        pending.entries.toList().forEach { (id, p) ->
            if (time < p.startAt) return@forEach
            pending.remove(id)
            if (sessions.size >= config.maxConcurrent) {
                ModCatalogManager.release(p.snapshot)
                setState(id, ModTransferReadyState.FAILED)
                onFailure(id, "too many concurrent transfers")
                return@forEach
            }
            sessions[id] = Session(id, p.snapshot, p.mods)
            setState(id, ModTransferReadyState.TRANSFERRING)
        }
    }

    private fun readOneLocked(s: Session): Triple<String, RwppModPacket.ChunkMetadata, ByteArray>? {
        val entry = s.mods.getOrNull(s.modIndex) ?: return null
        val total = ((entry.size + RwppConstants.CHUNK_SIZE - 1) / RwppConstants.CHUNK_SIZE).toInt().coerceAtLeast(1)
        if (s.nextChunk >= total) return null
        val input = s.input ?: source.open(entry).also { s.input = it }
        val expected = min(RwppConstants.CHUNK_SIZE.toLong(), entry.size - s.nextChunk.toLong() * RwppConstants.CHUNK_SIZE).toInt()
        if (expected <= 0) return null
        val bytes = ByteArray(expected)
        var read = 0
        while (read < expected) {
            val n = input.read(bytes, read, expected - read)
            if (n < 0) error("mod file changed")
            read += n
        }
        val chunk = s.nextChunk++
        s.outstanding.add(Outstanding(s.modIndex, chunk, now()))
        val totalSize = if (chunk == 0) entry.size else 0L
        val sha256 = if (chunk == 0) entry.sha256 else ""
        return Triple(s.id, RwppModPacket.ChunkMetadata(entry.logicalName, chunk, total, totalSize, sha256), bytes)
    }

    private fun advanceCompletedModLocked(s: Session) {
        val entry = s.mods.getOrNull(s.modIndex) ?: return
        val total = ((entry.size + RwppConstants.CHUNK_SIZE - 1) / RwppConstants.CHUNK_SIZE).toInt().coerceAtLeast(1)
        if (s.nextChunk < total || s.outstanding.any { it.modIndex == s.modIndex }) return
        s.input?.close()
        s.input = null
        s.modIndex++
        s.nextChunk = 0
        if (s.modIndex == s.mods.size) {
            sessions.remove(s.id)
            ModCatalogManager.release(s.snapshot)
            setState(s.id, ModTransferReadyState.WAITING_RELOAD)
        }
    }

    private fun failLocked(id: String, reason: String) {
        cancelLocked(id, false)
        setState(id, ModTransferReadyState.FAILED)
        onFailure(id, reason)
    }

    private fun setState(id: String, state: ModTransferReadyState) {
        states[id] = state
        stateEnteredAt[id] = now()
    }

    private fun stopSessionLocked(id: String) {
        sessions.remove(id)?.also { ModCatalogManager.release(it.snapshot) }?.input?.runCatching { close() }
    }

    private fun clearPendingLocked(id: String) {
        pending.remove(id)?.also { ModCatalogManager.release(it.snapshot) }
    }

    private fun cancelLocked(id: String, removeState: Boolean) {
        stopSessionLocked(id)
        clearPendingLocked(id)
        if (removeState) {
            states.remove(id)
            names.remove(id)
            stateEnteredAt.remove(id)
        }
    }

    override fun close() {
        scope.cancel()
        runBlocking {
            lock.withLock {
                sessions.values.forEach {
                    ModCatalogManager.release(it.snapshot)
                    it.input?.runCatching { close() }
                }
                pending.values.forEach { ModCatalogManager.release(it.snapshot) }
                sessions.clear()
                pending.clear()
                states.clear()
                names.clear()
                stateEnteredAt.clear()
            }
        }
    }
}
