/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.net.rwpp

import net.rwhps.server.data.global.Data
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.util.file.FileUtils
import java.io.File

/**
 * 判定服务器是否向 RWJS 客户端宣告 mod 传输能力，并计算 [RwppRoomOption.allModsSize]。
 */
object ModTransferSupport {
    /** 配置开启且已加载非原版 mod 时，才在 PREREGISTER 中注入 RWJS RoomOption。 */
    @JvmStatic
    fun isActive(): Boolean {
        if (!Data.configServer.enableModTransfer) {
            return false
        }
        return HeadlessModuleManage.hps.gameUnitData.useMod
    }

    @JvmStatic
    fun currentRoomOption(): RwppRoomOption = RwppRoomOption(
        canTransferMod = true,
        allModsSize = computeAllModsSize().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        protocolVersion = Data.configServer.rwjsProtocolVersion,
    )

    @JvmStatic
    fun preregisterPrefix(): String = currentRoomOption().preregisterPrefix()

    /**
     * 统计 [Data.Plugin_Mods_Path] 下全部 mod 文件体积（字节），供客户端下载进度 UI 使用。
     */
    @JvmStatic
    fun computeAllModsSize(): Long {
        val root = FileUtils.getFolder(Data.Plugin_Mods_Path).file
        if (!root.exists()) {
            return 0L
        }
        return directorySize(root)
    }

    private fun directorySize(file: File): Long {
        if (!file.exists()) {
            return 0L
        }
        if (file.isFile) {
            return file.length()
        }
        var total = 0L
        file.listFiles()?.forEach { child ->
            total += directorySize(child)
        }
        return total
    }
}
