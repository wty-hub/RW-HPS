/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.data.bean

import net.rwhps.server.data.global.Data
import net.rwhps.server.util.SystemUtils
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.inline.toGson
import net.rwhps.server.util.math.RandomUtils


/**
 * The server is primarily controlled
 *
 * Save data for serialization and deserialization
 * @author Dr (dr@der.kim)
 */
data class BeanCoreConfig(
    val noteEnglish: String = """
        Different protocols use different configuration files, please go to the corresponding file ConfigServer/ConfigRelay
    """.trimIndent(), val noteChina: String = """
        不同协议使用的配置文件不同, 请自行前往对应文件 ConfigServer/ConfigRelay
    """.trimIndent(),

    /** Default startup command */
    val defStartCommand: String = "start",

    val log: String = "WARN",

    val cmdTitle: String = "",

    /** 更新是否使用测试版本 */
    val followBetaVersion: Boolean = false,

    /** Port */
    val port: Int = 5123,

    /** 服务器名称 */
    val serverName: String = "RW-HPS",
    /** 上列表显示的地图名, 留空则使用 subtitle, 均为空则上列表显示空字符串 */
    val gameMap: String = "",
    /** 上列表显示的当前人数 */
    val upListPlayerCount: Int = 0,
    /** 上列表显示的最大人数 */
    val upListMaxPlayerCount: Int = 0,
    /** 标题, 留空使用地图名 */
    val subtitle: String = "",
    /** Automatically after starting UPLIST */
    val autoUpList: Boolean = false,

    /** ip多语言支持 */
    val ipCheckMultiLanguageSupport: Boolean = true,

    /** Single user relay disable pop-up selection */
    val singleUserRelay: Boolean = false,
    /** Default mods configuration for single user relay */
    val singleUserRelayMod: Boolean = false,

    /** 混合协议监听, 会使用 [BeanCoreConfig.port] 作为HTTP/HTTPS/R-Con/Game, 支持SSL */
    val mixProtocolEnable: Boolean = false,
    val rconMixEnable: Boolean = false,
    val webMixEnable: Boolean = false,

    val rconPort: Int = 0,
    val rconPasswd: String = RandomUtils.getRandomIetterString(10),

    /** Web的 Port, 不为 0 时在对应端口启用一个HTTP服务 */
    val webPort: Int = 0,
    /** Test : HTTP 鉴权 */
    val webToken: String = RandomUtils.getRandomIetterString(10),
    /** Web HOST 限制 */
    val webHOST: String = "",
    /** 启用SSL支持(需要使用jks) */
    val sslEnable: Boolean = false,
    /** 混合协议, 允许在启用SSL的情况下, 同端口服务HTTP */
    val sslMixEnable: Boolean = false,
    /** SSL密码 */
    val sslPasswd: String = "RW-HPS",

    var runPid: Long = 0
): AbstractBeanConfig(
        this::class.java, "rwhps.config.core"
) {
    override fun save() {
        runPid = SystemUtils.pid
        super.save()
    }

    companion object {
        val fileUtils = FileUtils.getFolder(Data.ServerDataPath).toFile("Config.json")

        @JvmStatic
        fun stringToClass(): BeanCoreConfig {

            val config: BeanCoreConfig = BeanCoreConfig::class.java.toGson(fileUtils.readFileStringData())
            config.bindFile(fileUtils)
            config.readProperty()
            return config
        }
    }
}