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
data class ModTransferConfig(val windowSize: Int = 32, val ackTimeoutMs: Long = 10_000, val sessionTimeoutMs: Long = 300_000, val maxConcurrent: Int = 4)
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
    private class Session(val id: String, val snapshot: ModCatalogSnapshot, val mods: List<ModCatalogEntry>) {
        var modIndex = 0
        var nextChunk = 0
        var input: InputStream? = null
        val outstanding = ArrayDeque<Outstanding>()
    }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Mutex()
    private val sessions = LinkedHashMap<String, Session>()
    private val states = ConcurrentHashMap<String, ModTransferReadyState>()
    private val names = ConcurrentHashMap<String, String>()
    private val stateEnteredAt = ConcurrentHashMap<String, Long>()
    private var cursor = 0

    init { if (startLoop) scope.launch { while (isActive) { tick(); pause(2) } } }

    fun markWaitingRequest(id: String, playerName: String) {
        names[id] = playerName
        states.compute(id) { _, old ->
            if (old == ModTransferReadyState.READY) old else ModTransferReadyState.WAITING_REQUEST.also { stateEnteredAt[id] = now() }
        }
    }

    suspend fun replace(id: String, playerName: String, snapshot: ModCatalogSnapshot, mods: List<ModCatalogEntry>): Boolean = lock.withLock {
        cancelLocked(id, false); names[id] = playerName
        if (mods.isEmpty()) { setState(id, ModTransferReadyState.WAITING_RELOAD); return true }
        if (sessions.size >= config.maxConcurrent) { setState(id, ModTransferReadyState.FAILED); return false }
        ModCatalogManager.pin(snapshot); sessions[id] = Session(id, snapshot, mods); setState(id, ModTransferReadyState.TRANSFERRING); true
    }

    suspend fun acknowledge(id: String, modName: String, chunkIndex: Int): Boolean = lock.withLock {
        val session = sessions[id] ?: return false
        val current = session.mods.getOrNull(session.modIndex) ?: return false
        if (current.logicalName != modName) return false
        val ack = session.outstanding.firstOrNull { it.modIndex == session.modIndex && it.chunkIndex == chunkIndex } ?: return false
        session.outstanding.remove(ack); advanceCompletedModLocked(session); true
    }

    suspend fun finishReload(id: String): Boolean = lock.withLock {
        if (states[id] != ModTransferReadyState.WAITING_RELOAD) return false
        setState(id, ModTransferReadyState.READY); true
    }
    suspend fun cancel(id: String) = lock.withLock { cancelLocked(id, true) }
    fun state(id: String) = states[id] ?: ModTransferReadyState.NOT_REQUIRED
    fun canStart() = states.values.all { it == ModTransferReadyState.READY || it == ModTransferReadyState.NOT_REQUIRED }
    fun pendingPlayerNames() = states.filterValues { it != ModTransferReadyState.READY && it != ModTransferReadyState.NOT_REQUIRED }.keys.mapNotNull { names[it] }.sorted()

    internal suspend fun tick() = lock.withLock {
        val time = now()
        states.entries.toList().forEach { (id, state) ->
            if (state == ModTransferReadyState.WAITING_REQUEST || state == ModTransferReadyState.WAITING_RELOAD) {
                val enteredAt = stateEnteredAt[id] ?: time.also { stateEnteredAt[id] = it }
                if (time - enteredAt >= config.sessionTimeoutMs) failLocked(id, "session timeout")
            }
        }
        sessions.values.toList().forEach { s ->
            val ackExpired = s.outstanding.any { time - it.sentAt >= config.ackTimeoutMs }
            val enteredAt = stateEnteredAt[s.id] ?: time.also { stateEnteredAt[s.id] = it }
            if (ackExpired || time - enteredAt >= config.sessionTimeoutMs) failLocked(s.id, if (ackExpired) "ACK timeout" else "session timeout")
        }
        val active = sessions.values.toList()
        if (active.isEmpty()) return@withLock
        cursor %= active.size
        for (offset in active.indices) {
            val s = active[(cursor + offset) % active.size]
            if (s.outstanding.size >= config.windowSize) continue
            val item = try { readOneLocked(s) } catch (e: Exception) {
                failLocked(s.id, "read failed: ${e.javaClass.simpleName}")
                null
            }
            cursor = (cursor + offset + 1) % active.size
            if (item != null) {
                try { sender.send(item.first, RwppModPacket.writeDownloadModChunk(item.second, item.third)) }
                catch (e: Exception) { failLocked(item.first, "send failed: ${e.javaClass.simpleName}") }
            }
            break
        }
    }

    private fun readOneLocked(s: Session): Triple<String, RwppModPacket.ChunkMetadata, ByteArray>? {
        val entry = s.mods.getOrNull(s.modIndex) ?: return null
        val total = ((entry.size + RwppConstants.CHUNK_SIZE - 1) / RwppConstants.CHUNK_SIZE).toInt()
        if (s.nextChunk >= total) return null
        val input = s.input ?: source.open(entry).also { s.input = it }
        val expected = min(RwppConstants.CHUNK_SIZE.toLong(), entry.size - s.nextChunk.toLong() * RwppConstants.CHUNK_SIZE).toInt()
        val bytes = ByteArray(expected); var read = 0
        while (read < expected) { val n = input.read(bytes, read, expected - read); if (n < 0) error("mod file changed"); read += n }
        val chunk = s.nextChunk++; s.outstanding.add(Outstanding(s.modIndex, chunk, now()))
        val totalSize = if (chunk == 0) entry.size else 0L
        val sha256 = if (chunk == 0) entry.sha256 else ""
        return Triple(s.id, RwppModPacket.ChunkMetadata(entry.logicalName, chunk, total, totalSize, sha256), bytes)
    }

    private fun advanceCompletedModLocked(s: Session) {
        val entry = s.mods[s.modIndex]
        val total = ((entry.size + RwppConstants.CHUNK_SIZE - 1) / RwppConstants.CHUNK_SIZE).toInt()
        if (s.nextChunk < total || s.outstanding.any { it.modIndex == s.modIndex }) return
        s.input?.close(); s.input = null; s.modIndex++; s.nextChunk = 0
        if (s.modIndex == s.mods.size) { sessions.remove(s.id); ModCatalogManager.release(s.snapshot); setState(s.id, ModTransferReadyState.WAITING_RELOAD) }
    }
    private fun failLocked(id: String, reason: String) { cancelLocked(id, false); setState(id, ModTransferReadyState.FAILED); onFailure(id, reason) }
    private fun setState(id: String, state: ModTransferReadyState) { states[id] = state; stateEnteredAt[id] = now() }
    private fun cancelLocked(id: String, removeState: Boolean) {
        sessions.remove(id)?.also { ModCatalogManager.release(it.snapshot) }?.input?.runCatching { close() }
        if (removeState) { states.remove(id); names.remove(id); stateEnteredAt.remove(id) }
    }
    override fun close() {
        scope.cancel()
        runBlocking { lock.withLock {
            sessions.values.forEach { ModCatalogManager.release(it.snapshot); it.input?.runCatching { close() } }
            sessions.clear(); states.clear(); names.clear(); stateEnteredAt.clear()
        } }
    }
}
