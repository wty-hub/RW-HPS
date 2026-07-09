/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.internal.headless.inject.lib

import com.corrodinggames.rts.gameFramework.j.NetEnginePackaging
import com.corrodinggames.rts.gameFramework.j.ad
import com.corrodinggames.rts.gameFramework.j.au
import com.corrodinggames.rts.gameFramework.j.c
import net.rwhps.server.core.thread.CallTimeTask
import net.rwhps.server.core.thread.Threads
import net.rwhps.server.data.global.Data
import net.rwhps.server.game.event.game.PlayerJoinEvent
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.game.room.ServerRoom
import net.rwhps.server.io.GameInputStream
import net.rwhps.server.io.GameOutputStream
import net.rwhps.server.io.output.CompressOutputStream
import net.rwhps.server.io.packet.type.PacketType
import net.rwhps.server.net.core.ConnectionAgreement
import net.rwhps.server.net.rwpp.ModTransferSupport
import net.rwhps.server.plugin.internal.headless.inject.core.GameEngine
import net.rwhps.server.plugin.internal.headless.inject.core.link.PrivateClassLinkPlayer
import net.rwhps.server.plugin.internal.headless.inject.net.GameVersionServer
import net.rwhps.server.plugin.internal.headless.inject.net.socket.HessSocket
import java.util.concurrent.TimeUnit
import com.corrodinggames.rts.gameFramework.j.c as PlayerConnect

/**
 * @author Dr (dr@der.kim)
 */
class PlayerConnectX(
    val netEngine: ad, val connectionAgreement: ConnectionAgreement
): PlayerConnect(netEngine, HessSocket(connectionAgreement)) {

    val netEnginePackaging: NetEnginePackaging = NetEnginePackaging(netEngine, this)
    var room: ServerRoom = HeadlessModuleManage.hessLoaderMap[this.javaClass.classLoader.toString()]!!.room
    var player: PlayerHess? = null
    lateinit var serverConnect: GameVersionServer

    @Synchronized
    override fun a(p0: Boolean, p1: Boolean, p2: String?) {
        super.a(p0, p1, p2)
        serverConnect.disconnect()
    }

    override fun d() {
        // Register BIO
    }

    override fun a(packetHess: au) {
        if (player == null) {
            if (this.e() != "<null>") {
                player = room.playerManage.addAbstractPlayer(serverConnect, PrivateClassLinkPlayer(z))

                serverConnect.player = player!!

                GameEngine.data.eventManage.fire(PlayerJoinEvent(GameEngine.data, player!!))

                if (!Threads.containsTimeTask(CallTimeTask.CallTeamTask)) {
                    Threads.newTimedTask(CallTimeTask.CallTeamTask, 0, 1, TimeUnit.SECONDS) {
                        GameEngine.netEngine.e(null as c?)
                        GameEngine.netEngine.L()
                    }
                }
            }
        } else {
            // 在这里过滤走官方的包, 加入 RW-HPS 的一些修改
            run {
                when (packetHess.b) {
                    PacketType.PREREGISTER_INFO.typeInt -> {
                        GameInputStream(packetHess.c).use {
                            val o = GameOutputStream()
                            val originalFirst = it.readString()
                            o.writeString(
                                if (ModTransferSupport.isActive()) ModTransferSupport.preregisterPrefix()
                                else originalFirst
                            )
                            o.transferToFixedLength(it, 12)
                            o.writeString(Data.SERVER_ID)
                            it.skip(it.readShort().toLong())
                            o.transferTo(it)
                            packetHess.c = o.getByteArray()
                        }
                    }
                    // 修改, 使 客户端 显示 AdminUI
                    PacketType.SERVER_INFO.typeInt -> {
                        GameInputStream(packetHess.c).use {
                            val o = GameOutputStream()
                            it.skip(it.readShort().toLong())
                            o.writeString(Data.SERVER_ID)

                            o.transferToFixedLength(it, 8)

                            val length = it.readShort()
                            o.writeShort(length)
                            o.transferToFixedLength(it, length.toInt())

                            o.transferToFixedLength(it, 15)

                            /* Admin Ui */
                            it.skip(1)
                            o.writeBoolean(player!!.isAdmin)
                            o.transferTo(it)
                            packetHess.c = o.getByteArray()
                        }
                    }
                    // 修改, 使 客户端 显示 HOST
                    PacketType.TEAM_LIST.typeInt -> {
                        GameInputStream(packetHess.c).use {
                            val o = GameOutputStream()
                            val site = it.readInt()
                            o.writeInt(site)
                            val isGameStatus = it.readBoolean()
                            o.writeBoolean(isGameStatus)
                            if (!isGameStatus) {
                                val playerConut = it.readInt()
                                o.writeInt(playerConut)

                                room.flagData.ai = false
                                CompressOutputStream.getGzipOutputStream("teams", true).also { teamIn ->
                                    it.getDecodeStream(true).use { team ->
                                        for (position in 0 until playerConut) {
                                            val hasPlayer = team.readBoolean()
                                            teamIn.writeBoolean(hasPlayer)
                                            if (hasPlayer) {
                                                teamIn.transferToFixedLength(team, 13)
                                                val name = team.readIsString()
                                                teamIn.writeIsString(name)

                                                //teamIn.writeBoolean(team.readBoolean())
//                                                team.skip(4)
//                                                teamIn.writeInt(99999)

                                                teamIn.transferToFixedLength(team, 32)

                                                // 可能存在 Hess 还没刷新的, 所以多来一次判断
                                                val player = room.playerManage.getPlayer(position)
                                                if (player == null) {
                                                    teamIn.transferToFixedLength(team, 4)
                                                    // 过滤掉 AI
                                                    if (name.contains("AI", ignoreCase = true)) {
                                                        room.flagData.ai = true
                                                    }
                                                } else {
                                                    team.skip(4)
                                                    teamIn.writeInt(if (player.isAdmin) 1 else 0)
                                                }


                                                teamIn.writeIsInt(team)
                                                teamIn.writeIsInt(team)
                                                teamIn.writeIsInt(team)
                                                teamIn.writeIsInt(team)
                                                teamIn.writeInt(team.readInt())
                                            }
                                        }
                                    }
                                    o.flushEncodeData(teamIn)
                                }
                                o.transferTo(it)
                                packetHess.c = o.getByteArray()
                            }
                        }
                    }
                    //
                    PacketType.START_GAME.typeInt -> {
                        if (!room.isStartGame) {
                            room.isStartGame = true
                            room.roomStartGame()
                        }
                    }
                }
            }
        }
        run {
            when (packetHess.b) {
                PacketType.KICK.typeInt -> {
                    GameInputStream(packetHess.c).use {
                        val o = GameOutputStream()
                        val msg = it.readString()
                        if (Data.configServer.maxPlayerJoinAd.isNotBlank() && msg.contains("free")) {
                            o.writeString(Data.configServer.maxPlayerJoinAd)
                        } else if (Data.configServer.startPlayerJoinAd.isNotBlank() && msg.contains("started")) {
                            o.writeString(Data.configServer.startPlayerJoinAd)
                        } else {
                            return@run
                        }
                        packetHess.c = o.getByteArray()
                    }
                }
            }
        }

        serverConnect.sendPacket(netEnginePackaging.transformPacket(packetHess))
    }

    override fun f(): String {
        return connectionAgreement.ip
    }

    override fun g(): String {
        return connectionAgreement.ip
    }
}