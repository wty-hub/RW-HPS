/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */
package net.rwhps.server.net.rudp

import net.rwhps.server.io.output.DisableSyncByteArrayOutputStream
import net.rwhps.server.util.internal.net.rudp.ReliableServerSocket
import net.rwhps.server.util.io.IOUtils
import net.rwhps.server.util.log.Log
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.DataInputStream

/**
 * @author Dr (dr@der.kim)
 * @date 2023/7/16 20:48
 */
internal class ReliableServerSocketTest {
    @Test
    @Disabled("Manual RUDP debug server; blocks forever on accept()")
    fun startPort() {
        Log.set("ALL")

        ReliableServerSocket(5123).use {
            while (true) {
                val socket = it.accept()
                while (true) {
                    val read = DataInputStream(socket.getInputStream())
                    val length = read.readInt()
                    val type = read.readInt()

                    Log.debug(length)
                    Log.debug(type)

                    val outputStream = DisableSyncByteArrayOutputStream()

                    var len = 0
                    var n: Int
                    val inputStream = socket.getInputStream()
                    while (IOUtils.EOF != inputStream.read().also { n = it } && len < length) {
                        outputStream.write(n)
                        len++
                    }

                    Log.debug(length)
                    Log.debug(type)
                    Log.debug(len)
                }
            }
        }
    }
}