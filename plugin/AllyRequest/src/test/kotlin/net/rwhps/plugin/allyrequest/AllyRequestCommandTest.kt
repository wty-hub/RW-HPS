package net.rwhps.plugin.allyrequest

import net.rwhps.server.data.bean.BeanServerConfig
import net.rwhps.server.data.global.Data
import net.rwhps.server.game.headless.core.AbstractGameModule
import net.rwhps.server.util.game.command.CommandHandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 通过真实 [CommandHandler] 验证 jm/y/n 的注册、校验与对服务层的接线。
 *
 * 使用 [FakeGameModule] 注入, 并覆写 [AllyRequestMain.scheduleTimeout]/[cancelTimeout]
 * 为记录模式, 避免测试中产生真实定时器。
 */
class AllyRequestCommandTest {
    private lateinit var module: FakeGameModule
    private lateinit var client: CommandHandler
    private lateinit var main: TestAllyRequestMain

    private class TestAllyRequestMain : AllyRequestMain() {
        val scheduled = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val timeoutRunnables = mutableMapOf<Int, Runnable>()
        lateinit var module: FakeGameModule

        override fun resolveModule(): AbstractGameModule? = module

        override fun scheduleTimeout(targetIndex: Int, onTimeout: Runnable) {
            scheduled += targetIndex
            timeoutRunnables[targetIndex] = onTimeout
        }

        override fun cancelTimeout(targetIndex: Int) {
            cancelled += targetIndex
            timeoutRunnables.remove(targetIndex)
        }

        /** 现有用例期望立即 SYNC; 防抖在 [AllyRequestSyncDebounceTest] 单独覆盖。 */
        override fun scheduleSyncDebounce(onFlush: Runnable) {
            onFlush.run()
        }

        override fun cancelSyncDebounce() = Unit
    }

    @BeforeEach
    fun setUp() {
        Data.configServer = BeanServerConfig()
        module = FakeGameModule()
        main = TestAllyRequestMain()
        main.module = module
        client = CommandHandler("/")
        main.registerServerClientCommands(client)
    }

    @AfterEach
    fun tearDown() {
        AllyRequestService.clear()
    }

    private fun addPlayer(team: Int = 0, index: Int = 0, name: String = "P-$index", alive: Boolean = true): TestPlayer {
        val p = TestPlayer(initialTeam = team, index = index, name = name, alive = alive)
        module.room.playerManage.playerAll.add(p)
        return p
    }

    private fun startGame() {
        module.room.isStartGame = true
    }

    // ---------- jm ----------

    @Test
    fun `jm requires game started`() {
        val p0 = addPlayer(index = 0)
        addPlayer(team = 1, index = 1)

        client.handleMessage("/jm 2", p0)

        assertTrue(p0.messages.any { it.contains("只能在游戏内") })
        assertTrue(AllyRequestService.pendingTargets().isEmpty())
        assertTrue(main.scheduled.isEmpty())
    }

    @Test
    fun `jm rejects non numeric seat`() {
        startGame()
        val p0 = addPlayer(index = 0)

        client.handleMessage("/jm abc", p0)

        assertTrue(p0.messages.any { it.contains("用法") })
    }

    @Test
    fun `jm rejects seat beyond max player`() {
        startGame()
        val p0 = addPlayer(index = 0)

        client.handleMessage("/jm 11", p0)

        assertTrue(p0.messages.any { it.contains("超过最大玩家数") })
        assertTrue(AllyRequestService.pendingTargets().isEmpty())
    }

    @Test
    fun `jm rejects empty seat`() {
        startGame()
        val p0 = addPlayer(index = 0)

        client.handleMessage("/jm 3", p0)

        assertTrue(p0.messages.any { it.contains("没有玩家") })
    }

    @Test
    fun `jm rejects inviting self`() {
        startGame()
        val p0 = addPlayer(index = 0)
        addPlayer(team = 1, index = 1)

        client.handleMessage("/jm 1", p0)

        assertTrue(p0.messages.any { it.contains("不能向自己") })
    }

    @Test
    fun `jm rejects same team`() {
        startGame()
        val p0 = addPlayer(index = 0)
        addPlayer(team = 0, index = 1)

        client.handleMessage("/jm 2", p0)

        assertTrue(p0.messages.any { it.contains("已经在同一队伍") })
        assertTrue(AllyRequestService.pendingTargets().isEmpty())
    }

    @Test
    fun `jm rejects when initiator is dead`() {
        startGame()
        val p0 = addPlayer(index = 0, alive = false)
        addPlayer(team = 1, index = 1)

        client.handleMessage("/jm 2", p0)

        assertTrue(p0.messages.any { it.contains("已经阵亡") })
        assertTrue(AllyRequestService.pendingTargets().isEmpty())
        assertTrue(main.scheduled.isEmpty())
    }

