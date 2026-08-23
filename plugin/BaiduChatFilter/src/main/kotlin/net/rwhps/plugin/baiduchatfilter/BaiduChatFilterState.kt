package net.rwhps.plugin.baiduchatfilter

import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.net.Administration
import net.rwhps.server.util.log.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 聊天过滤器实现, 通过 [Administration.ChatFilter] 注册进服务器过滤管道。
 *
 * 说明:
 * - [filter] 在玩家连接线程同步执行, 调用百度 API 会阻塞该连接直至超时。
 * - 结果缓存 (消息内容 -> 结论) 减少重复请求, 缓解免费版 QPS 限制。
 * - 返回 null 表示拦截该消息, 返回字符串表示放行。
 *
 * @author RW-HPS
 */
class BaiduChatFilterState(
    @Volatile var config: BaiduChatFilterConfig,
) : Administration.ChatFilter {

    @Volatile
    private var client: TextCensorApi = createClient(config)

    internal constructor(config: BaiduChatFilterConfig, client: TextCensorApi) : this(config) {
        this.client = client
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val maxTextBytes = 20000

    override fun filter(player: PlayerHess, message: String?): String? {
        val cfg = config
        if (!cfg.enabled) {
            return message
        }
        if (message.isNullOrBlank()) {
            return message
        }
        if (cfg.skipAdmin && player.isAdmin) {
            return message
        }
        // 百度文本审核限制 20000 字节, 超长消息直接放行(服务端另有 maxMessageLen 限制)
        if (message.toByteArray(Charsets.UTF_8).size > maxTextBytes) {
            return message
        }

        val cached = getCached(message)
        if (cached != null) {
            return if (cached.conclusionType in cfg.blockConclusionTypes) {
                block(player, cfg, message, cached)
                null
            } else {
                message
            }
        }

        val result = client.checkText(message)
        if (result == null) {
            // API 失败 / 超时
            if (cfg.debug) {
                Log.clog("[BaiduChatFilter] 审核失败(消息已${if (cfg.failOpen) "放行" else "拦截"}): ${player.name}")
            }
            return if (cfg.failOpen) {
                message
            } else {
                blockWithReason(player, cfg, message, "审核服务不可用")
                null
            }
        }

        putCache(message, result.conclusionType)
        return if (result.conclusionType in cfg.blockConclusionTypes) {
            block(player, cfg, message, result)
            null
        } else {
            message
        }
    }

    /** 昵称审核: 返回结论类型, null 表示审核失败。复用消息结果缓存。 */
    fun checkName(name: String): Int? {
        val cached = getCached(name)
        if (cached != null) {
            return cached.conclusionType
        }
        val result = client.checkText(name)
        if (result != null) {
            putCache(name, result.conclusionType)
            return result.conclusionType
        }
        return null
    }

    /** 缓存命中拦截 */
    private fun block(player: PlayerHess, cfg: BaiduChatFilterConfig, message: String, entry: CacheEntry) {
        player.sendSystemMessage(cfg.blockMessage)
        Log.clog("[BaiduChatFilter] 拦截 ${player.name} 的违规消息(缓存 类型${entry.conclusionType}): ${message.short()}")
    }

    /** API 命中拦截 */
    private fun block(player: PlayerHess, cfg: BaiduChatFilterConfig, message: String, result: BaiduCheckResult) {
        player.sendSystemMessage(cfg.blockMessage)
        val detail = if (result.hits.isEmpty()) result.conclusion else "${result.conclusion} 命中[${result.hits.joinToString(",")}]"
        Log.clog("[BaiduChatFilter] 拦截 ${player.name} 的违规消息(类型${result.conclusionType} $detail): ${message.short()}")
    }

    private fun blockWithReason(player: PlayerHess, cfg: BaiduChatFilterConfig, message: String, reason: String) {
        player.sendSystemMessage(cfg.blockMessage)
        Log.clog("[BaiduChatFilter] 拦截 ${player.name} 的消息($reason): ${message.short()}")
    }

    private fun String.short(max: Int = 60): String {
        return if (length <= max) this else substring(0, max) + "..."
    }

    private fun getCached(message: String): CacheEntry? {
        val entry = cache[message] ?: return null
        val ttlMillis = config.cacheMinutes * 60_000L
        if (System.currentTimeMillis() - entry.time > ttlMillis) {
            cache.remove(message, entry)
            return null
        }
        return entry
    }

    private fun putCache(message: String, conclusionType: Int) {
        if (cache.size >= config.cacheMaxSize) {
            cache.clear()
        }
        cache[message] = CacheEntry(conclusionType, System.currentTimeMillis())
    }

    fun reload(newConfig: BaiduChatFilterConfig) {
        val old = client
        client = createClient(newConfig)
        old.close()
        config = newConfig
        cache.clear()
    }

    fun close() {
        client.close()
        cache.clear()
    }

    private fun createClient(cfg: BaiduChatFilterConfig): TextCensorApi {
        return BaiduClient(cfg.apiKey, cfg.secretKey, cfg.timeoutSeconds)
    }

    private data class CacheEntry(
        val conclusionType: Int,
        val time: Long,
    )
}
