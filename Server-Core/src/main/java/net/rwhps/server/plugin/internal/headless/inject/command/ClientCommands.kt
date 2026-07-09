/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.internal.headless.inject.command

import android.graphics.PointF
import com.corrodinggames.rts.game.n
import com.corrodinggames.rts.gameFramework.j.ai
import net.rwhps.server.command.ex.Vote
import net.rwhps.server.core.thread.CallTimeTask
import net.rwhps.server.core.thread.Threads
import net.rwhps.server.data.global.Data
import net.rwhps.server.func.StrCons
import net.rwhps.server.game.GameMaps
import net.rwhps.server.game.enums.GamePingActions
import net.rwhps.server.game.event.game.PlayerBanEvent
import net.rwhps.server.game.event.game.ServerGameStartEvent
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.game.manage.MapManage
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.game.room.RelayRoom
import net.rwhps.server.net.core.server.AbstractNetConnectServer
import net.rwhps.server.net.netconnectprotocol.realize.GameVersionRelay
import net.rwhps.server.plugin.internal.headless.inject.core.GameEngine
import net.rwhps.server.plugin.internal.headless.inject.util.TabCompleterProcess
import net.rwhps.server.struct.map.BaseMap.Companion.toSeq
import net.rwhps.server.util.IsUtils
import net.rwhps.server.util.IsUtils.isNumeric
import net.rwhps.server.util.IsUtils.notIsNumeric
import net.rwhps.server.util.Time
import net.rwhps.server.util.algorithms.HexUtils
import net.rwhps.server.util.console.tab.TabDefaultEnum.*
import net.rwhps.server.util.file.plugin.PluginManage
import net.rwhps.server.util.game.command.CommandHandler
import net.rwhps.server.util.inline.findField
import net.rwhps.server.util.log.Log
import net.rwhps.server.util.log.Log.error
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.corrodinggames.rts.game.units.h as SupBuild

/**
 * @author Dr (dr@der.kim)
 */
internal class ClientCommands(handler: CommandHandler) {
    private val localeUtil = Data.i18NBundle

    private fun isAdmin(player: PlayerHess): Boolean {
        if (player.isAdmin) {
            return true
        }
        player.sendSystemMessage(player.i18NBundle.getinput("err.noAdmin"))
        return false
    }

    private fun checkPositionNumb(int: String, player: PlayerHess): Boolean {
        if (notIsNumeric(int)) {
            player.sendSystemMessage(player.i18NBundle.getinput("err.noNumber"))
            return false
        }
        if (int.toInt() < 0) {
            player.sendSystemMessage(player.i18NBundle.getinput("err.noInt"))
            return false
        }
        if (int.toInt() > (Data.configServer.maxPlayer)) {
            player.sendSystemMessage(player.i18NBundle.getinput("err.maxPlayer"))
            return false
        }
        return true
    }

