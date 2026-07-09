/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.net.rwpp

import net.rwhps.server.io.packet.Packet
import net.rwhps.server.net.rwpp.packet.RwppModPacket
import net.rwhps.server.plugin.internal.headless.inject.net.GameVersionServer
import net.rwhps.server.util.log.Log

/**
 * RWJS mod 传输协议的服务器侧入口（阶段 2：拦截与日志；阶段 3 接入分块发送）。
 */
object ModTransferHandler {
    /**
     * 客户端请求下载缺失 mod（type 500）。
     */
    @JvmStatic
    fun handleDownloadRequest(server: GameVersionServer, packet: Packet) {
        if (!ModTransferSupport.isActive()) {
            Log.warn("[MODSYNC-HPS] mod download request ignored: transfer disabled or no mods loaded")
            return
        }
        val request = RwppModPacket.readDownloadRequest(packet)
        Log.clog("[MODSYNC-HPS] download request from ${server.player.name}: mods='${request.mods}'")
        // TODO phase 3: queue chunked send via ModTransferScheduler
    }

    /**
     * 客户端确认收到分块（type 503），用于流量控制。
     */
    @JvmStatic
    fun handleChunkAck(server: GameVersionServer, packet: Packet) {
        if (!ModTransferSupport.isActive()) {
            return
        }
        val ack = RwppModPacket.readChunkAck(packet)
        Log.clog("[MODSYNC-HPS] chunk ack from ${server.player.name}: mod='${ack.name}' idx=${ack.ackChunkIndex}")
        // TODO phase 3: release send window
    }

    /**
     * 客户端 mod reload 完成（type 502）。
     */
    @JvmStatic
    fun handleReloadFinish(server: GameVersionServer, packet: Packet) {
        if (!ModTransferSupport.isActive()) {
            return
        }
        Log.clog("[MODSYNC-HPS] ModReloadFinish from ${server.player.name}")
        // TODO phase 4: mark player ready in Hess
    }

    @JvmStatic
    fun onPlayerDisconnect(server: GameVersionServer) {
        // TODO phase 3: cancel in-flight transfer session
    }
}
