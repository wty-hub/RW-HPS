package net.rwhps.plugin.playeripgeo

import net.renfei.ip2location.IP2Location
import net.rwhps.server.util.compression.CompressionDecoderUtils
import net.rwhps.server.util.log.Log

object PlayerIpGeoLookup {
    private val searcher by lazy {
        IP2Location().apply {
            val stream = PlayerIpGeoMain::class.java.getResourceAsStream("/ip2location.7z")
                ?: error("ip2location.7z not found in PlayerIpGeo.jar")
            Open(
                CompressionDecoderUtils.sevenAllReadStream(stream)
                    .getZipAllBytes(false)["IP2LOCATION-LITE-DB5.BIN"]
            )
        }.also {
            Log.clog("[PlayerIpGeo] IP2Location 数据库已加载 (IP2LOCATION-LITE-DB5)")
        }
    }

    fun lookup(ip: String): String {
        if (ip.isBlank()) {
            return "未知"
        }
        return try {
            val rec = searcher.IPQuery(ip)
            when (rec.status) {
                "OK" -> "${rec.countryLong}|${rec.region}|${rec.city}"
                else -> "未知 (${rec.status})"
            }
        } catch (e: Exception) {
            Log.error("[PlayerIpGeo] IP 查询失败: $ip", e)
            "未知"
        }
    }
}
