package net.rwhps.server.net.rwpp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RwppRoomOptionTest {
    @Test fun `TOML and preregister prefix exactly match RWJS protocol`() {
        val option = RwppRoomOption(canTransferMod = true, allModsSize = 65537, protocolVersion = 4)
        val toml = "canTransferMod = true\nallModsSize = 65537\nprotocolVersion = 4"
        assertEquals(toml, option.encodeToml())
        assertEquals("io.github.rwpp$toml", option.preregisterPrefix())
        assertEquals(4, RwppConstants.DEFAULT_PROTOCOL_VERSION)
        assertEquals(64 * 1024, RwppConstants.CHUNK_SIZE)
    }
}
