/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.net.rwpp.packet

import net.rwhps.server.io.GameInputStream
import net.rwhps.server.io.GameOutputStream
import net.rwhps.server.io.packet.Packet

/**
 * RWJS [ModPacket] 读写，包体格式与 RWJS 客户端一致。
 */
object RwppModPacket {
    data class DownloadRequest(val mods: String)

    data class ChunkAck(val name: String, val ackChunkIndex: Int)

    fun readDownloadRequest(packet: Packet): DownloadRequest {
        GameInputStream(packet).use {
            return DownloadRequest(it.readString())
        }
    }

    fun readChunkAck(packet: Packet): ChunkAck {
        GameInputStream(packet).use {
            return ChunkAck(it.readString(), it.readInt())
        }
    }

    fun writeReloadFinish(): Packet {
        val out = GameOutputStream()
        out.writeInt(1)
        return out.createPacket(net.rwhps.server.io.packet.type.PacketType.MOD_RELOAD_FINISH)
    }
}
