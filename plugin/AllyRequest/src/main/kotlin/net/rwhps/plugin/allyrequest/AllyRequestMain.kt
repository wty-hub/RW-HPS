package net.rwhps.plugin.allyrequest

import net.rwhps.server.core.thread.Threads
import net.rwhps.server.data.global.Data
import net.rwhps.server.game.headless.core.AbstractGameModule
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.util.IsUtils.notIsNumeric
import net.rwhps.server.util.game.command.CommandHandler
import java.util.concurrent.TimeUnit

/**
 * 结盟请求插件。
 *
 * 参考结盟脚本的 `tti`/`tta` 邀请流程, 改为服务端实现:
 * - 游戏内 ( 管理员与否均可 ) 发送 `.jm <席位>` 向指定位置玩家发起结盟
 * - 被邀请方 30 秒内用 `.y` 同意 ( 加入发起方队伍 ) / `.n` 拒绝
 * - 30 秒未回复默认拒绝
 * - 同一玩家存在待决请求期间, 不能被其他玩家再次结盟
 *
 * 开局后落队通过全量存档同步 ( SYNC/35 `allPlayerSync` ) 强制应用,
 * 与 TeamChange 插件机制一致; 本插件自包含实现, 不依赖其他插件。
 */
open class AllyRequestMain : Plugin() {

    /** 获取当前 Hess 游戏模块。测试可通过子类覆写注入假模块。 */
    protected open fun resolveModule(): AbstractGameModule? =
        if (HeadlessModuleManage.initHPS()) HeadlessModuleManage.hps else null

    /** 启动 30 秒结盟超时倒计时。测试可覆写为不真实调度。 */
    protected open fun scheduleTimeout(targetIndex: Int, onTimeout: Runnable) {
        Threads.newCountdown(
            "$TIMER_NAME_PREFIX$targetIndex", TIMER_GROUP, "结盟请求超时",
            REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS, onTimeout
        )
    }

    /** 取消指定目标的结盟超时倒计时。 */
    protected open fun cancelTimeout(targetIndex: Int) {
        Threads.closeTimeTask("$TIMER_NAME_PREFIX$targetIndex", TIMER_GROUP)
    }

    /**
     * 开局后全量存档同步防抖。连续多次同意结盟时只保留最后一次调度。
     * 测试可覆写为立即执行或记录 [onFlush] 以便手动 flush。
     */
    protected open fun scheduleSyncDebounce(onFlush: Runnable) {
        cancelSyncDebounce()
        Threads.newCountdown(
            SYNC_TIMER_NAME, TIMER_GROUP, "结盟存档同步防抖",
            SYNC_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS, onFlush
        )
    }

    /** 取消尚未执行的存档同步防抖。 */
    protected open fun cancelSyncDebounce() {
        Threads.closeTimeTask(SYNC_TIMER_NAME, TIMER_GROUP)
    }

    /** 将 [allPlayerSync] 排进防抖窗口; 触发时仍走主线程闸门。 */
    private fun requestAllPlayerSync(hps: AbstractGameModule) {
        scheduleSyncDebounce {
            hps.gameFunction.suspendMainThreadOperations {
                hps.gameLinkFunction.allPlayerSync()
            }
        }
    }

