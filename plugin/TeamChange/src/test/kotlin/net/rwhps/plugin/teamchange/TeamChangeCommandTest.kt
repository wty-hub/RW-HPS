package net.rwhps.plugin.teamchange

import net.rwhps.server.data.bean.BeanServerConfig
import net.rwhps.server.data.global.Data
import net.rwhps.server.func.StrCons
import net.rwhps.server.game.headless.core.AbstractGameModule
import net.rwhps.server.util.game.command.CommandHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 通过真实 [CommandHandler] 验证 forceteam 的注册、校验与对服务层的调用接线。
 *
 * 使用 [FakeGameModule] 注入, 不依赖真实游戏 boot ( JUnit 内无法满足
 * GameStartInit 对 core jar 流的依赖 )。真实游戏环境下的最终行为需实机验证。
 */
class TeamChangeCommandTest {
    private lateinit var module: FakeGameModule
    private lateinit var console: CommandHandler
    private lateinit var client: CommandHandler

    private val logs = mutableListOf<String>()
    private val log = StrCons { logs += it }

    @BeforeEach
    fun setUp() {
        Data.configServer = BeanServerConfig()
        module = FakeGameModule()
        val main = object : TeamChangeMain() {
            override fun resolveModule(): AbstractGameModule? = module
        }
        console = CommandHandler("")
        client = CommandHandler("/")
        main.registerServerCommands(console)
        main.registerServerClientCommands(client)
    }

    private fun addTargetPlayer(initialTeam: Int = 0, index: Int = 0): TestPlayer {
        val target = TestPlayer(initialTeam = initialTeam, index = index, name = "Target-$index")
        module.room.playerManage.playerAll.add(target)
        return target
    }

    // ---------- console ----------

    @Test
    fun `console command changes team in lobby without sync`() {
        val target = addTargetPlayer(initialTeam = 1, index = 0)

        console.handleMessage("forceteam 1 3", log)

        assertEquals(2, target.team)
        assertEquals(0, module.syncCalls)
        assertTrue(logs.any { it.contains("队伍修改为 3") })
    }

    @Test
    fun `console command changes team and syncs after start`() {
        module.room.isStartGame = true
        val target = addTargetPlayer(initialTeam = 0, index = 1)

        console.handleMessage("forceteam 2 4", log)

        assertEquals(3, target.team)
        assertEquals(1, module.syncCalls)
        assertTrue(logs.any { it.contains("已强制同步") })
    }

    @Test
    fun `console command rejects player position beyond max`() {
        addTargetPlayer(index = 0)
        logs.clear()

        console.handleMessage("forceteam 11 2", log)

        assertTrue(logs.any { it.contains("超过最大玩家数") })
        assertEquals(0, module.syncCalls)
    }

    @Test
    fun `console command rejects empty player position`() {
        addTargetPlayer(index = 0)
        logs.clear()

        console.handleMessage("forceteam 2 2", log)

        assertTrue(logs.any { it.contains("不存在玩家") })
        assertEquals(0, module.syncCalls)
    }

    @Test
    fun `console command rejects non numeric args`() {
        console.handleMessage("forceteam abc def", log)

        assertTrue(logs.any { it.contains("用法") })
        assertEquals(0, module.syncCalls)
    }

    @Test
    fun `console command rejects when server not started`() {
        val noModule = object : TeamChangeMain() {
            override fun resolveModule(): AbstractGameModule? = null
        }
        val h = CommandHandler("")
        noModule.registerServerCommands(h)

        h.handleMessage("forceteam 1 2", log)

        assertTrue(logs.any { it.contains("尚未启动") })
    }

    // ---------- client (in-game) ----------

    @Test
    fun `client command requires admin`() {
        addTargetPlayer(index = 0)
        val admin = TestPlayer(isAdmin = false)

        client.handleMessage("/forceteam 1 2", admin)

        assertTrue(admin.messages.any { it.contains("err.noAdmin") })
        assertEquals(0, module.syncCalls)
    }

    @Test
    fun `client command changes team and syncs after start`() {
        module.room.isStartGame = true
        val target = addTargetPlayer(initialTeam = 1, index = 0)
        val admin = TestPlayer(isAdmin = true)

        client.handleMessage("/forceteam 1 2", admin)

        assertEquals(1, target.team)
        assertEquals(1, module.syncCalls)
        assertEquals(1, module.suspendRuns)
        assertTrue(admin.messages.any { it.contains("已强制同步") })
    }

    @Test
    fun `client command lobby change does not sync`() {
        val target = addTargetPlayer(initialTeam = 0, index = 0)
        val admin = TestPlayer(isAdmin = true)

        client.handleMessage("/forceteam 1 5", admin)

        assertEquals(4, target.team)
        assertEquals(0, module.syncCalls)
        assertFalse(admin.messages.any { it.contains("已强制同步") })
    }
}
