package net.rwhps.plugin.baiduchatfilter

import net.rwhps.server.game.event.EventManage
import net.rwhps.server.game.event.game.ServerGameOverEvent.GameOverData
import net.rwhps.server.game.headless.core.AbstractGameData
import net.rwhps.server.game.headless.core.AbstractGameFast
import net.rwhps.server.game.headless.core.AbstractGameFunction
import net.rwhps.server.game.headless.core.AbstractGameHessData
import net.rwhps.server.game.headless.core.AbstractGameModule
import net.rwhps.server.game.headless.core.AbstractGameUnitData
import net.rwhps.server.game.headless.core.link.AbstractLinkGameFunction
import net.rwhps.server.game.headless.core.link.AbstractLinkGameNet
import net.rwhps.server.game.headless.core.link.AbstractLinkGameServerData
import net.rwhps.server.game.headless.core.link.AbstractLinkPlayerData
import net.rwhps.server.game.headless.core.scripts.AbstractScriptMultiPlayer
import net.rwhps.server.game.headless.core.scripts.AbstractScriptRoot
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.game.room.ServerRoom
import net.rwhps.server.net.core.server.AbstractNetConnectServer
import net.rwhps.server.struct.list.Seq
import net.rwhps.server.struct.map.ObjectMap
import net.rwhps.server.struct.map.OrderedMap
import net.rwhps.server.util.file.load.I18NBundle
import java.io.ByteArrayInputStream

/** 可编程的假文本审核客户端: 记录调用, 可配置返回结果或失败 */
internal class FakeTextCensor(
    var result: BaiduCheckResult = BaiduCheckResult(BaiduCheckResult.TYPE_PASS, "合规", emptyList()),
    var shouldFail: Boolean = false,
) : TextCensorApi {
    val calls = mutableListOf<String>()
    var closed = false

    override fun checkText(text: String): BaiduCheckResult? {
        calls.add(text)
        return if (shouldFail) null else result
    }

    override fun close() {
        closed = true
    }
}

/** 记录 sendSystemMessage 与 kickPlayer 的测试玩家 */
internal class TestPlayer(
    name: String = "TestPlayer",
    isAdmin: Boolean = false,
) : PlayerHess(null, I18NBundle(ByteArrayInputStream(ByteArray(0))), FakePlayerData(name)) {
    val messages = mutableListOf<String>()
    val kicks = mutableListOf<Pair<String, Int>>()

    init {
        this.isAdmin = isAdmin
    }

    override fun sendSystemMessage(text: String) {
        messages.add(text)
    }

    override fun kickPlayer(text: String, time: Int) {
        kicks.add(text to time)
    }
}

/** 简易可写 AbstractLinkPlayerData, 仅关注 name/index */
internal class FakePlayerData(
    override val name: String = "FakePlayer",
    override var index: Int = 0,
) : AbstractLinkPlayerData {
    override var team: Int = 0
    override var credits: Int = 0
    override var startUnit: Int = 0
    override var color: Int = 0
    override var aiDifficulty: Int = 1
    override val connectHexID: String = "FAKE-$name-$index"
    override val survive: Boolean = true
    override val unitsKilled: Int = 0
    override val buildingsKilled: Int = 0
    override val experimentalsKilled: Int = 0
    override val unitsLost: Int = 0
    override val buildingsLost: Int = 0
    override val experimentalsLost: Int = 0
    override fun updateDate() = Unit
}

/** 仅用于构造 PlayerJoinEvent 的最小游戏模块(成员不被事件处理器使用) */
internal class FakeGameModule : AbstractGameModule {
    override val useClassLoader: ClassLoader get() = this.javaClass.classLoader
    override val eventManage: EventManage get() = EventManage()
    override val room: ServerRoom by lazy { ServerRoom(this) }
    override val gameData: AbstractGameData = object : AbstractGameData {
        override val commandPacketList = Seq<ByteArray>()
    }
    override val gameFast: AbstractGameFast = object : AbstractGameFast {
        override fun filteredPacket(packet: Any): Boolean = false
    }
    override val gameHessData: AbstractGameHessData = object : AbstractGameHessData {
        override val tickHess: Int = 0
        override val tickNetHess: Int = 0
        override val gameDelta: Long = 0L
        override val gameFPS: Int = 0
        override fun getWin(position: Int): Boolean = false
        override fun getGameOverData(): GameOverData? = null
        override fun getPlayerBirthPointXY() = Unit
        override fun existPlayer(position: Int): Boolean = false
        override fun getHeadlessAIServer(): AbstractNetConnectServer =
            throw UnsupportedOperationException("fake module")
    }
    override val gameUnitData: AbstractGameUnitData = object : AbstractGameUnitData {
        override var useMod: Boolean = false
        override fun reloadUnitData() = Unit
        override fun getUnitData(coreName: String): OrderedMap<String, ObjectMap<String, Int>> = OrderedMap()
        override fun getRwModLoadInfo(): Seq<String> = Seq()
    }
    override val gameFunction: AbstractGameFunction = object : AbstractGameFunction {
        override fun suspendMainThreadOperations(run: Runnable) {
            run.run()
        }
        override val neverEnd: IntArray = intArrayOf()
    }
    override val gameScriptMultiPlayer: AbstractScriptMultiPlayer = object : AbstractScriptMultiPlayer {
        override fun addAi() = Unit
        override fun multiplayerStart() = Unit
    }
    override val gameScriptRoot: AbstractScriptRoot = object : AbstractScriptRoot {}
    override val gameLinkFunction: AbstractLinkGameFunction = object : AbstractLinkGameFunction {
        override fun allPlayerSync() = Unit
        override fun pauseGame(pause: Boolean) = Unit
        override fun battleRoom(time: Int) = Unit
        override fun saveGame() = Unit
        override fun clean() = Unit
    }
    override val gameLinkServerData: AbstractLinkGameServerData = object : AbstractLinkGameServerData {
        override val teamOperationsSyncObject: Any = Any()
        override var maxUnit: Int = 0
        override var sharedcontrol: Boolean = false
        override var fog: Int = 0
        override var nukes: Boolean = false
        override var credits: Int = 0
        override var aiDifficuld: Int = 0
        override var income: Float = 0f
        override var startingunits: Int = 0
        override fun resetRoomToDefaults() = Unit
        override fun getPlayerData(position: Int): AbstractLinkPlayerData =
            throw UnsupportedOperationException("fake module has no real player")
        override fun getPlayerAIData(position: Int): AbstractLinkPlayerData =
            throw UnsupportedOperationException("fake module has no real player")
    }
    override val gameLinkNet: AbstractLinkGameNet = object : AbstractLinkGameNet {
        override fun newConnect(ip: String, name: String) = Unit
        override fun startHeadlessServer(port: Int, passwd: String?) = Unit
        override fun closeHeadlessServer() = Unit
        override fun reBootServer(run: () -> Unit) = Unit
    }
}
