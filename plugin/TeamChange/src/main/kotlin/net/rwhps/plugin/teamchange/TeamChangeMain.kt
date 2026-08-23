package net.rwhps.plugin.teamchange

import net.rwhps.server.data.global.Data
import net.rwhps.server.func.StrCons
import net.rwhps.server.game.headless.core.AbstractGameModule
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.util.IsUtils.notIsNumeric
import net.rwhps.server.util.game.command.CommandHandler

/**
 * 强制修改玩家队伍插件。
 *
 * 区别于核心内置的 `team` 指令 ( 仅限大厅 ), 本插件的 `forceteam`
 * 在开局后同样可用: 修改玩家同盟字段后调用全量存档同步
 * ( `allPlayerSync()` → SYNC/35 ) 把新的队伍关系强制同步给所有客户端。
 *
 * 注意: 开局后改队伍会导致全员重载网络存档 ( 短时卡顿 ), 属于实验性功能。
 *
 * 游戏内 ( 管理员 ): `.forceteam <席位> <队伍>`
 * 控制台:            `forceteam <席位> <队伍>`
 */
open class TeamChangeMain : Plugin() {

    /**
     * 获取当前 Hess 游戏模块。测试可通过子类覆写注入假模块。
     */
    protected open fun resolveModule(): AbstractGameModule? =
        if (HeadlessModuleManage.initHPS()) HeadlessModuleManage.hps else null

    override fun registerServerClientCommands(handler: CommandHandler) {
        handler.register("forceteam", "<PlayerPosition> <Team>", "#强制修改玩家队伍(支持开局后)") { args: Array<String>, player: PlayerHess ->
            if (!player.isAdmin) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.noAdmin"))
                return@register
            }
            if (args.size < 2 || notIsNumeric(args[0]) || notIsNumeric(args[1])) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.noNumber"))
                return@register
            }

            val hps = resolveModule()
            if (hps == null) {
                player.sendSystemMessage("服务器尚未启动, 无法修改队伍")
                return@register
            }

            val position = args[0].toInt()
            if (position < 1 || position > Data.configServer.maxPlayer) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.maxPlayer"))
                return@register
            }

            val target = hps.room.playerManage.getPlayer(position - 1)
            if (target == null) {
                player.sendSystemMessage(player.i18NBundle.getinput("err.player.no.site", position))
                return@register
            }

            val newTeam = args[1].toInt() - 1
            val started = hps.room.isStartGame
            hps.gameFunction.suspendMainThreadOperations {
                TeamChangeService.apply(target, newTeam, started) {
                    hps.gameLinkFunction.allPlayerSync()
                }
            }
            player.sendSystemMessage(
                "已将玩家 ${target.name} (席位 $position) 的队伍修改为 ${newTeam + 1}" +
                    if (started) " (开局中, 已强制同步)" else ""
            )
        }
    }

    override fun registerServerCommands(handler: CommandHandler) {
        handler.register("forceteam", "<PlayerPosition> <Team>", "#强制修改玩家队伍(支持开局后)") { args: Array<String>, log: StrCons ->
            if (args.size < 2 || notIsNumeric(args[0]) || notIsNumeric(args[1])) {
                log("用法: forceteam <席位> <队伍>")
                return@register
            }

            val hps = resolveModule()
            if (hps == null) {
                log("服务器尚未启动, 无法修改队伍")
                return@register
            }

            val position = args[0].toInt()
            if (position < 1 || position > Data.configServer.maxPlayer) {
                log("超过最大玩家数")
                return@register
            }

            val target = hps.room.playerManage.getPlayer(position - 1)
            if (target == null) {
                log("位置: $position 不存在玩家")
                return@register
            }

            val newTeam = args[1].toInt() - 1
            val started = hps.room.isStartGame
            TeamChangeService.apply(target, newTeam, started) {
                hps.gameLinkFunction.allPlayerSync()
            }
            log(
                "已将玩家 ${target.name} (席位 $position) 的队伍修改为 ${newTeam + 1}" +
                    if (started) " (开局中, 已强制同步)" else ""
            )
        }
    }
}