    private fun registerGameCoreCommand(handler: CommandHandler) {
        /* QC */
        handler.register("credits", "<money>", "HIDE") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                if (notIsNumeric(args[0])) {
                    player.sendSystemMessage(localeUtil.getinput("err.noNumber"))
                    return@register
                }
                GameEngine.data.gameLinkServerData.credits = when (args[0].toInt()) {
                    0 -> 1
                    1000 -> 2
                    2000 -> 3
                    5000 -> 4
                    10000 -> 5
                    50000 -> 6
                    100000 -> 7
                    200000 -> 8
                    4000 -> 0
                    else -> 0
                }
            }
        }
        handler.register("nukes", "<boolean>", "HIDE") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                GameEngine.data.gameLinkServerData.nukes = !args[0].toBoolean()
            }
        }
        handler.register("addai", "HIDE") { _: Array<String>?, player: PlayerHess ->
            if (isAdmin(player)) {
                if (Data.configServer.enableAI) {
                    GameEngine.data.room.playerManage.addAI()
                } else {
                    player.sendSystemMessage("请在配置文件启用AI")
                }
            }
        }
        handler.register("fog", "<type>", "HIDE") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                GameEngine.data.gameLinkServerData.fog = if ("off" == args[0]) 0 else if ("basic" == args[0]) 1 else 2
            }
        }
        handler.register("sharedcontrol", "<boolean>", "HIDE") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                GameEngine.data.gameLinkServerData.sharedcontrol = args[0].toBoolean()
            }
        }
        handler.register("startingunits", "<type>", "HIDE") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                GameEngine.data.gameLinkServerData.startingunits = args[0].toInt()
            }
        }
        handler.register("income", "<income>", "clientCommands.income") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                if (room.isStartGame) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.startGame.warn"))
                }
                GameEngine.data.gameLinkServerData.income = args[0].toFloat()
            }
        }
        handler.register("ai", "<difficuld> [$PlayerPositionAI]", "clientCommands.income") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                if (room.isStartGame) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.startGame"))
                    return@register
                }

                if (!isNumeric(args[0])) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.noInt"))
                    return@register
                }

                synchronized(gameModule.gameLinkServerData.teamOperationsSyncObject) {
                    if (args.size > 1) {
                        if (!isNumeric(args[1])) {
                            player.sendSystemMessage(player.i18NBundle.getinput("err.noInt.check"))
                            return@register
                        }
                        val playerAi = gameModule.room.playerManage.getPlayer(args[1].toInt() - 1)
                        if (playerAi == null) {
                            player.sendSystemMessage(player.i18NBundle.getinput("no.findPlayer"))
                        } else {
                            playerAi.aiDifficulty = args[0].toInt()
                        }
                    } else {
                        GameEngine.data.gameLinkServerData.aiDifficuld = args[0].toInt()

                        gameModule.room.playerManage.playerGroup.eachAllFind( { it.isAi }) {
                            it.aiDifficulty = args[0].toInt()
                        }
                    }
                }
            }
        }
        handler.register("start", "clientCommands.start") { _: Array<String>?, player: PlayerHess? ->
            if (player != null) {
                if (!isAdmin(player)) {
                    return@register
                }
                if (room.isStartGame) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.startGame"))
                    return@register
                }
            } else if (room.isStartGame) {
                return@register
            }

            val minPlayers = Data.configServer.minStartPlayerCount()
            if (room.playerManage.playerGroup.size < minPlayers) {
                if (player != null) {
                    player.sendSystemMessage(player.i18NBundle.getinput("start.playerNo", minPlayers))
                } else {
                    room.call.sendSystemMessageLocal("start.playerNo", minPlayers)
                }
                return@register
            }

            if (room.maps.mapType != GameMaps.MapType.DefaultMap) {
                room.maps.mapData!!.readMap()
            }

            gameModule.gameScriptMultiPlayer.multiplayerStart()

            GameEngine.data.eventManage.fire(ServerGameStartEvent(gameModule))
        }
        handler.register("kick", "<$PlayerPosition>", "clientCommands.kick") { args: Array<String>, player: PlayerHess ->
            if (room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.startGame"))
                return@register
            }
            if (isAdmin(player)) {
                if (notIsNumeric(args[0])) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.noNumber"))
                    return@register
                }

                synchronized(gameModule.gameLinkServerData.teamOperationsSyncObject) {
                    val site = args[0].toInt() - 1
                    val kickPlayer = room.playerManage.getPlayer(site)
                    if (kickPlayer != null) {
                        try {
                            kickPlayer.kickPlayer(localeUtil.getinput("kick.you"), 60)
                        } catch (e: IOException) {
                            error("[Player] Send Kick Player Error", e)
                        }
                    }
                }
            }
        }
        handler.register("move", "<$PlayerPosition> <ToSerialNumber> <Team>", "HIDE") { args: Array<String>, player: PlayerHess ->
            if (room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.startGame"))
                return@register
            }
            if (isAdmin(player)) {
                if (notIsNumeric(args[0]) && notIsNumeric(args[1]) && notIsNumeric(args[2])) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.noNumber"))
                    return@register
                }

                synchronized(gameModule.gameLinkServerData.teamOperationsSyncObject) {
                    val tg = args[0].toInt() - 1
                    val playerTarget = n.k(tg)
                    if (checkPositionNumb(args[1], player)) {
                        val site = args[1].toInt() - 1
                        val newTeam = args[2].toInt()
                        GameEngine.netEngine.a(playerTarget, site)
                        when (newTeam) {
                            -1 -> {
                                playerTarget.r = site % 2
                            }
                            -4 -> {
                                playerTarget.r = -3
                            }
                            else -> {
                                playerTarget.r = newTeam
                            }
                        }
                    }
                }
            }
        }
        handler.register("team", "<$PlayerPosition> <ToTeamNumber>", "HIDE") { args: Array<String>, player: PlayerHess ->
            if (room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.startGame"))
                return@register
            }
            if (isAdmin(player)) {
                if (notIsNumeric(args[0]) && notIsNumeric(args[1])) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.noNumber"))
                    return@register
                }
                synchronized(gameModule.gameLinkServerData.teamOperationsSyncObject) {
                    val playerPosition = args[0].toInt() - 1
                    val newPosition = args[1].toInt() - 1
                    n.k(playerPosition).r = newPosition
                }
            }
        }
        handler.register("am","<on/off>" ,"clientCommands.am") { args: Array<String>, player: PlayerHess ->
            if (room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.startGame"))
                return@register
            }
            if (isAdmin(player)) {
                gameModule.room.isMixPlayer = args[0].equals("on", true)
                if (gameModule.room.isMixPlayer) {
                    synchronized(gameModule.gameLinkServerData.teamOperationsSyncObject) {
                        for (site in 0 until Data.configServer.maxPlayer) {
                            n.k(site)?.r = site
                        }
                    }
                }
                player.sendSystemMessage(player.i18NBundle.getinput("server.amTeam", gameModule.room.isMixPlayer))
            }
        }
        handler.register("battleroom",  "clientCommands.battleroom") { _: Array<String>, player: PlayerHess ->
            if (!room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.noStartGame"))
                return@register
            }
            if (isAdmin(player)) {
                gameModule.gameLinkFunction.battleRoom()
            }
        }
        handler.register("pause",  "clientCommands.pause") { _: Array<String>, player: PlayerHess ->
            if (!room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.noStartGame"))
                return@register
            }
            if (isAdmin(player)) {
                gameModule.gameLinkFunction.pauseGame(true)
            }
        }
        handler.register("unpause",  "clientCommands.unpause") { _: Array<String>, player: PlayerHess ->
            if (!room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.noStartGame"))
                return@register
            }
            if (isAdmin(player)) {
                gameModule.gameLinkFunction.pauseGame(false)
            }
        }
    }

    private fun registerGameCommandX(handler: CommandHandler) {
        handler.register("help", "clientCommands.help") { _: Array<String>?, player: PlayerHess ->
            val str = StringBuilder(16)
            for (command in handler.commandList) {
                if (command.description.startsWith("#")) {
                    str.append("   ").append(command.text).append(if (command.paramText.isEmpty()) "" else " ").append(command.paramText)
                        .append(" - ").append(command.description.substring(1))
                } else {
                    if ("HIDE" == command.description) {
                        continue
                    }
                    str.append("   ").append(command.text).append(if (command.paramText.isEmpty()) "" else " ").append(command.paramText)
                        .append(" - ").append(player.i18NBundle.getinput(command.description)).append(Data.LINE_SEPARATOR)
                }
            }
            player.sendSystemMessage(str.toString())
        }
        handler.register("map", "<MapNumber...>", "clientCommands.map") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                if (room.isStartGame) {
                    player.sendSystemMessage(player.getinput("err.startGame"))
                    return@register
                }
                val response = StringBuilder(args[0])
                var i = 1
                val lens = args.size
                while (i < lens) {
                    response.append(" ").append(args[i])
                    i++
                }
                val inputMapName = response.toString().replace("'", "").replace(" ", "").replace("-", "").replace("_", "")
                val mapPlayer = MapManage.gameMaps[inputMapName]
                if (inputMapName.equals("DEF", ignoreCase = true)) {
                    GameEngine.netEngine.az = "maps/skirmish/[z;p10]Crossing Large (10p).tmx"
                    GameEngine.netEngine.ay.a = ai.a
                    GameEngine.netEngine.ay.b = "[z;p10]Crossing Large (10p).tmx"
                    room.maps.mapName = "Crossing Large (10p)"
                    room.maps.mapType = GameMaps.MapType.DefaultMap
                    return@register
                }
                if (mapPlayer != null) {
                    val data = mapPlayer.split("@").toTypedArray()
                    GameEngine.netEngine.az = "maps/skirmish/${data[1]}${data[0]}.tmx"
                    GameEngine.netEngine.ay.a = ai.a
                    GameEngine.netEngine.ay.b = "${data[1]}${data[0]}.tmx"
                    room.maps.mapName = data[0]
                    room.maps.mapType = GameMaps.MapType.DefaultMap
                } else {
                    if (MapManage.mapsData.size == 0) {
                        player.sendSystemMessage(localeUtil.getinput("map.no"))
                        return@register
                    }
                    if (notIsNumeric(inputMapName)) {
                        player.sendSystemMessage(localeUtil.getinput("err.noNumber"))
                        return@register
                    }
                    val nameNoPx = MapManage.mapsData.keys.toSeq()[inputMapName.toInt()]
                    val data = MapManage.mapsData[nameNoPx]!!
                    val name = "$nameNoPx${data.mapType.fileType}"
                    room.maps.mapData = data
                    room.maps.mapType = data.mapType
                    room.maps.mapName = nameNoPx
                    room.maps.mapPlayer = ""
                    GameEngine.netEngine.az = "/SD/rusted_warfare_maps/$name"
                    GameEngine.netEngine.ay.a = ai.entries.toTypedArray()[data.mapType.ordinal]
                    GameEngine.netEngine.ay.b = name

                    player.sendSystemMessage(player.i18NBundle.getinput("map.custom.info"))
                }
                room.call.sendSystemMessage(localeUtil.getinput("map.to", player.name, room.maps.mapName))
                GameEngine.netEngine.L()
            }
        }
        handler.register("maps", "[page]", "clientCommands.maps") { _: Array<String>?, player: PlayerHess ->
            if (room.isStartGame) {
                player.sendSystemMessage(localeUtil.getinput("err.startGame"))
                return@register
            }
            if (MapManage.mapsData.size == 0) {
                return@register
            }
            val i = AtomicInteger(0)

            player.sendSystemMessage(localeUtil.getinput("maps.top"))
            MapManage.mapsData.keys.forEach { k: String ->
                player.sendSystemMessage(localeUtil.getinput("maps.info", i.get(), k))
                i.getAndIncrement()
            }
        }
        handler.register("afk", "clientCommands.afk") { _: Array<String>?, player: PlayerHess ->
            if (!room.isAfk) {
                player.sendSystemMessage(player.i18NBundle.getinput("ban.comm", "afk"))
                return@register
            }
            if (player.isAdmin) {
                player.sendSystemMessage(player.i18NBundle.getinput("afk.adminNo"))
            } else {
                if (room.isStartGame) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.startGame"))
                    return@register
                }
                if (Threads.containsTimeTask(CallTimeTask.PlayerAfkTask)) {
                    return@register
                }
                val admin = AtomicBoolean(true)
                room.playerManage.playerGroup.eachAllFind({ p: PlayerHess -> p.isAdmin }, { _: PlayerHess -> admin.set(false) })
                if (admin.get()) {
                    player.isAdmin = true
                    room.call.sendSystemMessageLocal("afk.end.noAdmin", player.name)
                    return@register
                }
                Threads.newCountdown(CallTimeTask.PlayerAfkTask, 30, TimeUnit.SECONDS) {
                    room.playerManage.playerGroup.eachFind({ p: PlayerHess -> p.isAdmin }) { i: PlayerHess ->
                        i.isAdmin = false
                        player.isAdmin = true
                        room.call.sendSystemMessageLocal("afk.end.ok", player.name)
                        GameEngine.netEngine.L()
                    }
                }
                room.call.sendSystemMessageLocal("afk.start", player.name)
            }
        }
        handler.register("status", "clientCommands.status") { _: Array<String>?, player: PlayerHess ->
            player.sendSystemMessage(
                    player.i18NBundle.getinput(
                            "status.version", room.playerManage.playerGroup.size, 0, Data.SERVER_CORE_VERSION, "RW-HPS-Hess"
                    )
            )
        }
        handler.register("sync", "<on/off>", "clientCommands.sync") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                gameModule.room.sync = "on".equals(args[0], true)
                player.sendSystemMessage(localeUtil.getinput("server.sync", if (gameModule.room.sync) "启用" else "禁止"))
            }
        }
        handler.register("vote", "<gameover>", "clientCommands.vote") { _: Array<String>?, player: PlayerHess ->
            Data.vote = Vote("gameover", player)
        }
        handler.register("setmaxplayer", "<number>", "clientCommands.vote") { args: Array<String>, player: PlayerHess ->
            n.b(args[0].toInt(), true)
            player.sendSystemMessage("设置最大人数: ${args[0]}")
        }
        handler.register("iunit", "<$PlayerPosition> <unitID>", "clientCommands.iunit") { args: Array<String>, player: PlayerHess ->
            if (room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.startGame"))
                return@register
            }
            if (isAdmin(player)) {
                if (notIsNumeric(args[1])) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.noNumber"))
                    return@register
                }

                val site = args[0].toInt() - 1
                val playerUnit = room.playerManage.getPlayer(site)
                if (playerUnit != null) {
                    playerUnit.startUnit = args[1].toInt()
                } else {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.player.no.site", args[0]))
                }
            }
        }
    }

    private fun registerGameCustomCommand(handler: CommandHandler) {
        handler.register("summon", "<unitName>", "clientCommands.summon") { args: Array<String>, player: PlayerHess ->
            if (!room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.noStartGame"))
                return@register
            }
            if (player.isAdmin) {
                val unit = args[0]
                player.sendSystemMessage(player.i18NBundle.getinput("clientCommands.summon.ping"))
                player.addData<(AbstractNetConnectServer, GamePingActions, Float, Float)->Unit>("Ping") { server, _, x, y ->
                    server.gameSummon(unit, x, y)
                }
            }
        }
        handler.register(
                "addmoney", "<$PlayerPosition> <money>", "clientCommands.addmoney"
        ) { args: Array<String>, player: PlayerHess ->
            if (!room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.noStartGame"))
                return@register
            }
            if (isAdmin(player)) {
                if (notIsNumeric(args[0]) || notIsNumeric(args[1])) {
                    player.sendSystemMessage(player.i18NBundle.getinput("err.noNumber"))
                    return@register
                }

                val site = args[0].toInt() - 1
                val addMoneyPlayer = room.playerManage.getPlayer(site)
                if (addMoneyPlayer != null) {
                    GameEngine.data.gameFunction.suspendMainThreadOperations {
                        try {
                            addMoneyPlayer.credits += args[1].toInt()
                        } catch (e: IOException) {
                            error("[Player] addMoneyPlayer Error", e)
                        }
                    }
                }
            }
        }
        handler.register("nuke",  "clientCommands.nuke") { _: Array<String>, player: PlayerHess ->
            if (!room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.noStartGame"))
                return@register
            }
            if (player.isAdmin) {
                player.sendSystemMessage(player.i18NBundle.getinput("clientCommands.nuke.ping"))
                player.addData<(AbstractNetConnectServer, GamePingActions, Float, Float)->Unit>("Ping") { _, _, x, y ->
                    GameEngine.data.gameFunction.suspendMainThreadOperations {
                        val obj = SupBuild::class.java.findField("n", com.corrodinggames.rts.game.units.a.s::class.java)!!.get(null) as com.corrodinggames.rts.game.units.a.s
                        val no = com.corrodinggames.rts.game.units.h(false)
                        no.a(obj, false, PointF(x, y), null)
                        no.b(n.k(player.index))
                        //no.bX = n.k(player.site)
                        GameEngine.gameEngine.bS.j(no)
                        gameModule.gameLinkFunction.allPlayerSync()
                    }
                }
            }
        }
        handler.register("clone",  "clientCommands.clone") { _: Array<String>, player: PlayerHess ->
            if (!room.isStartGame) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.noStartGame"))
                return@register
            }
            if (player.isAdmin) {
                player.sendSystemMessage(player.i18NBundle.getinput("clientCommands.clone.ping"))
                player.addData<(AbstractNetConnectServer, GamePingActions, Float, Float)->Unit>("Ping") { _, _, x, y ->
                    gameModule.gameFunction.suspendMainThreadOperations {
                        val obj = SupBuild::class.java.findField("j", com.corrodinggames.rts.game.units.a.s::class.java)!!.get(null) as com.corrodinggames.rts.game.units.a.s
                        val no = com.corrodinggames.rts.game.units.h(false)
                        no.bX = n.k(player.index)
                        no.a(obj, false, PointF(x, y), null)
                        GameEngine.gameEngine.bS.j(no)
                        gameModule.gameLinkFunction.allPlayerSync()
                    }
                }
            }
        }
        handler.register("myid", "clientCommands.-") { _: Array<String>, player: PlayerHess ->
            player.sendSystemMessage(player.i18NBundle.getinput("myid", BigInteger(HexUtils.decodeHex(player.connectHexID)).toInt().let { if (it < 0) -it else it }))
        }
        /*
        handler.register("banu", "<ID>", "#BAN UNIT") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                when (args[0]) {
                    "0" -> {
                        if (!gameModule.room.banUnit.contains("fabricator")) {
                            gameModule.room.banUnit.add("fabricator")
                            player.sendSystemMessage("BAN 资源制造器")
                        } else {
                            player.sendSystemMessage("重复禁止 BAN 资源制造器")
                        }
                    }
                    "1" -> {
                        if (!gameModule.room.banUnit.contains("experimentalLandFactory")) {
                            gameModule.room.banUnit.add("experimentalLandFactory")
                            player.sendSystemMessage("BAN 实验工厂")
                        } else {
                            player.sendSystemMessage("重复禁止 BAN 实验工厂")
                        }
                    }
                    "2" -> {
                        if (!gameModule.room.banUnit.contains("builder")) {
                            gameModule.room.banUnit.add("builder")
                            player.sendSystemMessage("BAN 建造者")
                        } else {
                            player.sendSystemMessage("重复禁止 BAN 建造者")
                        }
                    }
                    else -> {
                        if (!gameModule.room.banUnit.contains(args[0])) {
                            gameModule.room.banUnit.add(args[0])
                            player.sendSystemMessage("BAN ${args[0]}")
                        }
                    }
                }
            }
        }
        */
        handler.register("ban", "<Name/Position>", "serverCommands.ban") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                val conTg: PlayerHess? = findPlayer(player, args[0])
                conTg?.let {
                    GameEngine.data.eventManage.fire(PlayerBanEvent(gameModule, player))
                    player.sendSystemMessage("Ban : ${args[0]} OK")
                }
            }
        }
        handler.register("kickx", "<Name/Position>", "#Kick Player") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                val conTg: PlayerHess? = findPlayer(player, args[0])
                conTg?.let {
                    it.kickPlayer("你被踢出服务器", 120)
                    player.sendSystemMessage("Kick : ${args[0]} OK")
                }
            }
        }
        handler.register("allmute", "<on/off>", "#Mute All") { args: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                gameModule.room.muteAll = "on".equals(args[0], true)
                player.sendSystemMessage("全体禁言 : ${if (gameModule.room.muteAll) "启用" else "禁用" } OK")
            }
        }
    }

    init {
        registerGameCoreCommand(handler)
        registerGameCommandX(handler)
        registerGameCustomCommand(handler)
        PluginManage.runRegisterServerClientCommands(handler)
    }

    private fun findPlayer(player: PlayerHess, findIn: String): PlayerHess? {
        var conTg: PlayerHess? = null

        var findNameIn: String? = null
        var findPositionIn: Int? = null

        if (IsUtils.isNumeric(findIn)) {
            findPositionIn = findIn.toInt()
        } else {
            findNameIn = findIn
        }

        findNameIn?.let { findName ->
            var count = 0
            gameModule.room.playerManage.playerAll.forEach {
                if (it.name.contains(findName, ignoreCase = true)) {
                    conTg = it
                    count++
                }
            }
            if (count > 1) {
                player.sendSystemMessage("目标不止一个, 请不要输入太短的玩家名")
                return@let
            }
            if (conTg == null) {
                player.sendSystemMessage("找不到玩家")
                return@let
            }
        }

        findPositionIn?.let { findPosition ->
            gameModule.room.playerManage.playerAll.forEach {
                if (it.position == findPosition) {
                    conTg = it
                }
            }
            if (conTg == null) {
                player.sendSystemMessage("找不到玩家")
                return@let
            }
        }

        if (conTg != null && conTg!!.isAdmin) {
            player.sendSystemMessage("不能踢出管理员")
            return null
        }

        return conTg
    }

    companion object {
        private val localeUtil = Data.i18NBundle
        private val gameModule = HeadlessModuleManage.hessLoaderMap[this::class.java.classLoader.toString()]!!
        private val room = gameModule.room
    }
}