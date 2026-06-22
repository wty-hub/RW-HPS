/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.beta

import net.rwhps.server.core.thread.CallTimeTask
import net.rwhps.server.core.thread.Threads
import net.rwhps.server.data.global.Data
import net.rwhps.server.data.global.NetStaticData
import net.rwhps.server.func.StrCons
import net.rwhps.server.game.event.EventManage
import net.rwhps.server.game.event.core.EventListenerHost
import net.rwhps.server.game.event.game.ServerGameOverEvent
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.net.core.IRwHps
import net.rwhps.server.net.core.server.AbstractNetConnectServer
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.util.IpUtils
import net.rwhps.server.util.algorithms.digest.DigestUtils
import net.rwhps.server.util.annotations.core.EventListenerHandler
import net.rwhps.server.util.game.command.CommandHandler
import net.rwhps.server.util.log.Log
import net.rwhps.server.util.math.RandomUtils
import net.rwhps.server.util.str.StringFilteringUtil.cutting
import okhttp3.FormBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


internal class UpListMain: Plugin() {
    private val privateIp: String
        get() {
            var privateIpTemp = IpUtils.getPrivateIp()
            if (privateIpTemp.isNullOrBlank()) {
                privateIpTemp = "10.0.0.1"
            }
            return privateIpTemp
        }
    private var port = Data.config.port.toString()

    private var versionBeta = false
    private var versionGame = "1.15"
    private var versionGameInt = 176

    private var upServerList = false

    private var userId = ""
    private var privateToken = ""
    private var tokenMd5 = ""
    private var timestampNs = 0L
    private var roomId = ""
    private var slot = 0

    private var uplistFlag = 0
    private var over = false

    override fun init() {
        AddLang(this)
    }

    override fun registerEvents(eventManage: EventManage) {
        eventManage.registerListener(object: EventListenerHost {
            @EventListenerHandler
            fun gameover(gameOverEvent: ServerGameOverEvent) {
                if (over) {
                    remove({})
                }
                over = false
            }
        })
    }

    override fun registerCoreCommands(handler: CommandHandler) {
        handler.removeCommand("upserverlist")
        handler.removeCommand("upserverlistnew")

        handler.register("uplist", "[command...]", "serverCommands.upserverlist") { args: Array<String>?, log: StrCons ->
            if (!args.isNullOrEmpty()) {
                when (args[0]) {
                    "add" -> NetStaticData.checkServerStartNet { if (args.size > 1) add(log, args[1]) else add(log) }
                    "update" -> NetStaticData.checkServerStartNet { update() }
                    "remove" -> NetStaticData.checkServerStartNet { remove(log) }
                    "help" -> log(Data.i18NBundle.getinput("uplist.help"))
                    else -> log("Check UpList Command ! use 'uplist help'")
                }
            } else {
                log("Check UpList Command ! use 'uplist help'")
            }
        }
    }

