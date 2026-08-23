package net.rwhps.plugin.allyrequest

import net.rwhps.server.game.event.EventManage
import net.rwhps.server.game.event.game.ServerGameOverEvent.GameOverData
import net.rwhps.server.game.headless.core.*
import net.rwhps.server.game.headless.core.link.*
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

/** 可测试用假游戏模块: 记录 allPlayerSync 调用次数, 直接执行 suspend 任务 */
internal class FakeGameModule : AbstractGameModule {
    var syncCalls = 0
    var suspendRuns = 0

    override val room: ServerRoom by lazy { ServerRoom(this) }
    override val useClassLoader: ClassLoader get() = this.javaClass.classLoader
    override val eventManage: EventManage get() = EventManage()
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
            suspendRuns++
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
        override fun allPlayerSync() {
            syncCalls++
        }
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

/** 记录 sendSystemMessage 的测试玩家 */
internal class TestPlayer(
    initialTeam: Int = 0,
    index: Int = 0,
    isAdmin: Boolean = false,
    name: String = "TestPlayer",
    alive: Boolean = true,
) : PlayerHess(null, I18NBundle(ByteArrayInputStream(ByteArray(0))), FakePlayerData(initialTeam, index, name, alive)) {
    val messages = mutableListOf<String>()

    init {
        this.isAdmin = isAdmin
    }

    override fun sendSystemMessage(text: String) {
        messages.add(text)
    }
}

/** 简易可写 AbstractLinkPlayerData, 仅关注 team/index/name/survive */
internal class FakePlayerData(
    initialTeam: Int = 0,
    override var index: Int = 0,
    override val name: String = "FakePlayer",
    alive: Boolean = true,
) : AbstractLinkPlayerData {
    override var team: Int = initialTeam
    override var credits: Int = 0
    override var startUnit: Int = 0
    override var color: Int = 0
    override var aiDifficulty: Int = 1
    override val connectHexID: String = "FAKE-$name-$index"
    override var survive: Boolean = alive
    override val unitsKilled: Int = 0
    override val buildingsKilled: Int = 0
    override val experimentalsKilled: Int = 0
    override val unitsLost: Int = 0
    override val buildingsLost: Int = 0
    override val experimentalsLost: Int = 0
    override fun updateDate() = Unit
}
