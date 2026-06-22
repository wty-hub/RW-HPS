package net.rwhps.plugin.playeripgeo

import net.rwhps.server.game.event.core.EventListenerHost
import net.rwhps.server.game.event.game.PlayerJoinEvent
import net.rwhps.server.util.annotations.core.EventListenerHandler
import net.rwhps.server.util.inline.coverConnect
import net.rwhps.server.util.log.Log

class PlayerIpGeoEvent : EventListenerHost {
    @EventListenerHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val connect = player.con?.coverConnect() ?: return

        val ip = connect.ip
        val location = PlayerIpGeoLookup.lookup(ip)

        Log.clog("[PlayerIpGeo] 玩家 ${player.name} 进入 | IP: $ip | 地理位置: $location")
    }
}
