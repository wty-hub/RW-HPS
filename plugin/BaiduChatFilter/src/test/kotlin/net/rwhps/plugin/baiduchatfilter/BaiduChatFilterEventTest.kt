package net.rwhps.plugin.baiduchatfilter

import net.rwhps.server.game.event.game.PlayerJoinEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaiduChatFilterEventTest {

    private fun passResult(): BaiduCheckResult =
        BaiduCheckResult(BaiduCheckResult.TYPE_PASS, "合规", emptyList())

    private fun blockResult(type: Int = BaiduCheckResult.TYPE_BLOCK): BaiduCheckResult =
        BaiduCheckResult(type, "违规", listOf("词1"))

    private fun buildEvent(
        config: BaiduChatFilterConfig = BaiduChatFilterConfig(),
        censor: FakeTextCensor = FakeTextCensor(passResult()),
    ): BaiduChatFilterEvent {
        val main = BaiduChatFilterMain()
        main.state = BaiduChatFilterState(config, censor)
        return BaiduChatFilterEvent(main)
    }

    private fun fireJoin(event: BaiduChatFilterEvent, player: TestPlayer) {
        event.onPlayerJoin(PlayerJoinEvent(FakeGameModule(), player))
    }

    @Test
    fun `no state does not kick`() {
        val event = BaiduChatFilterEvent(BaiduChatFilterMain())
        val player = TestPlayer(name = "badname")

        fireJoin(event, player)

        assertTrue(player.kicks.isEmpty())
    }

    @Test
    fun `filterName disabled does not kick and does not call api`() {
        val censor = FakeTextCensor(blockResult())
        val event = buildEvent(BaiduChatFilterConfig(filterName = false), censor)
        val player = TestPlayer(name = "badname")

        fireJoin(event, player)

        assertTrue(player.kicks.isEmpty())
        assertTrue(censor.calls.isEmpty())
    }

    @Test
    fun `admin is skipped when skipAdmin enabled`() {
        val censor = FakeTextCensor(blockResult())
        val event = buildEvent(censor = censor)
        val player = TestPlayer(name = "badname", isAdmin = true)

        fireJoin(event, player)

        assertTrue(player.kicks.isEmpty())
        assertTrue(censor.calls.isEmpty())
    }

    @Test
    fun `admin is not skipped when skipAdmin disabled`() {
        val censor = FakeTextCensor(blockResult())
        val event = buildEvent(BaiduChatFilterConfig(skipAdmin = false), censor)
        val player = TestPlayer(name = "badname", isAdmin = true)

        fireJoin(event, player)

        assertEquals(1, player.kicks.size)
    }

    @Test
    fun `compliant nickname is not kicked`() {
        val event = buildEvent(censor = FakeTextCensor(passResult()))
        val player = TestPlayer(name = "goodname")

        fireJoin(event, player)

        assertTrue(player.kicks.isEmpty())
    }

    @Test
    fun `violating nickname is kicked with configured message and duration`() {
        val config = BaiduChatFilterConfig(
            nameKickMessage = "昵称违规，请修改后重进",
            nameKickDurationSeconds = 600,
        )
        val event = buildEvent(config, FakeTextCensor(blockResult()))
        val player = TestPlayer(name = "badname")

        fireJoin(event, player)

        assertEquals(listOf("昵称违规，请修改后重进" to 600), player.kicks)
    }

    @Test
    fun `default kick uses default message and duration`() {
        val event = buildEvent(censor = FakeTextCensor(blockResult()))
        val player = TestPlayer(name = "badname")

        fireJoin(event, player)

        assertEquals(listOf(BaiduChatFilterConfig().nameKickMessage to 0), player.kicks)
    }

    @Test
    fun `suspicious nickname is kicked by default config`() {
        val event = buildEvent(censor = FakeTextCensor(BaiduCheckResult(BaiduCheckResult.TYPE_SUSPICIOUS, "疑似", emptyList())))
        val player = TestPlayer(name = "susname")

        fireJoin(event, player)

        assertEquals(1, player.kicks.size)
    }

    @Test
    fun `conclusion type not in block list is not kicked`() {
        val event = buildEvent(censor = FakeTextCensor(BaiduCheckResult(BaiduCheckResult.TYPE_ERROR, "审核失败", emptyList())))
        val player = TestPlayer(name = "weird")

        fireJoin(event, player)

        assertTrue(player.kicks.isEmpty())
    }

    @Test
    fun `api failure with failOpen does not kick`() {
        val censor = FakeTextCensor(passResult()).apply { shouldFail = true }
        val event = buildEvent(BaiduChatFilterConfig(failOpen = true), censor)
        val player = TestPlayer(name = "any")

        fireJoin(event, player)

        assertTrue(player.kicks.isEmpty())
    }

    @Test
    fun `api failure with failOpen disabled kicks`() {
        val censor = FakeTextCensor(passResult()).apply { shouldFail = true }
        val event = buildEvent(BaiduChatFilterConfig(failOpen = false), censor)
        val player = TestPlayer(name = "any")

        fireJoin(event, player)

        assertEquals(1, player.kicks.size)
        assertEquals(BaiduChatFilterConfig().nameKickMessage, player.kicks[0].first)
    }

    @Test
    fun `repeated joins share the same cache`() {
        val censor = FakeTextCensor(blockResult())
        val event = buildEvent(censor = censor)

        val player1 = TestPlayer(name = "badname")
        fireJoin(event, player1)
        val player2 = TestPlayer(name = "badname")
        fireJoin(event, player2)

        // 相同昵称命中缓存, 只调用一次 API
        assertEquals(1, censor.calls.size)
        assertEquals(1, player1.kicks.size)
        assertEquals(1, player2.kicks.size)
    }
}
