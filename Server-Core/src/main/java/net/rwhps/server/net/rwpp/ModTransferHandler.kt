package net.rwhps.server.net.rwpp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.rwhps.server.data.global.Data
import net.rwhps.server.io.packet.Packet
import net.rwhps.server.net.rwpp.packet.RwppModPacket
import net.rwhps.server.util.log.Log
import java.util.concurrent.ConcurrentHashMap

/** Validates RWJS protocol messages and delegates file IO to the shared scheduler. */
object ModTransferHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, ModTransferEndpoint>()
    private val scheduler by lazy {
        val c = Data.configServer
        ModTransferScheduler(
            ModChunkSender { id, packet ->
                val endpoint = connections[id] ?: error("connection closed")
                if (endpoint.isDis) error("connection closed")
                endpoint.sendPacket(packet)
                if (endpoint.isDis) error("send failed")
            },
            ModTransferConfig(
                windowSize = c.modTransferWindowSize,
                ackTimeoutMs = c.modTransferAckTimeoutMs,
                sessionTimeoutMs = c.modTransferSessionTimeoutMs,
                maxConcurrent = c.maxConcurrentModTransfers,
            ),
            onFailure = { id, reason ->
                val endpoint = connections[id]
                Log.clog("[MODSYNC-HPS] transfer failed for ${safePlayerName(endpoint)}: $reason")
                endpoint?.disconnect()
            },
        )
    }

    @JvmStatic
    fun onCapabilitySent(endpoint: ModTransferEndpoint) {
        if (!ModTransferSupport.isActive()) return
        connections[endpoint.connectionId] = endpoint
        scheduler.markWaitingRequest(endpoint.connectionId, safePlayerName(endpoint))
    }

    @JvmStatic
    fun handleDownloadRequest(endpoint: ModTransferEndpoint, packet: Packet) {
        if (!ModTransferSupport.isActive()) return reject(endpoint, "download request while transfer inactive")
        val request = runCatching { RwppModPacket.readDownloadRequest(packet).mods }.getOrElse { return reject(endpoint, "malformed download request") }
        val snapshot = ModCatalogManager.snapshot()
        val requested = parseRequest(request, snapshot) ?: return reject(endpoint, "invalid, ambiguous, or unknown mod request")
        val total = runCatching { requested.fold(0L) { sum, entry -> Math.addExact(sum, entry.size) } }.getOrElse { return reject(endpoint, "requested size overflow") }
        if (total > Data.configServer.maxModTransferSizeMb * 1024L * 1024L) return reject(endpoint, "requested mods exceed transfer limit")
        connections[endpoint.connectionId] = endpoint
        val prior = scheduler.state(endpoint.connectionId)
        Log.clog(
            "[MODSYNC-HPS] Download request from ${safePlayerName(endpoint)}" +
                (when (prior) {
                    ModTransferReadyState.TRANSFERRING -> " (keep going, ignore mid-transfer duplicate)"
                    ModTransferReadyState.WAITING_REQUEST -> " (debounce refresh)"
                    else -> ""
                }) +
                ": ${requested.joinToString { it.logicalName }} ($total bytes)"
        )
        scope.launch {
            if (!scheduler.replace(endpoint.connectionId, safePlayerName(endpoint), snapshot, requested)) {
                reject(endpoint, "too many concurrent transfers")
            }
        }
    }

    @JvmStatic
    fun handleChunkAck(endpoint: ModTransferEndpoint, packet: Packet) {
        val ack = runCatching { RwppModPacket.readChunkAck(packet) }.getOrElse { return reject(endpoint, "malformed chunk ACK") }
        scope.launch {
            // 对齐 TXJS：陈旧/重复 ACK 安全忽略，不断开连接
            scheduler.acknowledge(endpoint.connectionId, ack.name, ack.ackChunkIndex)
        }
    }

    @JvmStatic
    fun handleReloadFinish(endpoint: ModTransferEndpoint, packet: Packet) {
        runCatching { RwppModPacket.readReloadFinish(packet) }.getOrElse { return reject(endpoint, "malformed reload finish") }
        scope.launch {
            if (!scheduler.finishReload(endpoint.connectionId)) {
                Log.clog(
                    "[MODSYNC-HPS] Ignoring reload finish from ${safePlayerName(endpoint)} " +
                        "(state=${scheduler.state(endpoint.connectionId)})"
                )
            }
        }
    }

    @JvmStatic
    fun onPlayerDisconnect(endpoint: ModTransferEndpoint) {
        connections.remove(endpoint.connectionId)
        scope.launch { scheduler.cancel(endpoint.connectionId) }
    }

    @JvmStatic fun canStart(): Boolean = !ModTransferSupport.isActive() || scheduler.canStart()
    @JvmStatic fun pendingPlayerNames(): List<String> = if (ModTransferSupport.isActive()) scheduler.pendingPlayerNames() else emptyList()
    @JvmStatic fun state(endpoint: ModTransferEndpoint): ModTransferReadyState = scheduler.state(endpoint.connectionId)
    @JvmStatic fun hasBusyTransfers(): Boolean = ModTransferSupport.isActive() && scheduler.hasBusyTransfers()

    internal fun parseRequest(raw: String, snapshot: ModCatalogSnapshot): List<ModCatalogEntry>? {
        if (raw.isEmpty()) return emptyList()
        val parts = raw.split(',')
        if (parts.any { it.trim().isEmpty() }) return null
        val byName = snapshot.entries.groupBy { it.logicalName.lowercase() }
        val result = ArrayList<ModCatalogEntry>()
        val seen = HashSet<String>()
        for (part in parts) {
            val name = part.trim()
            val key = name.lowercase()
            if (!seen.add(key)) continue
            val matches = byName[key] ?: return null
            if (matches.size != 1) return null
            result.add(matches.single())
        }
        return result
    }

    private fun reject(endpoint: ModTransferEndpoint, reason: String) {
        Log.clog("[MODSYNC-HPS] Reject ${safePlayerName(endpoint)}: $reason")
        endpoint.disconnect()
    }

    private fun safePlayerName(endpoint: ModTransferEndpoint?): String =
        runCatching { endpoint?.playerLabel() }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unregistered connection"
}
