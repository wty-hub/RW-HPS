package net.rwhps.server.net.rwpp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.rwhps.server.data.global.Data
import net.rwhps.server.io.packet.Packet
import net.rwhps.server.net.rwpp.packet.RwppModPacket
import net.rwhps.server.plugin.internal.headless.inject.net.GameVersionServer
import net.rwhps.server.util.log.Log
import java.util.concurrent.ConcurrentHashMap

/** Validates RWJS protocol messages and delegates file IO to the shared scheduler. */
object ModTransferHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, GameVersionServer>()
    private val scheduler by lazy {
        val c = Data.configServer
        ModTransferScheduler(
            ModChunkSender { id, packet ->
                val server = connections[id] ?: error("connection closed")
                if (server.isDis) error("connection closed")
                server.sendPacket(packet)
                if (server.isDis) error("send failed")
            },
            ModTransferConfig(c.modTransferWindowSize, c.modTransferAckTimeoutMs, c.modTransferSessionTimeoutMs, c.maxConcurrentModTransfers),
            onFailure = { id, reason ->
                val server = connections[id]
                Log.warn("[MODSYNC-HPS] transfer failed for ${safePlayerName(server)}: $reason")
                server?.disconnect()
            },
        )
    }

    @JvmStatic
    fun onCapabilitySent(server: GameVersionServer) {
        if (!ModTransferSupport.isActive()) return
        val id = id(server)
        connections[id] = server
        scheduler.markWaitingRequest(id, safePlayerName(server))
    }

    @JvmStatic
    fun handleDownloadRequest(server: GameVersionServer, packet: Packet) {
        if (!ModTransferSupport.isActive()) return reject(server, "download request while transfer inactive")
        val request = runCatching { RwppModPacket.readDownloadRequest(packet).mods }.getOrElse { return reject(server, "malformed download request") }
        val snapshot = ModCatalogManager.snapshot()
        val requested = parseRequest(request, snapshot) ?: return reject(server, "invalid, ambiguous, or unknown mod request")
        val total = runCatching { requested.fold(0L) { sum, entry -> Math.addExact(sum, entry.size) } }.getOrElse { return reject(server, "requested size overflow") }
        if (total > Data.configServer.maxModTransferSizeMb * 1024L * 1024L) return reject(server, "requested mods exceed transfer limit")
        val connectionId = id(server)
        connections[connectionId] = server
        scope.launch {
            if (!scheduler.replace(connectionId, safePlayerName(server), snapshot, requested)) reject(server, "too many concurrent transfers")
        }
    }

    @JvmStatic
    fun handleChunkAck(server: GameVersionServer, packet: Packet) {
        val ack = runCatching { RwppModPacket.readChunkAck(packet) }.getOrElse { return reject(server, "malformed chunk ACK") }
        scope.launch {
            if (!scheduler.acknowledge(id(server), ack.name, ack.ackChunkIndex)) reject(server, "invalid, duplicate, stale, or out-of-window chunk ACK")
        }
    }

    @JvmStatic
    fun handleReloadFinish(server: GameVersionServer, packet: Packet) {
        runCatching { RwppModPacket.readReloadFinish(packet) }.getOrElse { return reject(server, "malformed reload finish") }
        scope.launch {
            if (!scheduler.finishReload(id(server))) reject(server, "reload completed before transfer was acknowledged")
        }
    }

    @JvmStatic
    fun onPlayerDisconnect(server: GameVersionServer) {
        val connectionId = id(server)
        connections.remove(connectionId)
        scope.launch { scheduler.cancel(connectionId) }
    }

    @JvmStatic fun canStart(): Boolean = !ModTransferSupport.isActive() || scheduler.canStart()
    @JvmStatic fun pendingPlayerNames(): List<String> = if (ModTransferSupport.isActive()) scheduler.pendingPlayerNames() else emptyList()
    @JvmStatic fun state(server: GameVersionServer): ModTransferReadyState = scheduler.state(id(server))

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

    private fun reject(server: GameVersionServer, reason: String) {
        Log.warn("[MODSYNC-HPS] Reject ${safePlayerName(server)}: $reason")
        server.disconnect()
    }
    private fun id(server: GameVersionServer) = server.playerConnectX.connectionAgreement.id
    private fun safePlayerName(server: GameVersionServer?): String = runCatching { server?.player?.name }.getOrNull() ?: "unregistered connection"
}
