package net.rwhps.plugin.allyrequest

import net.rwhps.server.game.player.PlayerHess
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AllyRequestServiceTest {

    @AfterEach
    fun tearDown() {
        AllyRequestService.clear()
    }

    private fun player(team: Int = 0, index: Int = 0, name: String = "P-$index"): TestPlayer =
        TestPlayer(initialTeam = team, index = index, name = name)

    // ---------- createRequest ----------

    @Test
    fun `create request when target has no pending`() {
        val request = AllyRequestService.createRequest(initiatorIndex = 0, targetIndex = 1)

        assertNotNull(request)
        assertEquals(0, request!!.initiatorIndex)
        assertEquals(1, request.targetIndex)
        assertTrue(request.expireAt > 0)
    }

    @Test
    fun `second request to same target is rejected`() {
        assertNotNull(AllyRequestService.createRequest(0, 1))

        val second = AllyRequestService.createRequest(2, 1)

        assertNull(second)
    }

    @Test
    fun `requests to different targets coexist`() {
        assertNotNull(AllyRequestService.createRequest(0, 1))
        assertNotNull(AllyRequestService.createRequest(0, 2))
        assertNotNull(AllyRequestService.createRequest(3, 4))

        assertEquals(3, AllyRequestService.pendingTargets().size)
    }

    // ---------- findByTarget / expiry ----------

    @Test
    fun `find request of target`() {
        AllyRequestService.createRequest(0, 1)

        val found = AllyRequestService.findByTarget(1)

        assertNotNull(found)
        assertEquals(0, found!!.initiatorIndex)
    }

    @Test
    fun `expired request is lazily removed`() {
        AllyRequestService.createRequest(0, 1)
        // 直接构造过期请求
        val expired = AllyRequest(initiatorIndex = 0, targetIndex = 2, expireAt = 0)
        AllyRequestService.pending[2] = expired

        assertNull(AllyRequestService.findByTarget(2))
        assertFalse(AllyRequestService.pendingTargets().contains(2))
    }

    @Test
    fun `nonexpired request is still found`() {
        AllyRequestService.createRequest(0, 1)

        assertNotNull(AllyRequestService.findByTarget(1))
        assertTrue(AllyRequestService.pendingTargets().contains(1))
    }

    @Test
    fun `find returns null when no request`() {
        assertNull(AllyRequestService.findByTarget(99))
    }

    // ---------- removeRequest ----------

    @Test
    fun `remove request returns it and clears`() {
        AllyRequestService.createRequest(0, 1)

        val removed = AllyRequestService.removeRequest(1)

        assertNotNull(removed)
        assertFalse(AllyRequestService.pendingTargets().contains(1))
    }

    // ---------- applyTeamChange ----------

    @Test
    fun `lobby change does not trigger sync`() {
        val target = player(team = 2)
        var syncCalled = false

        AllyRequestService.applyTeamChange(target, newTeam = 0, roomStarted = false) { syncCalled = true }

        assertEquals(0, target.team)
        assertFalse(syncCalled)
    }

    @Test
    fun `started change triggers sync`() {
        val target = player(team = 2)
        var syncCalled = false

        AllyRequestService.applyTeamChange(target, newTeam = 1, roomStarted = true) { syncCalled = true }

        assertEquals(1, target.team)
        assertTrue(syncCalled)
    }

    @Test
    fun `same team change in started game still triggers sync`() {
        val target = player(team = 1)
        var syncCalled = false

        AllyRequestService.applyTeamChange(target, newTeam = 1, roomStarted = true) { syncCalled = true }

        assertEquals(1, target.team)
        assertTrue(syncCalled)
    }
}