    @Test
    fun `jm rejects when target is dead`() {
        startGame()
        val p0 = addPlayer(index = 0)
        addPlayer(team = 1, index = 1, alive = false)

        client.handleMessage("/jm 2", p0)

        assertTrue(p0.messages.any { it.contains("该玩家已经阵亡") })
        assertTrue(AllyRequestService.pendingTargets().isEmpty())
        assertTrue(main.scheduled.isEmpty())
    }

    @Test
    fun `jm creates request and notifies both`() {
        startGame()
        val p0 = addPlayer(index = 0, name = "P0")
        val p1 = addPlayer(team = 1, index = 1, name = "P1")

        client.handleMessage("/jm 2", p0)

        val request = AllyRequestService.findByTarget(1)
        assertNotNull(request)
        assertEquals(0, request!!.initiatorIndex)
        assertEquals(1, request.targetIndex)
        assertTrue(p0.messages.any { it.contains("发起结盟请求") && it.contains("P1") })
        assertTrue(p1.messages.any { it.contains("结盟请求") && it.contains("P0") })
        assertEquals(listOf(1), main.scheduled)
    }

    @Test
    fun `jm rejected while target already has pending request`() {
        startGame()
        addPlayer(index = 0)
        addPlayer(team = 1, index = 1)
        val p2 = addPlayer(team = 2, index = 2, name = "P2")
        AllyRequestService.createRequest(0, 1)

        client.handleMessage("/jm 2", p2)

        assertTrue(p2.messages.any { it.contains("已有待处理的结盟请求") })
        // 原请求仍有效
        val request = AllyRequestService.findByTarget(1)
        assertNotNull(request)
        assertEquals(0, request!!.initiatorIndex)
        assertTrue(main.scheduled.isEmpty())
    }

    // ---------- y / n ----------

    @Test
    fun `y with no pending request`() {
        startGame()
        val p0 = addPlayer(index = 0)

        client.handleMessage("/y", p0)

        assertTrue(p0.messages.any { it.contains("没有人向你发起结盟请求") })
    }

    @Test
    fun `n with no pending request`() {
        startGame()
        val p0 = addPlayer(index = 0)

        client.handleMessage("/n", p0)

        assertTrue(p0.messages.any { it.contains("没有人向你发起结盟请求") })
    }

    @Test
    fun `y agrees and moves target to initiator team after start`() {
        startGame()
        val p0 = addPlayer(index = 0, name = "P0")
        val p1 = addPlayer(team = 1, index = 1, name = "P1")
        AllyRequestService.createRequest(0, 1)

        client.handleMessage("/y", p1)

        assertEquals(0, p1.team)
        assertEquals(1, module.syncCalls)
        // 写 team 一次闸门 + 防抖立即 flush 时再进一次闸门
        assertEquals(2, module.suspendRuns)
        assertTrue(AllyRequestService.pendingTargets().isEmpty())
        assertEquals(listOf(1), main.cancelled)
        assertTrue(p1.messages.any { it.contains("同意") })
        assertTrue(p0.messages.any { it.contains("同意") })
    }

    @Test
    fun `y agrees in lobby does not sync`() {
        val p0 = addPlayer(index = 0)
        val p1 = addPlayer(team = 1, index = 1)
        AllyRequestService.createRequest(0, 1)

        client.handleMessage("/y", p1)

        assertEquals(0, p1.team)
        assertEquals(0, module.syncCalls)
        assertTrue(AllyRequestService.pendingTargets().isEmpty())
    }

    @Test
    fun `n rejects and notifies both`() {
        startGame()
        val p0 = addPlayer(index = 0, name = "P0")
        val p1 = addPlayer(team = 1, index = 1, name = "P1")
        AllyRequestService.createRequest(0, 1)

        client.handleMessage("/n", p1)

        assertTrue(AllyRequestService.pendingTargets().isEmpty())
        assertEquals(listOf(1), main.cancelled)
        assertTrue(p1.messages.any { it.contains("拒绝") })
        assertTrue(p0.messages.any { it.contains("P1") && it.contains("拒绝") })
    }

    @Test
    fun `y when initiator already left`() {
        startGame()
        val p1 = addPlayer(team = 1, index = 1)
        AllyRequestService.createRequest(0, 1)

        client.handleMessage("/y", p1)

        assertTrue(p1.messages.any { it.contains("不在房间") })
        assertTrue(AllyRequestService.pendingTargets().isEmpty())
        assertEquals(listOf(1), main.cancelled)
        assertEquals(0, module.syncCalls)
    }

    // ---------- timeout ----------

