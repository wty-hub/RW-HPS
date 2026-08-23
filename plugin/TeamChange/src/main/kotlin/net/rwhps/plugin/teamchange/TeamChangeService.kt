package net.rwhps.plugin.teamchange

import net.rwhps.server.game.player.PlayerHess

/**
 * 玩家队伍修改服务。
 *
 * 将「改队伍」核心逻辑独立出来, 与命令注册解耦, 便于单元测试:
 * 只要传 [target] 玩家、目标队伍 [newTeam]、开局标志 [roomStarted] 与强制同步回调 [sync],
 * 即可复用与内置 team 指令一致的写队逻辑。
 *
 * 开局后 ( [roomStarted] == true ) 修改队伍需要配合全量存档同步
 * ( SYNC/35, 见 `AbstractLinkGameFunction.allPlayerSync()` ),
 * 否则客户端不会应用新的同盟关系。
 *
 * @param newTeam 目标队伍, 0-based (与游戏内字段 `r` / `PlayerHess.team` 一致)
 */
object TeamChangeService {
    fun apply(
        target: PlayerHess,
        newTeam: Int,
        roomStarted: Boolean,
        sync: () -> Unit,
    ) {
        target.team = newTeam
        if (roomStarted) {
            sync()
        }
    }
}
