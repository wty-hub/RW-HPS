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
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.inline.toGson
import net.rwhps.server.util.log.Log
import net.rwhps.server.util.math.RandomUtils

/**
 * Server-Protocol configuration file
 *
 * Save data for serialization and deserialization
 * @author Dr (dr@der.kim)
 */
data class BeanServerConfig(
    /** 服务器ID, 作为后续 mods/maps/save 文件区分 */
    val serverID: String = RandomUtils.getRandomIetterString(5),

    val enterAd: String = "",
    val startAd: String = "",
    val maxPlayerJoinAd: String = "",
    val startPlayerJoinAd: String = "",

    /** 密码 */
    val passwd: String = "",

    /** 服务器最大人数 */
    val maxPlayer: Int = 10,
    /** only Admin (Auto) */
    val oneAdmin: Boolean = true,

    /** 禁止单人 -start（至少 2 人在线才可开始） */
    val denySinglePlayerStart: Boolean = true,
    /** 服务器最小Start人数 (-1 为禁用) */
    val startMinPlayerSize: Int = -1,
    /** 服务器最小AutoStart人数 (-1 为禁用) */
    val autoStartMinPlayerSize: Int = 4,
    /** 服务器最大游戏时间 (s) 2Hour (-1 为禁用) */
    val maxGameIngTime: Int = 7200,
    /** 服务器最大仅AI游戏时间 (s) 1Hour (-1 为禁用) */
    val maxOnlyAIGameIngTime: Int = 3600,

    val enableAI: Boolean = false,


    /** 最大发言长度 */
    val maxMessageLen: Int = 40,
    /** 最大单位数 */
    val maxUnit: Int = 200,
    /** 默认倍率 */
    val defIncome: Float = 1f,

    val isAfk: Boolean = true,
    val muteAll: Boolean = false,

    /** 点石成金 */
    val turnStoneIntoGold: Boolean = false,

    /** Mod 加载的错误信息 */
    val modsLoadErrorPrint: Boolean = false,

    /** 向 RWJS 客户端宣告 mod 传输能力（PREREGISTER RoomOption） */
    val enableModTransfer: Boolean = false,
    /** RWJS mod 传输协议版本，须与客户端 protocolVersion 一致 */
    val rwjsProtocolVersion: Int = 4,
    /** 单 mod 传输体积上限 (MB)，阶段 3 发送时校验 */
    val maxModTransferSizeMb: Int = 128,

    /** 是否保存 RePlay */
    val saveRePlayFile: Boolean = true,
    /***/
): AbstractBeanConfig(
        this::class.java, "rwhps.config.server"
) {
    fun minStartPlayerCount(): Int {
        var min = if (denySinglePlayerStart) 2 else 1
        if (startMinPlayerSize != -1) {
            min = maxOf(min, startMinPlayerSize)
        }
        return min
    }

    private fun checkValue() {
        // 拒绝最大玩家数超过最小开始玩家数
        if (maxPlayer < startMinPlayerSize) {
            Log.warn("MaxPlayer < StartMinPlayerSize , Reset !")
            coverField("StartMinPlayerSize", 0)
        }
        if (maxPlayer > 100) {
            Log.warn("MaxPlayer > GameMaxPlayerSize , Reset !")
            //coverField("MaxPlayer",100)
        }
    }

    companion object {
        val fileUtils = FileUtils.getFolder(Data.ServerDataPath).toFile("ConfigServer.json")

        @JvmStatic
        fun stringToClass(): BeanServerConfig {
            val config: BeanServerConfig = BeanServerConfig::class.java.toGson(fileUtils.readFileStringData())
            config.bindFile(fileUtils)
            config.readProperty()
            config.checkValue()
            return config
        }
    }
}
