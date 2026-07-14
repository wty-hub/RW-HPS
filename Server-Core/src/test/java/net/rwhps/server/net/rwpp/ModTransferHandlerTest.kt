package net.rwhps.server.net.rwpp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ModTransferHandlerTest {
    private fun entry(name: String, size: Long = 1) = ModCatalogEntry(name, Path.of(name), size, "0".repeat(64))

    @Test fun `empty request is valid but whitespace and empty comma elements are rejected`() {
        val snapshot = ModCatalogSnapshot(1, listOf(entry("A")))
        assertEquals(emptyList<ModCatalogEntry>(), ModTransferHandler.parseRequest("", snapshot))
        listOf(" ", "\t", ",A", "A,", "A,,A", "A, ,A").forEach { assertNull(ModTransferHandler.parseRequest(it, snapshot), it) }
    }

    @Test fun `request matching is case insensitive deduplicated and preserves first order`() {
        val a = entry("Alpha"); val b = entry("Beta"); val unicode = entry("模组É")
        val snapshot = ModCatalogSnapshot(1, listOf(a, b, unicode))
        assertEquals(listOf(b, a, unicode), ModTransferHandler.parseRequest(" beta, ALPHA,BETA,模组é ", snapshot))
    }

    @Test fun `unknown and ambiguous names are rejected`() {
        val snapshot = ModCatalogSnapshot(1, listOf(entry("Same"), entry("same"), entry("Known")))
        assertNull(ModTransferHandler.parseRequest("Unknown", snapshot))
        assertNull(ModTransferHandler.parseRequest("Same", snapshot))
        assertNull(ModTransferHandler.parseRequest("Known,Unknown", snapshot))
    }
}
