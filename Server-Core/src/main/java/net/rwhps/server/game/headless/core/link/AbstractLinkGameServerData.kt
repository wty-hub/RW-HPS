/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.game.headless.core.link

import net.rwhps.server.util.annotations.mark.GameSimulationLayer
import net.rwhps.server.util.log.exp.ImplementedException

/**
 * Link The game comes with settings to avoid some of the distractions caused by confusion
 *
 * @author Dr (dr@der.kim)
 */
interface AbstractLinkGameServerData {
    companion object {
        const val DEFAULT_MAP_NAME = "Crossing Large (10p)"
        const val DEFAULT_MAP_PLAYER = "[z;p10]"
        const val DEFAULT_CREDITS = 0
        const val DEFAULT_FOG = 2
        const val DEFAULT_NUKES = false
        const val DEFAULT_SHARED_CONTROL = false
        const val DEFAULT_AI_DIFFICULTY = 1
        const val DEFAULT_INCOME = 1.0f
        const val DEFAULT_STARTING_UNITS = 1
    }

    @GameSimulationLayer.GameSimulationLayer_KeyWords("overrideTeamLayout: unhandled layout:")
    val teamOperationsSyncObject: Any

    var maxUnit: Int

    var sharedcontrol: Boolean

    var fog: Int

    var nukes: Boolean

    var credits: Int

    var aiDifficuld: Int

    var income: Float

    var startingunits: Int

    /** Restore the original battle-room settings and notify connected clients. */
    fun resetRoomToDefaults()

    fun getDefPlayerData(): AbstractLinkPlayerData {
        return object: AbstractLinkPlayerData {
            private val error: () -> Nothing get() = throw ImplementedException.PlayerImplementedException("[Player] No Bound PlayerData")

            override fun updateDate() {
                throw ImplementedException.PlayerImplementedException("[Player] No Bound PlayerData")
            }
            override val survive get() = error()
            override val unitsKilled get() = error()
            override val buildingsKilled get() = error()
            override val experimentalsKilled get() = error()
            override val unitsLost get() = error()
            override val buildingsLost get() = error()
            override val experimentalsLost get() = error()
            override var credits: Int = 0
            override val name get() = error()
            override val connectHexID get() = error()
            override var index = 0
            override var team = 0
            override var startUnit = 0
            override var color = 0
            override var aiDifficulty = 0
        }
    }

    @Throws(ImplementedException.PlayerImplementedException::class)
    fun getPlayerData(position: Int): AbstractLinkPlayerData

    fun getPlayerAIData(position: Int): AbstractLinkPlayerData
}