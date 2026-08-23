/*
 *
 *  * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *  *
 *  * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *  *
 *  * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 *
 */

package net.rwhps.server.dependent.redirections.game

import net.rwhps.asm.api.listener.RedirectionListener
import net.rwhps.server.data.global.Data
import net.rwhps.server.util.Sync
import net.rwhps.server.util.annotations.mark.AsmMark

/**
 *
 *
 * @date 2024/1/20 12:16
 * @author Dr (dr@der.kim)
 */
@AsmMark.ClassLoaderCompatible
object FPSSleepRedirections : RedirectionListener {
    private val fpsLock = Data.configHeadless.headlessFPS
    private val sync = Sync()
    var deltaMillis = 0L

    override fun invoke(obj: Any, desc: String, vararg args: Any?) {
        MainThreadGate.drain()
        deltaMillis = args[0].toString().toLong()
//        val fps = if (HeadlessModuleManage.initHPS()) {
//            HeadlessModuleManage.hps.gameHessData.gameFPS
//        } else 0
//        Log.clog("DeltaMillis: $deltaMillis, FPS: $fps")
        sync.sync(fpsLock)
    }
}