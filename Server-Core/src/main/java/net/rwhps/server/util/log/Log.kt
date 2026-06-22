/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.util.log

import net.rwhps.server.data.global.Data
import net.rwhps.server.util.str.Parser.parseLog
import java.text.MessageFormat

/**
 * Log 接口
 *
 * 在这里实现 RW-HPS 的主要输出
 *
 * 如果您需要使用 [println] 等接口, 那么请使用 [Data.privateOut] , [System.out] 被 `RW-HPS` 替换
 *
 * 替换后只检测 `/n` `/r/n` 来输出
 *
 * @date 2020年3月8日星期日 3:54
 * @author Dr (dr@der.kim)
 * @version 1.1
 */
@Suppress("UNUSED")
object Log: LogCore() {
    /** 默认错误捕获器 */
    val errorDispose = Thread.UncaughtExceptionHandler { thread, error ->
        if (thread != null) {
            error("[Error] ${thread.name}", error)
        } else {
            error("[Error] ?Thread", error)
        }
    }

    init {
        // 设置默认的线程异常捕获处理器
        Thread.setDefaultUncaughtExceptionHandler(errorDispose)

        // 设置主线程名字和捕获器
        Thread.currentThread().name = "Main"
        Thread.currentThread().uncaughtExceptionHandler = errorDispose
    }

    @JvmStatic
    fun skipping(tag: Any) = logs(Logg.OFF, tag)
    @JvmStatic
    fun skipping(tag: Any, e: Any, vararg params: Any) = logs(Logg.OFF, tag, e.parse(*params))

    @JvmStatic
    fun fatal(tag: Any) = logs(Logg.FATAL, tag)
    @JvmStatic
    fun fatal(tag: Any, e: Any, vararg params: Any) = logs(Logg.FATAL, tag, e.parse(*params))

    @JvmStatic
    fun error(tag: Any) = logs(Logg.ERROR, tag)
    @JvmStatic
    fun error(tag: Any, e: Any, vararg params: Any) = logs(Logg.ERROR, tag, e.parse(*params))

    @JvmStatic
    fun warn(tag: Any) = logs(Logg.WARN, tag)
    @JvmStatic
    fun warn(tag: Any, e: Any, vararg params: Any) = logs(Logg.WARN, tag, e.parse(*params))

    @JvmStatic
    fun info(tag: Any) = logs(Logg.INFO, tag)
    @JvmStatic
    fun info(tag: Any, e: Any, vararg params: Any) = logs(Logg.INFO, tag, e.parse(*params))

    @JvmStatic
    fun debug(tag: Any) = logs(Logg.DEBUG, tag)
    @JvmStatic
    fun debug(tag: Any, e: Any, vararg params: Any) = logs(Logg.DEBUG, tag, e.parse(*params))

    @JvmStatic
    fun track(tag: Any) = logs(Logg.TRACK, tag)
    @JvmStatic
    fun track(tag: Any, e: Any, vararg params: Any) = logs(Logg.TRACK, tag, e.parse(*params))

    @JvmStatic
    fun all(tag: Any)= logs(Logg.ALL, tag)
    @JvmStatic
    fun all(tag: Any, e: Any, vararg params: Any)= logs(Logg.ALL, tag, e.parse(*params))

    @JvmStatic
    fun clog(e: Any, vararg params: Any) = logs(Logg.CONSOLE, "", e.parse(*params))

    private fun Any.parse(vararg params: Any): Any {
        return if (this is String) {
            if (params.isEmpty()) {
                this
            } else {
                MessageFormat(parseLog(this, *params)).format(params)
            }
        } else {
            this
        }
    }
}