    override fun registerServerClientCommands(handler: CommandHandler) {
        fun isAdmin(player: PlayerHess): Boolean {
            if (player.isAdmin) {
                return true
            }
            player.sendSystemMessage(player.i18NBundle.getinput("err.noAdmin"))
            return false
        }

        handler.register("toup", "#up") { _: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                when (uplistFlag) {
                    0 -> {
                        thread {
                            player.sendSystemMessage("耐心等待, 错误信息不打印, 请自行在控制台提前实验")
                            prepareVersionInfo()
                            over = true
                            this.port = port.ifBlank { Data.config.port.toString() }
                            Threads.newThreadCore { upServerList = true; uplistFlag = 1; uplist(player::sendSystemMessage) }
                        }
                    }
                    else -> player.sendSystemMessage("Already on the list")
                }

            }
        }
        handler.register("tonp", "#up") { _: Array<String>, player: PlayerHess ->
            if (isAdmin(player)) {
                when (uplistFlag) {
                    1 -> {
                        thread {
                            player.sendSystemMessage("耐心等待")
                            remove(player::sendSystemMessage)
                        }
                    }
                    else -> player.sendSystemMessage("no uplist")
                }
            }
        }
    }

    private fun prepareVersionInfo() {
        if (NetStaticData.RwHps.netType.ordinal in IRwHps.NetType.ServerProtocol.ordinal .. IRwHps.NetType.ServerTestProtocol.ordinal) {
            (NetStaticData.RwHps.typeConnect.abstractNetConnect as AbstractNetConnectServer).run {
                versionBeta = supportedversionBeta
                versionGame = supportedversionGame
                versionGameInt = supportedVersionInt
            }
        } else {
            versionBeta = false
            versionGame = "1.15-Other"
            versionGameInt = 176
        }
    }

    private fun initRoomCredentials() {
        timestampNs = System.nanoTime()
        userId = "u_${UUID.randomUUID()}"
        privateToken = RandomUtils.getRandomByteArray(20).joinToString("") { "%02x".format(it) }
        tokenMd5 = DigestUtils.md5Hex(privateToken)
        roomId = ""
        slot = 0
    }

    private fun add(log: StrCons, port: String = "") {
        if (!upServerList) {
            prepareVersionInfo()
            this.port = port.ifBlank { Data.config.port.toString() }
            Threads.newThreadCore { upServerList = true; uplistFlag = 1; uplist(Log::clog) }
        } else {
            log("Already on the list")
        }
    }

    private fun buildAddArgs(): Map<String, String> {
        val serverName = cutting(Data.config.serverName, 15)
        return linkedMapOf(
            "action" to "add",
            "user_id" to userId,
            "_1" to timestampNs.toString(),
            "private_token" to privateToken,
            "private_token_2" to DigestUtils.md5Hex(tokenMd5),
            "confirm" to DigestUtils.md5Hex("a$tokenMd5"),
            "tx2" to sha256Hex4("_${userId}5"),
            "tx3" to sha256Hex4("_${userId}${timestampNs + 5}"),
            "game_version" to versionGameInt.toString(),
            "game_version_string" to versionGame,
            "game_version_beta" to versionBeta.toString(),
            "password_required" to Data.configServer.passwd.isNotBlank().toString(),
            "game_name" to serverName,
            "created_by" to serverName,
            "game_map" to getMapName,
            "game_status" to gameStatus,
            "private_ip" to privateIp,
            "port_number" to port,
            "game_mode" to "skirmishMap",
            "player_count" to Data.config.upListPlayerCount.toString(),
            "max_player_count" to Data.config.upListMaxPlayerCount.toString(),
        )
    }

    private fun roomState(): Map<String, String> {
        val serverName = cutting(Data.config.serverName, 15)
        return mapOf(
            "password_required" to Data.configServer.passwd.isNotBlank().toString(),
            "created_by" to serverName,
            "private_ip" to privateIp,
            "port_number" to port,
            "game_map" to getMapName,
            "game_mode" to "skirmishMap",
            "game_status" to gameStatus,
            "player_count" to Data.config.upListPlayerCount.toString(),
            "max_player_count" to Data.config.upListMaxPlayerCount.toString(),
        )
    }

    private fun uplist(print: (String)->Unit) {
        initRoomCredentials()
        val addArgs = buildAddArgs()
        Log.debug(addArgs.entries.joinToString("&") { "${it.key}=${it.value}" })

        val addGs1Response = postForm(MASTER_GS1, addArgs)
        val addGs4Response = postForm(MASTER_GS4, addArgs)
        val addGs1 = isOk(addGs1Response)
        val addGs4 = isOk(addGs4Response)

        if (addGs1) {
            parseAddResponse(addGs1Response)
        }
        if (roomId.isEmpty() && addGs4) {
            parseAddResponse(addGs4Response)
        }

        if ((addGs1 || addGs4) && roomId.isNotEmpty()) {
            if (addGs1 && addGs4) {
                print(Data.i18NBundle.getinput("err.yesList"))
            } else {
                print(Data.i18NBundle.getinput("err.ynList"))
            }
        } else {
            print(Data.i18NBundle.getinput("err.noList"))
            resetUpListState()
            return
        }

        doSelfInfo(print)

        Threads.newTimedTask(CallTimeTask.CustomUpServerListTask, 50, 50, TimeUnit.SECONDS) { update() }
    }

    private fun doSelfInfo(print: (String)->Unit) {
        if (roomId.isEmpty()) {
            return
        }
        val args = mapOf(
            "action" to "self_info",
            "id" to roomId,
            "port" to port,
            "tx3" to sha256Hex4("-$roomId${5 + slot}"),
        )
        val gs1 = isOk(postForm(MASTER_GS1, args))
        val gs4 = isOk(postForm(MASTER_GS4, args))
        if (gs1 || gs4) {
            print(Data.i18NBundle.getinput("err.yesOpen"))
        } else {
            print(Data.i18NBundle.getinput("err.noOpen"))
        }
    }

    private fun update() {
        if (!upServerList || roomId.isEmpty()) {
            return
        }
        val args = linkedMapOf(
            "action" to "update",
            "id" to roomId,
            "private_token" to privateToken,
        )
        args.putAll(roomState())
        postForm(MASTER_GS1, args)
        postForm(MASTER_GS4, args)
    }

    private fun remove(log: StrCons) {
        if (!upServerList) {
            log("Not uploaded No deletion is required")
            return
        }
        if (roomId.isNotEmpty()) {
            val args = mapOf(
                "action" to "remove",
                "id" to roomId,
                "private_token" to privateToken,
            )
            postForm(MASTER_GS1, args)
            postForm(MASTER_GS4, args)
        }
        resetUpListState()
        log("Deleted UPLIST")
    }

    private fun resetUpListState() {
        upServerList = false
        uplistFlag = 0
        roomId = ""
        slot = 0
    }

    private fun postForm(url: String, data: Map<String, String>): String {
        val form = FormBody.Builder()
        data.forEach { (key, value) -> form.add(key, value) }
        return Data.core.rwHttp.doPost(url, form)
    }

    private fun isOk(text: String): Boolean {
        return text.contains("CORRODINGGAMES") && !text.contains("[FAILED]")
    }

    private fun parseAddResponse(text: String) {
        val lines = text.trim().lines().filter { it.isNotBlank() }
        if (lines.size < 2) {
            Log.error("[UpList] unexpected add response: ${text.take(200)}")
            return
        }
        val parts = lines[1].split(",")
        roomId = parts[0]
        slot = parts.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun sha256Hex4(value: String): String {
        return DigestUtils.sha256Hex(value).substring(0, 4)
    }

    private val gameStatus get() = if (isRelay || HeadlessModuleManage.hps.room.isStartGame) "ingame" else "battleroom"

    private val isRelay get() = (NetStaticData.RwHps.netType == IRwHps.NetType.RelayProtocol || NetStaticData.RwHps.netType == IRwHps.NetType.RelayMulticastProtocol)

    private val getMapName get() = cutting(Data.config.gameMap.ifBlank { Data.config.subtitle }, 15)

    private companion object {
        const val MASTER_GS1 = "http://gs1.corrodinggames.com/masterserver/1.4/interface"
        const val MASTER_GS4 = "http://gs4.corrodinggames.net/masterserver/1.4/interface"
    }

    /**
     * Inject multiple languages into the server
     * @author Dr (dr@der.kim)
     */
    private class AddLang(val plugin: Plugin) {
        init {
            help()
        }

        private fun help() {
            loadCN(
                    "uplist.help", """       
        [uplist add] 服务器上传到列表 显示配置文件端口
        [uplist add (port)] 服务器上传到列表 服务器运行配置文件端口 显示自定义端口
        [uplist update] 立刻更新列表服务器信息
        [uplist remove] 取消服务器上传列表
        [uplist help] 获取帮助
        """.trimIndent()
            )
            loadEN(
                    "uplist.help", """
        [uplist add] Server upload to list Show profile port
        [uplist add (port)] Server upload to list Server running profile port Display custom port
        [uplist update] Update list server information immediately
        [uplist remove] Cancel server upload list
        [uplist help] Get Help
        """.trimIndent()
            )
            loadHK(
                    "uplist.help", """        
        [uplist add] 服务器上传到列表 显示配置文件端口
        [uplist add (port)] 服务器上传到列表 服务器运行配置文件端口 显示自定义端口
        [uplist update] 立刻更新列表服务器信息
        [uplist remove] 取消服务器上传列表
        [uplist help] 获取帮助
        """.trimIndent()
            )
            loadRU(
                    "uplist.help", """
        [uplist add] Загрузка сервера в список Показать порт профиля
        [uplist add (port)] Загрузка сервера в список Порт запущенного профиля сервера Показать пользовательские порты
        [uplist update] Немедленное обновление информации сервера списка
        [uplist remove] Отмена загрузки сервера в список
        [uplist help] Получить помощь
        """.trimIndent()
            )
        }

        private fun loadCN(k: String, v: String) {
            plugin.loadLang("CN", k, v)
        }

        private fun loadEN(k: String, v: String) {
            plugin.loadLang("EN", k, v)
        }

        private fun loadHK(k: String, v: String) {
            plugin.loadLang("HK", k, v)
        }

        private fun loadRU(k: String, v: String) {
            plugin.loadLang("RU", k, v)
        }
    }
}
// 给岁月以文明，而不是给文明以岁月
