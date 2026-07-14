package net.rwhps.plugin.roomreset

import net.rwhps.server.game.headless.core.link.AbstractLinkGameServerData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RoomResetDefaultsTest {
    @Test
    fun `room defaults match original game defaults`() {
        assertEquals("Crossing Large (10p)", AbstractLinkGameServerData.DEFAULT_MAP_NAME)
        assertEquals("[z;p10]", AbstractLinkGameServerData.DEFAULT_MAP_PLAYER)
        assertEquals(0, AbstractLinkGameServerData.DEFAULT_CREDITS)
        assertEquals(2, AbstractLinkGameServerData.DEFAULT_FOG)
        assertFalse(AbstractLinkGameServerData.DEFAULT_NUKES)
        assertFalse(AbstractLinkGameServerData.DEFAULT_SHARED_CONTROL)
        assertEquals(1, AbstractLinkGameServerData.DEFAULT_AI_DIFFICULTY)
        assertEquals(1.0f, AbstractLinkGameServerData.DEFAULT_INCOME)
        assertEquals(1, AbstractLinkGameServerData.DEFAULT_STARTING_UNITS)
    }

    @Test
    fun `missing password is rejected before server access`() {
        assertEquals(RoomResetMain.USAGE, RoomResetMain().validateReset(emptyArray()))
        assertEquals(RoomResetMain.USAGE, RoomResetMain().validateReset(arrayOf("one", "two")))
    }
}
