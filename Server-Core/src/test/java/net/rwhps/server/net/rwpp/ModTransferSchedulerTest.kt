package net.rwhps.server.net.rwpp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.rwhps.server.io.GameInputStream
import net.rwhps.server.io.packet.Packet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path

class ModTransferSchedulerTest {
    private val hash = "0".repeat(64)
    private fun entry(name: String, size: Int) = ModCatalogEntry(name, Path.of(name), size.toLong(), hash)
    private data class Chunk(val name: String, val index: Int, val total: Int, val size: Long, val hash: String, val bytes: ByteArray)

    @Test fun `exact 64 KiB and plus one have correct chunk lengths and metadata`() = runBlocking {
        val packets = mutableListOf<Packet>(); val scheduler = scheduler(packets = packets, window = 3)
        try {
            val exact = entry("exact", RwppConstants.CHUNK_SIZE); val plus = entry("plus", RwppConstants.CHUNK_SIZE + 1)
            scheduler.replace("c", "p", ModCatalogSnapshot(1, listOf(exact, plus)), listOf(exact, plus))
            scheduler.tick(); assertTrue(scheduler.acknowledge("c", "exact", 0)); scheduler.tick(); scheduler.tick()
            val chunks = packets.map(::chunk)
            assertEquals(listOf(RwppConstants.CHUNK_SIZE, RwppConstants.CHUNK_SIZE, 1), chunks.map { it.bytes.size })
            assertEquals(listOf("exact", "plus", "plus"), chunks.map { it.name })
            assertEquals(listOf(1, 2, 2), chunks.map { it.total })
            assertEquals(exact.size, chunks[0].size); assertEquals(hash, chunks[0].hash)
            assertEquals(plus.size, chunks[1].size); assertEquals(hash, chunks[1].hash)
            assertEquals(0, chunks[2].size); assertEquals("", chunks[2].hash)
        } finally { scheduler.close() }
    }

    @Test fun `window sends multiple chunks caps without ACK and accepts out of order ACK`() = runBlocking {
        val packets = mutableListOf<Packet>(); val scheduler = scheduler(packets = packets, window = 3)
        try {
            val mod = entry("mod", RwppConstants.CHUNK_SIZE * 3); scheduler.replace("c", "p", ModCatalogSnapshot(2, listOf(mod)), listOf(mod))
            repeat(5) { scheduler.tick() }; assertEquals(listOf(0, 1, 2), packets.map { chunk(it).index })
            assertTrue(scheduler.acknowledge("c", "mod", 2)); assertTrue(scheduler.acknowledge("c", "mod", 0))
            assertEquals(ModTransferReadyState.TRANSFERRING, scheduler.state("c")); assertTrue(scheduler.acknowledge("c", "mod", 1))
            assertEquals(ModTransferReadyState.WAITING_RELOAD, scheduler.state("c"))
        } finally { scheduler.close() }
    }

    @Test fun `ACK validation rejects connection name index and duplicate`() = runBlocking {
        val scheduler = scheduler(window = 2)
        try {
            val mod = entry("mod", 1); scheduler.replace("c", "p", ModCatalogSnapshot(3, listOf(mod)), listOf(mod)); scheduler.tick()
            assertFalse(scheduler.acknowledge("other", "mod", 0)); assertFalse(scheduler.acknowledge("c", "wrong", 0))
            assertFalse(scheduler.acknowledge("c", "mod", -1)); assertFalse(scheduler.acknowledge("c", "mod", 1))
            assertTrue(scheduler.acknowledge("c", "mod", 0)); assertFalse(scheduler.acknowledge("c", "mod", 0))
        } finally { scheduler.close() }
    }

    @Test fun `replacement closes old stream and stale ACK is invalid`() = runBlocking {
        var closes = 0; val source = ModSource { object : ByteArrayInputStream(ByteArray(it.size.toInt())) { override fun close() { closes++; super.close() } } }
        val scheduler = scheduler(source = source)
        try {
            val old = entry("old", RwppConstants.CHUNK_SIZE * 2); val fresh = entry("new", 1)
            scheduler.replace("c", "p", ModCatalogSnapshot(4, listOf(old)), listOf(old)); scheduler.tick()
            scheduler.replace("c", "p", ModCatalogSnapshot(5, listOf(fresh)), listOf(fresh))
            assertEquals(1, closes); assertFalse(scheduler.acknowledge("c", "old", 0)); scheduler.tick(); assertTrue(scheduler.acknowledge("c", "new", 0))
        } finally { scheduler.close() }
    }

    @Test fun `max concurrent rejects excess and cancellation frees capacity`() = runBlocking {
        val scheduler = scheduler(maxConcurrent = 1)
        try {
            val mod = entry("m", 1); assertTrue(scheduler.replace("1", "z", ModCatalogSnapshot(6, listOf(mod)), listOf(mod)))
            assertFalse(scheduler.replace("2", "a", ModCatalogSnapshot(7, listOf(mod)), listOf(mod)))
            assertEquals(ModTransferReadyState.FAILED, scheduler.state("2")); scheduler.cancel("1")
            assertTrue(scheduler.replace("2", "a", ModCatalogSnapshot(8, listOf(mod)), listOf(mod)))
        } finally { scheduler.close() }
    }

