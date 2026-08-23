/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *  
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.internal.headless.inject.core

import net.rwhps.server.dependent.redirections.game.MainThreadGate
import net.rwhps.server.game.headless.core.AbstractGameFunction
import net.rwhps.server.util.log.Log

/**
 *
 *
 * @date 2023/12/22 17:17
 * @author Dr (dr@der.kim)
 */
class GameFunction : AbstractGameFunction {
    override fun suspendMainThreadOperations(run: Runnable) {
        try {
            MainThreadGate.runExclusive(run)
        } catch (e: Exception) {
            Log.error("Hess MainThreadOperations", e)
        }
    }

    override val neverEnd: IntArray get() = intArrayOf(GameEngine.gameEngine.bL.C, GameEngine.gameEngine.bL.D)
}