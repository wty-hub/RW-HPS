package net.rwhps.plugin.baiduchatfilter

import net.rwhps.server.util.inline.toGson
import net.rwhps.server.util.inline.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaiduChatFilterConfigTest {

    @Test
    fun `defaults enable both chat and nickname filtering`() {
        val config = BaiduChatFilterConfig()

        assertTrue(config.enabled)
        assertTrue(config.filterName)
        assertTrue(config.skipAdmin)
        assertTrue(config.failOpen)
        assertFalse(config.debug)

        assertEquals("", config.apiKey)
        assertEquals("", config.secretKey)
        assertEquals(listOf(2, 3), config.blockConclusionTypes)
        assertEquals("您的消息包含违规内容，已被拦截", config.blockMessage)
        assertEquals("您的昵称包含违规内容，已被服务器踢出", config.nameKickMessage)
        assertEquals(0, config.nameKickDurationSeconds)
        assertEquals(5, config.timeoutSeconds)
        assertEquals(1000, config.cacheMaxSize)
        assertEquals(10, config.cacheMinutes)
    }

    @Test
    fun `empty json deserializes to all defaults`() {
        val config = BaiduChatFilterConfig::class.java.toGson("{}")
        assertEquals(BaiduChatFilterConfig(), config)
    }

    @Test
    fun `blank json deserializes to all defaults`() {
        val config = BaiduChatFilterConfig::class.java.toGson("")
        assertEquals(BaiduChatFilterConfig(), config)
    }

    @Test
    fun `partial json overrides only provided fields`() {
        val config = BaiduChatFilterConfig::class.java.toGson(
            """
            {
              "enabled": false,
              "filterName": false,
              "blockConclusionTypes": [2],
              "nameKickDurationSeconds": 600
            }
            """.trimIndent()
        )

        assertFalse(config.enabled)
        assertFalse(config.filterName)
        assertEquals(listOf(2), config.blockConclusionTypes)
        assertEquals(600, config.nameKickDurationSeconds)
        // 未提供的字段保持默认值
        assertTrue(config.skipAdmin)
        assertTrue(config.failOpen)
        assertEquals("您的昵称包含违规内容，已被服务器踢出", config.nameKickMessage)
        assertEquals(5, config.timeoutSeconds)
    }

    @Test
    fun `unknown fields in json are ignored`() {
        val config = BaiduChatFilterConfig::class.java.toGson("""{"enabled":false,"someUnknownField":123}""")
        assertFalse(config.enabled)
        assertEquals(listOf(2, 3), config.blockConclusionTypes)
    }

    @Test
    fun `json round-trip preserves all values`() {
        val config = BaiduChatFilterConfig(
            enabled = false,
            apiKey = "ak",
            secretKey = "sk",
            skipAdmin = false,
            failOpen = false,
            blockConclusionTypes = listOf(2),
            blockMessage = "blocked",
            filterName = false,
            nameKickMessage = "bad name",
            nameKickDurationSeconds = 300,
            timeoutSeconds = 8,
            cacheMaxSize = 50,
            cacheMinutes = 2,
            debug = true,
        )

        val restored = BaiduChatFilterConfig::class.java.toGson(config.toJson())
        assertEquals(config, restored)
    }
}
