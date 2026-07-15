/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.internal.headless.inject.net

import net.rwhps.server.data.global.Data
import net.rwhps.server.func.Control
import net.rwhps.server.io.packet.Packet
import net.rwhps.server.io.packet.type.PacketType
import net.rwhps.server.net.core.ConnectionAgreement
import net.rwhps.server.net.core.TypeConnect
import net.rwhps.server.net.core.server.AbstractNetConnect
import net.rwhps.server.net.rwpp.ModTransferHandler
import net.rwhps.server.net.rwpp.ModTransferSupport
import net.rwhps.server.plugin.internal.headless.inject.core.GameEngine.netEngine
import net.rwhps.server.plugin.internal.headless.inject.lib.PlayerConnectX
import net.rwhps.server.util.log.Log

/**
 * Parse the [net.rwhps.server.net.core.IRwHps.NetType.ServerProtocol] protocol
 * @property con                GameVersionRelay
 * @property conClass           Initialize
 * @property abstractNetConnect AbstractNetConnect
 * @property version            Parser version
 * @author Dr (dr@der.kim)
 */
open class HeadlessTypeConnect: TypeConnect {
    val con: GameVersionServer
    var conClass: Class<out GameVersionServer>? = null

    override val abstractNetConnect: AbstractNetConnect
        get() = con

    constructor(con: GameVersionServer) {
        this.con = con
    }

    constructor(con: Class<out GameVersionServer>) {
        // will not be used ; just override the initial value to avoid refusing to compile
        this.con = GameVersionServer(PlayerConnectX(netEngine, ConnectionAgreement()))

        // use for instantiation
        conClass = con
    }

    override fun getTypeConnect(connectionAgreement: ConnectionAgreement): TypeConnect {
        val netEngine = con.playerConnectX.netEngine

        val playerConnect = PlayerConnectX(netEngine, connectionAgreement)
        playerConnect.h = false // UDP
        playerConnect.i = false // UDP
        playerConnect.d()

        netEngine.aM.add(playerConnect)

        return HeadlessTypeConnect(GameVersionServer(playerConnect))
    }

    @Throws(Exception::class)
    override fun processConnect(packet: Packet) {
        try {
            when (packet.type) {
                PacketType.CHAT_RECEIVE -> {
                    con.receiveChat(packet)
                }
                // TODO 用户不可信
                PacketType.GAMECOMMAND_RECEIVE -> {
                    con.receiveCommand(packet)
                }
                PacketType.RELAY_118_117_RETURN -> {
                    packet.status = Control.EventNext.STOPPED
                    con.sendRelayServerTypeReply(packet)
                }
                PacketType.DISCONNECT -> {
                    con.disconnect()
                    packet.status = Control.EventNext.STOPPED
                }
                PacketType.MOD_DOWNLOAD_REQUEST -> {
                    if (ModTransferSupport.isActive()) {
                        ModTransferHandler.handleDownloadRequest(con, packet)
                    } else {
                        Log.clog(
                            "[MODSYNC-HPS] Ignored download request from ${con.player?.name ?: "unregistered"}: " +
                                (ModTransferSupport.inactiveReason() ?: "unknown")
                        )
                    }
                    packet.status = Control.EventNext.STOPPED
                }
                PacketType.MOD_CHUNK_ACK -> {
                    if (ModTransferSupport.isActive()) {
                        ModTransferHandler.handleChunkAck(con, packet)
                    } else {
                        Log.clog("[MODSYNC-HPS] Ignored chunk ACK while transfer inactive")
                    }
                    packet.status = Control.EventNext.STOPPED
                }
                PacketType.MOD_RELOAD_FINISH -> {
                    if (ModTransferSupport.isActive()) {
                        ModTransferHandler.handleReloadFinish(con, packet)
                    } else {
                        Log.clog("[MODSYNC-HPS] Ignored reload finish while transfer inactive")
                    }
                    packet.status = Control.EventNext.STOPPED
                }
                else -> {}
            }
            if (packet.status == Control.EventNext.CONTINUE) {
                con.receivePacket(packet)
            }
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    override val version: String
        get() = "${Data.SERVER_CORE_VERSION}: 1.0.0"
}