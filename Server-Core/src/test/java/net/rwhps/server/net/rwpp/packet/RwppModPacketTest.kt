package net.rwhps.server.net.rwpp.packet

import net.rwhps.server.io.GameInputStream
import net.rwhps.server.io.GameOutputStream
import net.rwhps.server.io.packet.Packet
import net.rwhps.server.io.packet.type.PacketType
import net.rwhps.server.net.rwpp.RwppConstants
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RwppModPacketTest {
    private val hash = "ab".repeat(32)

    @Test fun `decodes download request and ACK including modified UTF names`() {
        val name = "模组-é-\u0000"
        val request = strings(PacketType.MOD_DOWNLOAD_REQUEST) { writeString(name) }
        val ack = strings(PacketType.MOD_CHUNK_ACK) { writeString(name); writeInt(17) }
        assertEquals(RwppModPacket.DownloadRequest(name), RwppModPacket.readDownloadRequest(request))
        assertEquals(RwppModPacket.ChunkAck(name, 17), RwppModPacket.readChunkAck(ack))
    }

    @Test fun `encodes first middle and last chunks in exact TXJS v4 layout`() {
        val chunks = listOf(
            RwppModPacket.ChunkMetadata("m", 0, 3, 131_073, hash) to ByteArray(RwppConstants.CHUNK_SIZE) { 1 },
            RwppModPacket.ChunkMetadata("m", 1, 3, 0, "") to ByteArray(RwppConstants.CHUNK_SIZE) { 2 },
            RwppModPacket.ChunkMetadata("m", 2, 3, 0, "") to byteArrayOf(3),
        )
        chunks.forEach { (metadata, bytes) ->
            val packet = RwppModPacket.writeDownloadModChunk(metadata, bytes)
            assertEquals(PacketType.DOWNLOAD_MOD_CHUNK, packet.type)
            GameInputStream(packet).use { input ->
                assertEquals(metadata.name, input.readString())
                assertEquals(metadata.chunkIndex, input.readInt())
                assertEquals(metadata.totalChunks, input.readInt())
                assertEquals(metadata.totalSize, input.readLong())
                assertEquals(metadata.sha256, input.readString())
                assertEquals(bytes.size, input.readInt())
                assertArrayEquals(bytes, input.readNBytes(bytes.size))
                assertEquals(0, input.getSize())
            }
        }
    }

    @Test fun `rejects invalid chunk metadata and payload sizes`() {
        fun invalid(metadata: RwppModPacket.ChunkMetadata, bytes: ByteArray = byteArrayOf(1)) =
            assertThrows(IllegalArgumentException::class.java) { RwppModPacket.writeDownloadModChunk(metadata, bytes) }
        invalid(RwppModPacket.ChunkMetadata(" ", 0, 1, 1, hash))
        invalid(RwppModPacket.ChunkMetadata("m", -1, 1, 1, hash))
        invalid(RwppModPacket.ChunkMetadata("m", 0, 0, 1, hash))
        invalid(RwppModPacket.ChunkMetadata("m", 1, 1, 0, ""))
        invalid(RwppModPacket.ChunkMetadata("m", 0, 1, 0, hash))
        invalid(RwppModPacket.ChunkMetadata("m", 0, 1, 1, "not-a-hash"))
        invalid(RwppModPacket.ChunkMetadata("m", 1, 2, 1, ""))
        invalid(RwppModPacket.ChunkMetadata("m", 1, 2, 0, hash))
        invalid(RwppModPacket.ChunkMetadata("m", 0, 1, 1, hash), ByteArray(RwppConstants.CHUNK_SIZE + 1))
        invalid(RwppModPacket.ChunkMetadata("m", 0, 1, 1, hash), byteArrayOf())
    }

    @Test fun `reload finish requires exactly marker one`() {
        RwppModPacket.readReloadFinish(ints(1))
        assertThrows(Exception::class.java) { RwppModPacket.readReloadFinish(ints()) }
        assertThrows(IllegalArgumentException::class.java) { RwppModPacket.readReloadFinish(ints(0)) }
        assertThrows(IllegalArgumentException::class.java) { RwppModPacket.readReloadFinish(ints(2)) }
        assertThrows(IllegalArgumentException::class.java) { RwppModPacket.readReloadFinish(ints(1, 2)) }
    }

    private fun strings(type: PacketType, body: GameOutputStream.() -> Unit): Packet = GameOutputStream().apply(body).createPacket(type)
    private fun ints(vararg values: Int): Packet = strings(PacketType.MOD_RELOAD_FINISH) { values.forEach(::writeInt) }
}
