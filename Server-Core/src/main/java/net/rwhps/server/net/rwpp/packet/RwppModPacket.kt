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
import net.rwhps.server.io.packet.type.PacketType
import net.rwhps.server.net.rwpp.RwppConstants

/**
 * RWJS [ModPacket] 读写，包体格式与 RWJS 客户端一致。
 */
object RwppModPacket {
    data class DownloadRequest(val mods: String)

    data class ChunkAck(val name: String, val ackChunkIndex: Int)

    data class ChunkMetadata(
        val name: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val totalSize: Long,
        val sha256: String,
    )

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

    fun readReloadFinish(packet: Packet) {
        GameInputStream(packet).use {
            require(it.readInt() == 1) { "reload finish marker must be 1" }
            require(it.getSize() == 0L) { "reload finish packet has trailing data" }
        }
    }

    /** Encodes a TXJS v4 type 511 chunk with caller-supplied metadata. */
    @JvmStatic
    fun writeDownloadModChunk(metadata: ChunkMetadata, bytes: ByteArray): Packet {
        require(metadata.name.isNotBlank()) { "mod name must not be blank" }
        require(metadata.chunkIndex >= 0) { "chunkIndex must not be negative" }
        require(metadata.totalChunks > 0) { "totalChunks must be positive" }
        require(metadata.chunkIndex < metadata.totalChunks) { "chunkIndex must be less than totalChunks" }
        if (metadata.chunkIndex == 0) {
            require(metadata.totalSize > 0) { "first chunk totalSize must be positive" }
            require(metadata.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "first chunk sha256 must be a 64-character hex digest" }
        } else {
            require(metadata.totalSize == 0L) { "non-first chunk totalSize must be zero" }
            require(metadata.sha256.isEmpty()) { "non-first chunk sha256 must be empty" }
        }
        require(bytes.size <= RwppConstants.CHUNK_SIZE) { "chunk exceeds ${RwppConstants.CHUNK_SIZE} bytes" }
        require(bytes.isNotEmpty()) { "chunk must not be empty" }

        val out = GameOutputStream()
        out.writeString(metadata.name)
        out.writeInt(metadata.chunkIndex)
        out.writeInt(metadata.totalChunks)
        out.writeLong(metadata.totalSize)
        out.writeString(metadata.sha256)
        out.writeBytesAndLength(bytes)
        return out.createPacket(PacketType.DOWNLOAD_MOD_CHUNK)
    }
}
