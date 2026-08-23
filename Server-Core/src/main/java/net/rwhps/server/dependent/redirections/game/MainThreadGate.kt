/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.dependent.redirections.game

import net.rwhps.server.util.log.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 把需要碰游戏世界的操作排进 Slick `updateAndRender` 之前执行,
 * 避免 Netty 线程与渲染并发重载存档把地图 [com.corrodinggames.rts.gameFramework.l.bL] 拔空。
 *
 * 游戏循环尚未跑起来 ( [gameThread] == null ) 或调用方已在游戏线程上时直接执行,
 * 以保持启动期与嵌套调用行为。
 */
object MainThreadGate {
    const val WAIT_TIMEOUT_SECONDS = 60L

    private val queue = ConcurrentLinkedQueue<QueuedOp>()

    @Volatile
    var gameThread: Thread? = null
        internal set

    fun markGameThread() {
        gameThread = Thread.currentThread()
    }

    fun runExclusive(run: Runnable) {
        val loopThread = gameThread
        if (loopThread == null || loopThread === Thread.currentThread()) {
            run.run()
            return
        }

        val latch = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        queue.add(QueuedOp {
            try {
                run.run()
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        })

        if (!latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Log.error("Hess MainThreadOperations timed out after ${WAIT_TIMEOUT_SECONDS}s")
            return
        }
        error.get()?.let { throw it }
    }

    /**
     * 在游戏循环 `updateAndRender` 开头调用: 记下当前线程并执行排队任务。
     * 单条任务异常只记日志, 不打断后续任务或渲染。
     */
    fun drain() {
        markGameThread()
        while (true) {
            val op = queue.poll() ?: break
            try {
                op.body.run()
            } catch (e: Exception) {
                Log.error("Hess MainThreadOperations", e)
            }
        }
    }

    internal fun resetForTest() {
        gameThread = null
        queue.clear()
    }

    private class QueuedOp(val body: Runnable)
}
