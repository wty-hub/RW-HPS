package net.rwhps.plugin.namefilter

import net.rwhps.server.game.event.core.EventListenerHost
import net.rwhps.server.game.event.game.PlayerJoinEvent
import net.rwhps.server.util.annotations.core.EventListenerHandler
import net.rwhps.server.util.log.Log

class NameFilterEvent(
    private val state: NameFilterState,
) : EventListenerHost {
    @EventListenerHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val pattern = state.pattern
        if (pattern.matches(player.name)) {
            return
        }
        player.kickPlayer(state.config.kickMessage, state.config.kickDurationSeconds)
        Log.clog("[NameFilter] 已踢出玩家 ${player.name}，昵称不符合正则: ${state.config.namePattern}")
    }
}
