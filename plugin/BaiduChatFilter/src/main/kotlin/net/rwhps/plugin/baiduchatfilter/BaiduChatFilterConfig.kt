package net.rwhps.plugin.baiduchatfilter

import net.rwhps.server.data.bean.AbstractBeanConfig
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.inline.toGson

data class BaiduChatFilterConfig(
    val enabled: Boolean = true,
    val apiKey: String = "",
    val secretKey: String = "",
    val skipAdmin: Boolean = true,
    val failOpen: Boolean = true,
    val blockConclusionTypes: List<Int> = listOf(2, 3),
    val blockMessage: String = "您的消息包含违规内容，已被拦截",
    val filterName: Boolean = true,
    val nameKickMessage: String = "您的昵称包含违规内容，已被服务器踢出",
    val nameKickDurationSeconds: Int = 0,
    val timeoutSeconds: Int = 5,
    val cacheMaxSize: Int = 1000,
    val cacheMinutes: Int = 10,
    val debug: Boolean = false,
) : AbstractBeanConfig(this::class.java, "") {
    companion object {
        fun get(fileUtils: FileUtils): BaiduChatFilterConfig {
            val config: BaiduChatFilterConfig = BaiduChatFilterConfig::class.java.toGson(fileUtils.readFileStringData())
            config.bindFile(fileUtils)
            config.readProperty()
            return config
        }
    }
}
