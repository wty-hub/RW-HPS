/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *  
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.game.manage

import net.rwhps.server.data.global.Data
import net.rwhps.server.net.rwpp.ModCatalogManager
import net.rwhps.server.struct.list.Seq
import net.rwhps.server.struct.map.BaseMap.Companion.toSeq
import net.rwhps.server.struct.map.ObjectMap
import net.rwhps.server.struct.map.OrderedMap

/**
 * Mods 加载管理器
 * 这里加载的 Mod 默认启用
 *
 * @author Dr (dr@der.kim)
 */
object ModManage {
    /** 游戏核心单位的命名(便于分辨) */
    private val coreName = "RW-HPS CoreUnits"

    /** 游戏单位校验数据*/
    private var enabledMods = OrderedMap<String, ObjectMap<String, Int>>()

    /** 启用的MOD(名字)-暂时无效 */
    private var enabledModsName = Seq<String>()

    /** MOD的名字列表 */
    private var modList = Seq<String>()

    /**
     * 从 GAME-Hess 获取Mod数据
     *
     * @return 获取到的非原版数量
     */
    @JvmStatic
    fun load(): Int {
        enabledMods = HeadlessModuleManage.hps.gameUnitData.getUnitData(coreName)
        enabledModsName.clear()

        modList = enabledMods.keys.toSeq()

        return (enabledMods.size - 1).also {
            HeadlessModuleManage.hps.gameUnitData.useMod = (it > 0)
            if (it > 0) {
                ModCatalogManager.refresh()
            } else {
                ModCatalogManager.invalidate()
            }
        }
    }

    /**
     * 重新从 [Data.Plugin_Mods_Path] 读取Mod
     * @return 读取到的Mod数量
     */
    @JvmStatic
    fun reLoadMods(): Int {
        HeadlessModuleManage.hps.gameLinkNet.reBootServer {
            HeadlessModuleManage.hps.gameLinkFunction.clean()
            HeadlessModuleManage.hps.gameUnitData.reloadUnitData()
        }

        return load()
    }


    /**
     * 获取 Mod 列表
     * @return Seq<String>
     */
    @JvmStatic
    fun getModsList(): Seq<String> = modList
}