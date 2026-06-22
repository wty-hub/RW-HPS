package net.rwhps.plugin.playeripgeo

import net.rwhps.server.game.event.EventManage
import net.rwhps.server.plugin.Plugin

class PlayerIpGeoMain : Plugin() {
    override fun onEnable() {
        PlayerIpGeoLookup.lookup("127.0.0.1")
    }

    override fun registerEvents(eventManage: EventManage) {
        eventManage.registerListener(PlayerIpGeoEvent())
    }
}
