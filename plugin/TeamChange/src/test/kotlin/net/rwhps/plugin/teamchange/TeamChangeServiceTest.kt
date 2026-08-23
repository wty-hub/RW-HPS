package net.rwhps.plugin.teamchange

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TeamChangeServiceTest {

    @Test
    fun `lobby change does not trigger sync`() {
        val player = TestPlayer(initialTeam = 0)
        var syncCalled = false

        TeamChangeService.apply(player, newTeam = 1, roomStarted = false) { syncCalled = true }

        assertEquals(1, player.team)
        assertFalse(syncCalled)
    }

    @Test
    fun `started change triggers sync`() {
        val player = TestPlayer(initialTeam = 1)
        var syncCalled = false

        TeamChangeService.apply(player, newTeam = 3, roomStarted = true) { syncCalled = true }

        assertEquals(3, player.team)
        assertTrue(syncCalled)
    }

    @Test
    fun `same team change in started game still triggers sync`() {
        val player = TestPlayer(initialTeam = 2)
        var syncCalled = false

        TeamChangeService.apply(player, newTeam = 2, roomStarted = true) { syncCalled = true }

        assertEquals(2, player.team)
        assertTrue(syncCalled)
    }
}
