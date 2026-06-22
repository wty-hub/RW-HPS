/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.core

import com.sun.jna.NativeLibrary
import net.rwhps.server.Main
import net.rwhps.server.core.ServiceLoader.ServiceType
import net.rwhps.server.core.thread.CallTimeTask
import net.rwhps.server.core.thread.Threads
import net.rwhps.server.data.global.Data
import net.rwhps.server.data.global.Data.serverCountry
import net.rwhps.server.data.global.NetStaticData
import net.rwhps.server.dependent.LibraryManager
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.game.room.RelayRoom
import net.rwhps.server.io.GameOutputStream
import net.rwhps.server.io.packet.type.PacketType
import net.rwhps.server.net.NetService
import net.rwhps.server.net.core.IRwHps
import net.rwhps.server.net.core.server.AbstractNetConnect
import net.rwhps.server.net.core.server.AbstractNetConnectServer
import net.rwhps.server.net.manage.DownloadManage
import net.rwhps.server.net.netconnectprotocol.FakeRwHps
import net.rwhps.server.net.netconnectprotocol.RwHps
import net.rwhps.server.net.netconnectprotocol.TypeRelay
import net.rwhps.server.net.netconnectprotocol.TypeRelayRebroadcast
import net.rwhps.server.net.netconnectprotocol.realize.GameVersionPacket
import net.rwhps.server.net.netconnectprotocol.realize.GameVersionRelay
import net.rwhps.server.net.netconnectprotocol.realize.GameVersionRelayRebroadcast
import net.rwhps.server.util.str.StringUtils
import net.rwhps.server.util.SystemUtils
import net.rwhps.server.util.Time
import net.rwhps.server.util.algorithms.Aes
import net.rwhps.server.util.algorithms.Rsa
import net.rwhps.server.util.annotations.mark.PrivateMark
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.file.load.I18NBundle
import net.rwhps.server.util.file.plugin.PluginData
import net.rwhps.server.util.inline.readBytes
import net.rwhps.server.util.inline.readFileListString
import net.rwhps.server.util.inline.toPrettyPrintingJson
import net.rwhps.server.util.log.Log
import net.rwhps.server.util.log.ex.PrintEx
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


/**
 * @author Dr (dr@der.kim)
 */
class Initialization {
    private fun checkEnvironment() {
        try {
            NativeLibrary.getProcess().name
        } catch (e: UnsatisfiedLinkError) {
            Log.clog("&r RW-HPS It may not be compatible with your architecture, there will be unexpected situations, Thank you !")
        }
    }

    private fun loadLang() {
        Data.i18NBundleMap["CN"] = I18NBundle(Main::class.java.getResourceAsStream("/bundles/HPS_zh_CN.properties")!!)
        Data.i18NBundleMap["HK"] = I18NBundle(Main::class.java.getResourceAsStream("/bundles/HPS_zh_HK.properties")!!)
        Data.i18NBundleMap["RU"] = I18NBundle(Main::class.java.getResourceAsStream("/bundles/HPS_ru_RU.properties")!!)
        Data.i18NBundleMap["EN"] = I18NBundle(Main::class.java.getResourceAsStream("/bundles/HPS_en_US.properties")!!)

        // Default use CN
        Data.i18NBundle = Data.i18NBundleMap["CN"]!!
    }

    private fun loadIpBin() {
        if (!Data.config.ipCheckMultiLanguageSupport) {
            return
        }/*
		try {
			Data.ip2Location = new IP2Location();
			Data.ip2Location.Open(FileUtil.getFolder(Data.Plugin_Data_Path).toFile("IP.bin").getPath(), true);
		} catch (IOException e) {
			Log.error("IP-LOAD ERR",e);
		}*/
    }

