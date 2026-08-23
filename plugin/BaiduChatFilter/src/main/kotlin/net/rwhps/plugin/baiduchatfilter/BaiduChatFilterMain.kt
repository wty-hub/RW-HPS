package net.rwhps.plugin.baiduchatfilter

import net.rwhps.server.data.global.Data
import net.rwhps.server.func.StrCons
import net.rwhps.server.game.event.EventManage
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.util.game.command.CommandHandler
import net.rwhps.server.util.log.Log

/**
 * 百度内容安全聊天过滤插件
 *
 * 在聊天消息广播前调用百度内容安全文本审核 API 进行同步过滤,
 * 命中 [BaiduChatFilterConfig.blockConclusionTypes] 的消息被拦截并通知玩家。
 *
 * @author RW-HPS
 */
class BaiduChatFilterMain : Plugin() {
    private lateinit var config: BaiduChatFilterConfig
    internal var state: BaiduChatFilterState? = null

    override fun onEnable() {
        config = BaiduChatFilterConfig.get(pluginDataFileUtils.toFile("BaiduChatFilterConfig.json"))
        config.save()
    }

    override fun registerEvents(eventManage: EventManage) {
        eventManage.registerListener(BaiduChatFilterEvent(this))
    }

    override fun init() {
        if (!config.enabled) {
            Log.clog("[BaiduChatFilter] 插件已禁用 (enabled=false)")
            return
        }
        if (config.apiKey.isBlank() || config.secretKey.isBlank()) {
            Log.clog("[BaiduChatFilter] 未配置 apiKey/secretKey，请在 BaiduChatFilterConfig.json 中填写百度智能云应用密钥")
            return
        }
        val newState = BaiduChatFilterState(config)
        Data.core.admin.addChatFilter(newState)
        state = newState
        Log.clog("[BaiduChatFilter] 已启用，百度内容安全文本审核过滤")
    }

    override fun registerCoreCommands(handler: CommandHandler) {
        handler.register("chatfilter", "[args...]", "#百度内容安全聊天过滤: status | reload | test <文本> | enable | disable") { args: Array<String>, log: StrCons ->
            when (args.getOrNull(0)) {
                null, "status" -> showStatus(log)
                "reload" -> reload(log)
                "test" -> test(args, log)
                "enable" -> setEnabled(true, log)
                "disable" -> setEnabled(false, log)
                else -> log("用法: chatfilter [status|reload|test <文本>|enable|disable]")
            }
        }
    }

    override fun onDisable() {
        state?.close()
        state = null
    }

    private fun showStatus(log: StrCons) {
        val s = state
        log("BaiduChatFilter 状态:")
        log("  启用: ${config.enabled}")
        log("  已注册过滤器: ${s != null}")
        log("  apiKey: ${mask(config.apiKey)}")
        log("  跳过管理员: ${config.skipAdmin}")
        log("  审核失败放行: ${config.failOpen}")
        log("  拦截类型: ${config.blockConclusionTypes}")
        log("  过滤昵称: ${config.filterName}")
        log("  昵称踢出消息: ${config.nameKickMessage}")
        log("  昵称踢出时长(秒): ${config.nameKickDurationSeconds}")
        log("  超时(秒): ${config.timeoutSeconds}")
        log("  缓存: ${config.cacheMaxSize}条 / ${config.cacheMinutes}分钟")
        log("  debug: ${config.debug}")
    }

    private fun reload(log: StrCons) {
        try {
            val newConfig = BaiduChatFilterConfig.get(pluginDataFileUtils.toFile("BaiduChatFilterConfig.json"))
            newConfig.save()
            val s = state
            if (s != null) {
                s.reload(newConfig)
            }
            config = newConfig
            if (newConfig.enabled && state == null) {
                init()
            }
            log("配置已重新加载")
            showStatus(log)
        } catch (e: Exception) {
            log("配置加载失败: ${e.message}")
            Log.error("[BaiduChatFilter] 配置重新加载失败", e)
        }
    }

    private fun test(args: Array<String>, log: StrCons) {
        if (args.size < 2) {
            log("用法: chatfilter test <文本>")
            return
        }
        if (config.apiKey.isBlank() || config.secretKey.isBlank()) {
            log("未配置 apiKey/secretKey，无法调用百度审核 API")
            return
        }
        val text = args.copyOfRange(1, args.size).joinToString(" ")
        val client = BaiduClient(config.apiKey, config.secretKey, config.timeoutSeconds)
        try {
            log("审核中: $text")
            val result = client.checkText(text)
            if (result == null) {
                log("审核失败(请求异常或响应解析失败)，详见服务端日志")
            } else {
                log("结论类型: ${result.conclusionType} (${result.conclusion})")
                if (result.hits.isNotEmpty()) {
                    log("命中: ${result.hits.joinToString(" | ")}")
                }
                log("按当前配置${if (result.conclusionType in config.blockConclusionTypes) "将拦截" else "将放行"}")
            }
        } finally {
            client.close()
        }
    }

    private fun setEnabled(enabled: Boolean, log: StrCons) {
        config = config.copy(enabled = enabled)
        config.save()
        val s = state
        if (enabled && s == null) {
            init()
        } else if (s != null) {
            s.reload(config)
        }
        log(if (enabled) "已启用" else "已禁用")
    }

    private fun mask(key: String): String {
        if (key.isBlank()) {
            return "(未配置)"
        }
        return if (key.length <= 8) {
            "***"
        } else {
            key.substring(0, 4) + "****" + key.substring(key.length - 4)
        }
    }
}
