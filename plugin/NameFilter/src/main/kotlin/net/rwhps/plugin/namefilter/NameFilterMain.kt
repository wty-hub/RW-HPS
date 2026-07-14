package net.rwhps.plugin.namefilter

import net.rwhps.server.func.StrCons
import net.rwhps.server.game.event.EventManage
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.plugin.api.AdminPassword
import net.rwhps.server.util.game.command.CommandHandler
import net.rwhps.server.util.log.Log

class NameFilterMain : Plugin() {
    private lateinit var config: NameFilterConfig
    private lateinit var state: NameFilterState

    override fun onEnable() {
        config = NameFilterConfig.get(pluginDataFileUtils.toFile("NameFilterConfig.json"))
        config.save()
    }

    override fun registerCoreCommands(handler: CommandHandler) {
        handler.register("namefilter", "[args...]", "namefilter") { args: Array<String>, log: StrCons ->
            when (args.getOrNull(0)) {
                null, "status" -> showStatus(log)
                "setpattern" -> setPattern(args, log)
                else -> log("用法: namefilter [status] | namefilter setpattern <密码> <正则>")
            }
        }
    }

    override fun registerEvents(eventManage: EventManage) {
        if (!config.enabled) {
            Log.clog("[NameFilter] 插件已禁用 (enabled=false)")
            return
        }

        val pattern = try {
            Regex(config.namePattern)
        } catch (e: Exception) {
            Log.error("[NameFilter] 正则表达式无效: ${config.namePattern}", e)
            return
        }

        state = NameFilterState(config, pattern)
        eventManage.registerListener(NameFilterEvent(state))
        Log.clog("[NameFilter] 已启用，昵称正则: ${config.namePattern}")
    }

    private fun showStatus(log: StrCons) {
        log("NameFilter 状态:")
        log("  启用: ${config.enabled}")
        log("  昵称正则: ${config.namePattern}")
        log("  踢出消息: ${config.kickMessage}")
        log("  踢出时长(秒): ${config.kickDurationSeconds}")
        log("修改正则: namefilter setpattern <密码> <正则>")
    }

    private fun setPattern(args: Array<String>, log: StrCons) {
        if (args.size < 3) {
            log("用法: namefilter setpattern <密码> <正则>")
            return
        }
        if (!::state.isInitialized) {
            log("NameFilter 未启用，无法修改正则 (请检查 enabled 与当前正则是否有效)")
            return
        }
        if (!AdminPassword.isAvailable()) {
            log("Password 插件未加载，无法校验密码")
            return
        }
        if (!AdminPassword.isConfigured()) {
            log("尚未设置管理员密码，请先用 setadminpassword 设置")
            return
        }

        val password = args[1]
        val newPatternText = args.copyOfRange(2, args.size).joinToString(" ")

        if (!AdminPassword.verify(password)) {
            log("管理员密码错误")
            return
        }

        val newPattern = try {
            Regex(newPatternText)
        } catch (e: Exception) {
            log("正则表达式无效: $newPatternText")
            return
        }

        config.coverField("namePattern", newPatternText)
        config.save()
        state.pattern = newPattern
        log("昵称正则已更新: $newPatternText")
        Log.clog("[NameFilter] 昵称正则已通过密码验证更新: $newPatternText")
    }
}