    @Test fun `send and read failures fail notify and close streams`() = runBlocking {
        suspend fun verify(sender: ModChunkSender, source: ModSource, reasonPart: String) {
            val failures = mutableListOf<Pair<String, String>>(); val scheduler = scheduler(sender = sender, source = source, failures = failures)
            try {
                val mod = entry("m", 1); scheduler.replace("c", "p", ModCatalogSnapshot(9, listOf(mod)), listOf(mod)); scheduler.tick()
                assertEquals(ModTransferReadyState.FAILED, scheduler.state("c")); assertEquals("c", failures.single().first)
                assertTrue(failures.single().second.contains(reasonPart))
            } finally { scheduler.close() }
        }
        var sendClosed = false
        verify(ModChunkSender { _, _ -> throw IOException("send") }, ModSource { object : ByteArrayInputStream(byteArrayOf(1)) { override fun close() { sendClosed = true; super.close() } } }, "send failed")
        assertTrue(sendClosed)
        var readClosed = false
        verify(ModChunkSender { _, _ -> }, ModSource { object : InputStream() { override fun read() = throw IOException("read"); override fun read(b: ByteArray, off: Int, len: Int) = throw IOException("read"); override fun close() { readClosed = true } } }, "read failed")
        assertTrue(readClosed)
    }

    @Test fun `ACK and session timeout boundaries are exact`() = runBlocking {
        var time = 0L; val failures = mutableListOf<Pair<String, String>>(); val scheduler = scheduler(clock = { time }, ackTimeout = 10, sessionTimeout = 100, failures = failures)
        try {
            val mod = entry("m", 1); scheduler.replace("ack", "b", ModCatalogSnapshot(10, listOf(mod)), listOf(mod)); scheduler.tick()
            time = 9; scheduler.tick(); assertEquals(ModTransferReadyState.TRANSFERRING, scheduler.state("ack"))
            time = 10; scheduler.tick(); assertEquals(ModTransferReadyState.FAILED, scheduler.state("ack")); assertTrue(failures.last().second.contains("ACK"))
            scheduler.markWaitingRequest("session", "a"); time = 109; scheduler.tick(); assertEquals(ModTransferReadyState.WAITING_REQUEST, scheduler.state("session"))
            time = 110; scheduler.tick(); assertEquals(ModTransferReadyState.FAILED, scheduler.state("session"))
        } finally { scheduler.close() }
    }

    @Test fun `finish reload gate names sorting and ready failed behavior`() = runBlocking {
        val scheduler = scheduler()
        try {
            scheduler.markWaitingRequest("z", "Zulu"); scheduler.markWaitingRequest("a", "Alpha")
            assertEquals(listOf("Alpha", "Zulu"), scheduler.pendingPlayerNames()); assertFalse(scheduler.canStart()); assertFalse(scheduler.finishReload("z"))
            scheduler.replace("a", "Alpha", ModCatalogSnapshot(11, emptyList()), emptyList()); assertTrue(scheduler.finishReload("a"))
            assertEquals(listOf("Zulu"), scheduler.pendingPlayerNames()); assertFalse(scheduler.canStart())
            scheduler.cancel("z"); assertTrue(scheduler.canStart()); assertEquals(ModTransferReadyState.READY, scheduler.state("a"))
        } finally { scheduler.close(); scheduler.close() }
    }

    @Test fun `multi mod transfer keeps requested order and first metadata per mod`() = runBlocking {
        val packets = mutableListOf<Packet>(); val scheduler = scheduler(packets = packets)
        try {
            val b = entry("B", 1); val a = entry("A", 1); scheduler.replace("c", "p", ModCatalogSnapshot(12, listOf(a, b)), listOf(b, a))
            scheduler.tick(); assertTrue(scheduler.acknowledge("c", "B", 0)); scheduler.tick()
            assertEquals(listOf("B", "A"), packets.map { chunk(it).name }); assertEquals(listOf(b.size, a.size), packets.map { chunk(it).size })
        } finally { scheduler.close() }
    }

    private fun scheduler(
        packets: MutableList<Packet> = mutableListOf(), window: Int = 1, maxConcurrent: Int = 4,
        ackTimeout: Long = Long.MAX_VALUE, sessionTimeout: Long = Long.MAX_VALUE,
        source: ModSource = ModSource { ByteArrayInputStream(ByteArray(it.size.toInt())) },
        sender: ModChunkSender = ModChunkSender { _, packet -> packets += packet }, clock: () -> Long = { 0 },
        failures: MutableList<Pair<String, String>> = mutableListOf(),
    ) = ModTransferScheduler(sender, ModTransferConfig(window, ackTimeout, sessionTimeout, maxConcurrent), source,
        Dispatchers.Unconfined, clock, { }, { id, reason -> failures += id to reason }, startLoop = false)

    private fun chunk(packet: Packet) = GameInputStream(packet).use { input ->
        val name = input.readString(); val index = input.readInt(); val total = input.readInt(); val size = input.readLong(); val digest = input.readString()
        val length = input.readInt(); Chunk(name, index, total, size, digest, input.readNBytes(length))
    }
}
