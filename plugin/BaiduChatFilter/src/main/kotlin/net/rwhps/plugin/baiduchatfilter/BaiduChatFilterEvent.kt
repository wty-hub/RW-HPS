package net.rwhps.plugin.baiduchatfilter

import net.rwhps.server.game.event.core.EventListenerHost
import net.rwhps.server.game.event.game.PlayerJoinEvent
import net.rwhps.server.util.annotations.core.EventListenerHandler
import net.rwhps.server.util.log.Log

/**
 * 昵称过滤事件监听器
 *
 * 在 [PlayerJoinEvent] 中调用百度内容安全 API 审核玩家昵称,
 * 命中 [BaiduChatFilterConfig.blockConclusionTypes] 则踢出玩家。
 *
 * @author RW-HPS
 */
class BaiduChatFilterEvent(
    private val main: BaiduChatFilterMain,
) : EventListenerHost {

    @EventListenerHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val state = main.state ?: return
        val cfg = state.config
        if (!cfg.filterName) {
            return
        }
        val player = event.player
        if (cfg.skipAdmin && player.isAdmin) {
            return
        }
        val conclusionType = state.checkName(player.name) ?: run {
            if (!cfg.failOpen) {
                player.kickPlayer(cfg.nameKickMessage, cfg.nameKickDurationSeconds)
                Log.clog("[BaiduChatFilter] 昵称审核失败，已踢出 ${player.name}")
            }
            return
        }
        if (conclusionType in cfg.blockConclusionTypes) {
            player.kickPlayer(cfg.nameKickMessage, cfg.nameKickDurationSeconds)
            Log.clog("[BaiduChatFilter] 昵称违规(类型$conclusionType)，已踢出 ${player.name}")
        }
    }
}
