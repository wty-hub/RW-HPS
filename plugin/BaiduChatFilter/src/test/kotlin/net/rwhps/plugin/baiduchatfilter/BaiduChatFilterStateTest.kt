package net.rwhps.plugin.baiduchatfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaiduChatFilterStateTest {

    private fun blockResult(type: Int = BaiduCheckResult.TYPE_BLOCK): BaiduCheckResult =
        BaiduCheckResult(type, "违规", listOf("词1"))

    private fun passResult(): BaiduCheckResult =
        BaiduCheckResult(BaiduCheckResult.TYPE_PASS, "合规", emptyList())

    private fun stateOf(
        config: BaiduChatFilterConfig = BaiduChatFilterConfig(),
        censor: FakeTextCensor = FakeTextCensor(passResult()),
    ) = BaiduChatFilterState(config, censor)

    // ---------------- filter ----------------

    @Test
    fun `disabled filter passes through without api call`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(BaiduChatFilterConfig(enabled = false), censor)
        val player = TestPlayer()

        assertEquals("hello", state.filter(player, "hello"))
        assertNull(state.filter(player, null))
        assertTrueTextEmpty(censor.calls)
    }

    @Test
    fun `blank and null messages pass through`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(censor = censor)
        val player = TestPlayer()

        assertEquals("", state.filter(player, ""))
        assertNull(state.filter(player, null))
        assertTrueTextEmpty(censor.calls)
    }

    @Test
    fun `admin is skipped when skipAdmin enabled`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(censor = censor)
        val player = TestPlayer(isAdmin = true)

        assertEquals("bad", state.filter(player, "bad"))
        assertTrueTextEmpty(censor.calls)
    }

    @Test
    fun `admin is not skipped when skipAdmin disabled`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(BaiduChatFilterConfig(skipAdmin = false), censor)
        val player = TestPlayer(isAdmin = true)

        assertNull(state.filter(player, "bad"))
        assertEquals(1, censor.calls.size)
        assertEquals(listOf(BaiduChatFilterConfig().blockMessage), player.messages)
    }

    @Test
    fun `message longer than 20000 bytes passes through`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(censor = censor)
        val player = TestPlayer()
        val longMessage = "a".repeat(20001)

        assertEquals(longMessage, state.filter(player, longMessage))
        assertTrueTextEmpty(censor.calls)
    }

    @Test
    fun `api block result blocks message and notifies player`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(censor = censor)
        val player = TestPlayer()

        assertNull(state.filter(player, "bad words"))
        assertEquals(listOf(BaiduChatFilterConfig().blockMessage), player.messages)
        assertEquals(listOf("bad words"), censor.calls)
    }

    @Test
    fun `api pass result allows message`() {
        val censor = FakeTextCensor(passResult())
        val state = stateOf(censor = censor)
        val player = TestPlayer()

        assertEquals("good words", state.filter(player, "good words"))
        assertTrueTextEmpty(player.messages)
    }

    @Test
    fun `suspicious result is blocked by default config`() {
        val censor = FakeTextCensor(BaiduCheckResult(BaiduCheckResult.TYPE_SUSPICIOUS, "疑似", emptyList()))
        val state = stateOf(censor = censor)
        val player = TestPlayer()

        assertNull(state.filter(player, "sus"))
    }

    @Test
    fun `conclusion type 4 is not blocked by default config`() {
        val censor = FakeTextCensor(BaiduCheckResult(BaiduCheckResult.TYPE_ERROR, "审核失败", emptyList()))
        val state = stateOf(censor = censor)
        val player = TestPlayer()

        assertEquals("x", state.filter(player, "x"))
    }

    @Test
    fun `custom blockMessage is used`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(BaiduChatFilterConfig(blockMessage = "自定义提示"), censor)
        val player = TestPlayer()

        assertNull(state.filter(player, "bad"))
        assertEquals(listOf("自定义提示"), player.messages)
    }

    @Test
    fun `blocked result is cached and api not called again`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(censor = censor)
        val player = TestPlayer()

        assertNull(state.filter(player, "bad"))
        assertNull(state.filter(player, "bad"))
        assertEquals(1, censor.calls.size)
    }

    @Test
    fun `pass result is cached and api not called again`() {
        val censor = FakeTextCensor(passResult())
        val state = stateOf(censor = censor)
        val player = TestPlayer()

        assertEquals("ok", state.filter(player, "ok"))
        assertEquals("ok", state.filter(player, "ok"))
        assertEquals(1, censor.calls.size)
    }

    @Test
    fun `cache expires when ttl elapses`() {
        val censor = FakeTextCensor(passResult())
        val state = stateOf(BaiduChatFilterConfig(cacheMinutes = 0), censor)
        val player = TestPlayer()

        assertEquals("ok", state.filter(player, "ok"))
        Thread.sleep(5)
        assertEquals("ok", state.filter(player, "ok"))
        assertEquals(2, censor.calls.size)
    }

    @Test
    fun `api failure with failOpen allows message`() {
        val censor = FakeTextCensor(passResult()).apply { shouldFail = true }
        val state = stateOf(BaiduChatFilterConfig(failOpen = true), censor)
        val player = TestPlayer()

        assertEquals("msg", state.filter(player, "msg"))
        assertTrueTextEmpty(player.messages)
    }

    @Test
    fun `api failure with failOpen disabled blocks message`() {
        val censor = FakeTextCensor(passResult()).apply { shouldFail = true }
        val state = stateOf(BaiduChatFilterConfig(failOpen = false), censor)
        val player = TestPlayer()

        assertNull(state.filter(player, "msg"))
        assertEquals(listOf(BaiduChatFilterConfig().blockMessage), player.messages)
    }

    // ---------------- checkName ----------------

    @Test
    fun `checkName returns api conclusion`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(censor = censor)

        assertEquals(BaiduCheckResult.TYPE_BLOCK, state.checkName("badname"))
        assertEquals(listOf("badname"), censor.calls)
    }

    @Test
    fun `checkName returns pass conclusion`() {
        val censor = FakeTextCensor(passResult())
        val state = stateOf(censor = censor)

        assertEquals(BaiduCheckResult.TYPE_PASS, state.checkName("goodname"))
    }

    @Test
    fun `checkName reuses cache for identical names`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(censor = censor)

        assertEquals(BaiduCheckResult.TYPE_BLOCK, state.checkName("badname"))
        assertEquals(BaiduCheckResult.TYPE_BLOCK, state.checkName("badname"))
        assertEquals(1, censor.calls.size)
    }

    @Test
    fun `checkName returns null on api failure`() {
        val censor = FakeTextCensor(passResult()).apply { shouldFail = true }
        val state = stateOf(censor = censor)

        assertNull(state.checkName("name"))
    }

    @Test
    fun `checkName refetches after cache expiry`() {
        val censor = FakeTextCensor(passResult())
        val state = stateOf(BaiduChatFilterConfig(cacheMinutes = 0), censor)

        state.checkName("name")
        Thread.sleep(5)
        state.checkName("name")
        assertEquals(2, censor.calls.size)
    }

    // ---------------- close / reload ----------------

    @Test
    fun `close releases client and cache`() {
        val censor = FakeTextCensor(blockResult())
        val state = stateOf(censor = censor)
        state.checkName("badname")

        state.close()

        assertTrue(censor.closed)
        // 缓存已清空 -> 再次审核会重新调用 API
        state.checkName("badname")
        assertEquals(2, censor.calls.size)
    }

    private fun assertTrueTextEmpty(list: List<String>) {
        assertEquals(emptyList<String>(), list)
    }
}