    private fun initRsa() {
        try {
            val rsa = Rsa().buildKeyPair()
            Rsa.getPublicKey(rsa.public)
            Rsa.getPrivateKey(rsa.private)
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    private fun initGetServerData() {
        Threads.newTimedTask(CallTimeTask.ServerUpStatistics, 1, 1, TimeUnit.MINUTES) {
            if (NetStaticData.RwHps.netType != IRwHps.NetType.NullProtocol) {
                try {
                    val data = when (NetStaticData.RwHps.netType) {
                        IRwHps.NetType.ServerProtocol, IRwHps.NetType.ServerProtocolOld, IRwHps.NetType.ServerTestProtocol -> {
                            BaseDataSend(
                                    IsServer = true,
                                    ServerData = BaseDataSend.Companion.ServerData(IpPlayerCountry = mutableMapOf<String, Int>().also {
                                        HeadlessModuleManage.hps.room.playerManage.playerGroup.eachAll { player ->
                                            val ipCountry = (player.con!! as AbstractNetConnect).ipCountry
                                            if (it.containsKey(ipCountry)) {
                                                it[ipCountry] = it[ipCountry]!! + 1
                                            } else {
                                                it[ipCountry] = 1
                                            }
                                        }
                                    })
                            )
                        }

                        IRwHps.NetType.RelayProtocol, IRwHps.NetType.RelayMulticastProtocol -> {
                            @PrivateMark
                            BaseDataSend(IsServer = false, RelayData = BaseDataSend.Companion.RelayData())
                        }

                        else -> {
                            BaseDataSend(IsServerRun = false, IsServer = false)
                        }
                    }


                    val out = GameOutputStream()
                    out.writeString("RW-HPS Statistics Data")
                    out.writeString(Data.core.serverConnectUuid)
                    out.writeBytesAndLength(
                            Aes.aesEncryptToBytes(data.toPrettyPrintingJson().toByteArray(Data.UTF_8), "RW-HPS Statistics Data")
                    )
                    val packet = out.createPacket(PacketType.SERVER_DEBUG_RECEIVE)
                    Socket().use {
                        it.connect(InetSocketAddress(InetAddress.getByName("relay.der.kim"), 6001), 10000)
                        DataOutputStream(it.getOutputStream()).use { outputStream ->
                            outputStream.writeInt(packet.bytes.size)
                            outputStream.writeInt(packet.type.typeInt)
                            outputStream.write(packet.bytes)
                            outputStream.flush()
                        }
                        it.close()
                    }
                } catch (e: Exception) {
                    // Ignored, should not be shown to the user
                }
            }
        }
    }

    companion object {
        @Volatile
        private var isClose = true

        internal fun startInit() {
            val pluginData = Data.core.settings
            initServerLanguage(pluginData)
            eula(pluginData)
        }

        /**
         * Choose the language environment according to JVM locale on first start
         */
        internal fun initServerLanguage(pluginData: PluginData, country: String = "") {
            serverCountry = if (country.isBlank()) {
                pluginData["serverCountry", { detectServerCountryFromLocale() }]
            } else {
                when {
                    country.contains("HK") || country.contains("CN") || country.contains("RU") -> country
                    else -> "CN"
                }.also {
                    pluginData["serverCountry"] = it
                }
            }

            Data.i18NBundle = Data.i18NBundleMap[serverCountry]!!
            Log.clog(Data.i18NBundle.getinput("server.language"))
        }

        private fun detectServerCountryFromLocale(): String {
            return when (java.util.Locale.getDefault().language) {
                "zh" -> when (java.util.Locale.getDefault().country) {
                    "HK", "MO", "TW" -> "HK"
                    else -> "CN"
                }
                "ru" -> "RU"
                else -> "CN"
            }
        }

        private fun eula(pluginData: PluginData) {
            // Eula
            if (pluginData["eulaVersion", ""] != Data.SERVER_EULA_VERSION) {
                PrintEx.waitLicense({
                    val eulaBytes = if (serverCountry == "CN") {
                        FileUtils.getInternalFileStream("/eula/Main-China.md").readBytes()
                    } else {
                        FileUtils.getInternalFileStream("/eula/Main-English.md").readBytes()
                    }
                    Log.clog(StringUtils.str(eulaBytes, Data.UTF_8))
                }, { pluginData["eulaVersion"] = Data.SERVER_EULA_VERSION }, { Data.core.exit() })
            }

            if (pluginData["initStart", true]) {
                PrintEx.waitLicense({
                    val initStart = if (serverCountry == "CN") {
                        "您看起来是第一次运行服务器, 需要WEB面板帮助您初始化吗"
                    } else {
                        "It looks like you are running the server for the first time. Do you need the WEB panel to help you initialize?"
                    }
                    Log.clog(initStart)
                }, {
                    pluginData["initStart"] = false
                }, {})
            }

            pluginData.save()
        }

        internal fun loadLib() {
            val libraryManager = LibraryManager()

            val excludeImport: (String) -> Unit = {
                val libData = it.split(":")
                if (libData[0] == "jar") {
                    libraryManager.implementation(libData[1], libData[2], libData[3], libData[4].let { classifier -> if (classifier == "null") "" else classifier })
                }
            }
            FileUtils.getInternalFileStream("/maven/ASM-Framework/compileOnly.txt").readFileListString().eachAll(excludeImport)
            FileUtils.getInternalFileStream("/maven/Server-Core/compileOnly.txt").readFileListString().eachAll(excludeImport)
            FileUtils.getInternalFileStream("/maven/TimeTaskQuartz/compileOnly.txt").readFileListString().eachAll(excludeImport)


            val libImport: (String) -> Unit = {
                val libData = it.split(":")
                if (libData[0] == "jar") {
                    libraryManager.exclude(libData[1], libData[2], libData[3], libData[4].let { classifier -> if (classifier == "null") "" else classifier })
                }
            }
            FileUtils.getInternalFileStream("/maven/Server-Core/implementation.txt").readFileListString().eachAll { libImport(it) }
            FileUtils.getInternalFileStream("/maven/ASM-Framework/implementation.txt").readFileListString().eachAll(libImport)
            FileUtils.getInternalFileStream("/maven/Server-Core/implementation.txt").readFileListString().eachAll(libImport)
            FileUtils.getInternalFileStream("/maven/TimeTaskQuartz/implementation.txt").readFileListString().eachAll(libImport)

            libraryManager.loadToClassLoader()

            loadPrivateLib()
        }

        private fun loadPrivateLib() {
            //val fileUtils = FileUtils.getFolder(Data.ServerPluginsPath).toFile("RW-HPS-JSLib.zip")
            // TODO: 加载JS-Lib
        }

        internal fun loadService() {
            @PrivateMark
            ServiceLoader.addService(ServiceType.ProtocolType, IRwHps.NetType.RelayProtocol.name, TypeRelay::class.java)
            @PrivateMark
            ServiceLoader.addService(ServiceType.ProtocolType, IRwHps.NetType.RelayMulticastProtocol.name, TypeRelayRebroadcast::class.java)
            @PrivateMark
            ServiceLoader.addService(ServiceType.Protocol, IRwHps.NetType.RelayProtocol.name, GameVersionRelay::class.java)
            @PrivateMark
            ServiceLoader.addService(ServiceType.Protocol, IRwHps.NetType.RelayMulticastProtocol.name, GameVersionRelayRebroadcast::class.java)

            ServiceLoader.addService(ServiceType.ProtocolPacket, IRwHps.NetType.GlobalProtocol.name, GameVersionPacket::class.java)

            ServiceLoader.addServiceObject(ServiceType.PacketType, IRwHps.NetType.GlobalProtocol.name, PacketType.INSTANCE)

            ServiceLoader.addService(ServiceType.IRwHps, "IRwHps", RwHps::class.java)
            ServiceLoader.addService(ServiceType.IRwHps, IRwHps.NetType.RemoteControlProtocol.name, FakeRwHps::class.java)
            ServiceLoader.addService(ServiceType.IRwHps, IRwHps.NetType.HttpProtocol.name, FakeRwHps::class.java)
        }

        internal data class BaseDataSend(
            val SendTime: Int = Time.concurrentSecond(),
            val ServerRunPort: Int = Data.config.port,
            val ServerNetType: String = NetStaticData.RwHps.netType.name,
            val System: String = SystemUtils.osName,
            val JavaVersion: String = SystemUtils.javaVersion,
            val VersionCount: String = Data.SERVER_CORE_VERSION,
            val IsServerRun: Boolean = true,
            val IsServer: Boolean,
            val ServerData: ServerData? = null,
            val RelayData: RelayData? = null,
        ) {
            companion object {
                data class ServerData(
                    val PlayerSize: Int = AtomicInteger().also {
                        NetStaticData.netService.eachAll { e: NetService -> it.addAndGet(e.getConnectSize()) }
                    }.get(),
                    val MaxPlayer: Int = Data.configServer.maxPlayer,
                    val PlayerVersion: Int = (NetStaticData.RwHps.typeConnect.abstractNetConnect as AbstractNetConnectServer).supportedVersionInt,
                    val IpPlayerCountry: Map<String, Int>,
                )

                @PrivateMark
                data class RelayData(
                    val PlayerSize: Int = AtomicInteger().also {
                        NetStaticData.netService.eachAll { e: NetService -> it.addAndGet(e.getConnectSize()) }
                    }.get(),
                    val RoomAllSize: Int = RelayRoom.roomAllSize,
                    val RoomNoStartSize: Int = RelayRoom.roomNoStartSize,
                    val RoomPublicListSize: Int = RelayRoom.roomPublicSize,
                    val PlayerVersion: Map<Int, Int> = RelayRoom.getAllRelayVersion(),
                    val IpPlayerCountry: Map<String, Int> = RelayRoom.getAllRelayIpCountry(),
                )
            }
        }
    }

    init {
        Runtime.getRuntime().addShutdownHook(object: Thread("Exit Handler") {
            override fun run() {
                if (!isClose) {
                    return
                }
                isClose = true

                Data.core.save()
                println("Exit Save Ok")

                System.setOut(Data.privateOut)
            }
        })

        checkEnvironment()
        //
        loadLang()

        //initRsa()
        initGetServerData()
    }
}