    override fun registerServerClientCommands(handler: CommandHandler) {
        handler.register("jm", "<PlayerPosition>", "#发起结盟请求") { args: Array<String>, player: PlayerHess ->
            val hps = resolveModule()
            if (hps == null) {
                player.sendSystemMessage("服务器尚未启动, 无法发起结盟")
                return@register
            }
            if (!hps.room.isStartGame) {
                player.sendSystemMessage("只能在游戏内发起结盟")
                return@register
            }
            if (args.isEmpty() || notIsNumeric(args[0])) {
                player.sendSystemMessage("用法: .jm <席位>")
                return@register
            }

            val position = args[0].toInt()
            if (position < 1 || position > Data.configServer.maxPlayer) {
                player.sendSystemMessage("超过最大玩家数")
                return@register
            }

            val target = hps.room.playerManage.getPlayer(position - 1)
            if (target == null) {
                player.sendSystemMessage("席位 $position 没有玩家")
                return@register
            }
            if (target.index == player.index) {
                player.sendSystemMessage("不能向自己发起结盟")
                return@register
            }
            if (target.isAi) {
                player.sendSystemMessage("不能向 AI 发起结盟")
                return@register
            }
            if (!player.survive) {
                player.sendSystemMessage("你已经阵亡, 无法发起结盟")
                return@register
            }
            if (!target.survive) {
                player.sendSystemMessage("该玩家已经阵亡, 无法发起结盟")
                return@register
            }
            if (target.team == player.team) {
                player.sendSystemMessage("你们已经在同一队伍了")
                return@register
            }

            val request = AllyRequestService.createRequest(player.index, target.index)
            if (request == null) {
                player.sendSystemMessage("对方 30 秒内已有待处理的结盟请求, 请稍后再试")
                return@register
            }

            scheduleTimeout(target.index) { handleTimeout(target.index) }

            player.sendSystemMessage(
                "已向玩家 ${target.name} (席位 $position) 发起结盟请求, 等待对方 30 秒内使用 .y 同意或 .n 拒绝"
            )
            target.sendSystemMessage(
                "玩家 ${player.name} (席位 ${player.position}) 向您发起结盟请求, 请在 30 秒内使用 .y 同意或 .n 拒绝"
            )
        }

        handler.register("y", "#同意结盟请求") { _: Array<String>, player: PlayerHess ->
            val hps = resolveModule()
            if (hps == null) {
                player.sendSystemMessage("服务器尚未启动, 无法处理结盟")
                return@register
            }
            val request = AllyRequestService.findByTarget(player.index)
            if (request == null) {
                player.sendSystemMessage("没有人向你发起结盟请求")
                return@register
            }

            val initiator = hps.room.playerManage.getPlayer(request.initiatorIndex)
            if (initiator == null) {
                AllyRequestService.removeRequest(player.index)
                cancelTimeout(player.index)
                player.sendSystemMessage("发起结盟的玩家已不在房间")
                return@register
            }

            if (initiator.team == player.team) {
                AllyRequestService.removeRequest(player.index)
                cancelTimeout(player.index)
                player.sendSystemMessage("你们已经在同一队伍了")
                return@register
            }

            val started = hps.room.isStartGame
            hps.gameFunction.suspendMainThreadOperations {
                AllyRequestService.applyTeamChange(player, initiator.team, started) {
                    requestAllPlayerSync(hps)
                }
            }

            AllyRequestService.removeRequest(player.index)
            cancelTimeout(player.index)

            player.sendSystemMessage("你已同意 ${initiator.name} 的结盟请求, 加入了其队伍")
            initiator.sendSystemMessage("玩家 ${player.name} 同意了你的结盟请求, 已加入你的队伍")
        }

        handler.register("n", "#拒绝结盟请求") { _: Array<String>, player: PlayerHess ->
            val hps = resolveModule()
            if (hps == null) {
                player.sendSystemMessage("服务器尚未启动, 无法处理结盟")
                return@register
            }
            val request = AllyRequestService.findByTarget(player.index)
            if (request == null) {
                player.sendSystemMessage("没有人向你发起结盟请求")
                return@register
            }

            AllyRequestService.removeRequest(player.index)
            cancelTimeout(player.index)

            val initiator = hps.room.playerManage.getPlayer(request.initiatorIndex)
            player.sendSystemMessage("你已拒绝结盟请求")
            initiator?.sendSystemMessage("玩家 ${player.name} 拒绝了你的结盟请求")
        }
    }

    /** 30 秒超时: 默认拒绝, 清除请求并通知双方。 */
    private fun handleTimeout(targetIndex: Int) {
        val request = AllyRequestService.findByTarget(targetIndex) ?: return
        val hps = resolveModule() ?: run {
            AllyRequestService.removeRequest(targetIndex)
            return
        }
        hps.gameFunction.suspendMainThreadOperations {
            AllyRequestService.removeRequest(targetIndex)
            val target = hps.room.playerManage.getPlayer(targetIndex)
            val initiator = hps.room.playerManage.getPlayer(request.initiatorIndex)
            target?.sendSystemMessage("你未在 30 秒内回应结盟请求, 已默认拒绝")
            initiator?.sendSystemMessage(
                "玩家 ${target?.name ?: "该玩家"} 未在 30 秒内回应结盟请求, 已超时"
            )
        }
    }

    override fun onDisable() {
        cancelSyncDebounce()
        AllyRequestService.pendingTargets().forEach { cancelTimeout(it) }
        AllyRequestService.clear()
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 30
        const val SYNC_DEBOUNCE_MILLIS = 300
        const val TIMER_GROUP = "AllyRequest"
        const val TIMER_NAME_PREFIX = "jm-"
        const val SYNC_TIMER_NAME = "ally-sync"
    }
}