    @Test
    fun `timeout default refuses and notifies both`() {
        startGame()
        val p0 = addPlayer(index = 0, name = "P0")
        val p1 = addPlayer(team = 1, index = 1, name = "P1")

        client.handleMessage("/jm 2", p0)
        assertEquals(listOf(1), main.scheduled)

        main.timeoutRunnables.getValue(1).run()

        assertTrue(AllyRequestService.pendingTargets().isEmpty())
        assertTrue(p1.messages.any { it.contains("默认拒绝") })
        assertTrue(p0.messages.any { it.contains("超时") })
        assertEquals(0, module.syncCalls)
    }

    @Test
    fun `timeout after request already handled does nothing`() {
        startGame()
        val p0 = addPlayer(index = 0, name = "P0")
        val p1 = addPlayer(team = 1, index = 1)

        client.handleMessage("/jm 2", p0)
        // 先捕获已排队的倒计时回调 ( 真实环境中回调可能已在执行中, 无法被取消 )
        val staleRunnable = main.timeoutRunnables.getValue(1)
        client.handleMessage("/n", p1)
        assertTrue(AllyRequestService.pendingTargets().isEmpty())

        // 残留的倒计时回调再次触发不应报错或产生副作用
        staleRunnable.run()

        assertTrue(AllyRequestService.pendingTargets().isEmpty())
    }
}

/**
 * 开局后连续同意结盟应合并为一次 allPlayerSync。
 */
class AllyRequestSyncDebounceTest {
    private lateinit var module: FakeGameModule
    private lateinit var client: CommandHandler
    private lateinit var main: DebounceAllyRequestMain

    private class DebounceAllyRequestMain : AllyRequestMain() {
        var pendingFlush: Runnable? = null
        lateinit var module: FakeGameModule

        override fun resolveModule(): AbstractGameModule? = module

        override fun scheduleTimeout(targetIndex: Int, onTimeout: Runnable) = Unit

        override fun cancelTimeout(targetIndex: Int) = Unit

        override fun scheduleSyncDebounce(onFlush: Runnable) {
            pendingFlush = onFlush
        }

        override fun cancelSyncDebounce() {
            pendingFlush = null
        }

        fun flushSync() {
            val flush = pendingFlush
            pendingFlush = null
            flush?.run()
        }

        fun clearPendingFlush() {
            pendingFlush = null
        }
    }

    @BeforeEach
    fun setUp() {
        Data.configServer = BeanServerConfig()
        module = FakeGameModule()
        main = DebounceAllyRequestMain()
        main.module = module
        client = CommandHandler("/")
        main.registerServerClientCommands(client)
        module.room.isStartGame = true
    }

    @AfterEach
    fun tearDown() {
        main.clearPendingFlush()
        AllyRequestService.clear()
    }

    private fun addPlayer(team: Int = 0, index: Int = 0, name: String = "P-$index"): TestPlayer {
        val p = TestPlayer(initialTeam = team, index = index, name = name)
        module.room.playerManage.playerAll.add(p)
        return p
    }

    @Test
    fun `two y in a row apply both teams but sync once after flush`() {
        val p0 = addPlayer(index = 0, name = "P0")
        val p1 = addPlayer(team = 1, index = 1, name = "P1")
        val p2 = addPlayer(team = 2, index = 2, name = "P2")
        AllyRequestService.createRequest(0, 1)
        AllyRequestService.createRequest(0, 2)

        client.handleMessage("/y", p1)
        client.handleMessage("/y", p2)

        assertEquals(0, p1.team)
        assertEquals(0, p2.team)
        assertEquals(0, module.syncCalls)
        assertNotNull(main.pendingFlush)
        assertTrue(p1.messages.any { it.contains("同意") })
        assertTrue(p2.messages.any { it.contains("同意") })
        assertTrue(p0.messages.any { it.contains("P1") && it.contains("同意") })
        assertTrue(p0.messages.any { it.contains("P2") && it.contains("同意") })

        main.flushSync()

        assertEquals(1, module.syncCalls)
        assertEquals(null, main.pendingFlush)
    }

    @Test
    fun `second wave after flush triggers another sync`() {
        val p0 = addPlayer(index = 0)
        val p1 = addPlayer(team = 1, index = 1)
        val p2 = addPlayer(team = 2, index = 2)
        AllyRequestService.createRequest(0, 1)

        client.handleMessage("/y", p1)
        main.flushSync()
        assertEquals(1, module.syncCalls)

        AllyRequestService.createRequest(0, 2)
        client.handleMessage("/y", p2)
        assertEquals(1, module.syncCalls)

        main.flushSync()
        assertEquals(2, module.syncCalls)
        assertEquals(0, p2.team)
    }

    @Test
    fun `lobby y does not schedule sync debounce`() {
        module.room.isStartGame = false
        addPlayer(index = 0)
        val p1 = addPlayer(team = 1, index = 1)
        AllyRequestService.createRequest(0, 1)

        client.handleMessage("/y", p1)

        assertEquals(0, p1.team)
        assertEquals(0, module.syncCalls)
        assertEquals(null, main.pendingFlush)
    }
}
