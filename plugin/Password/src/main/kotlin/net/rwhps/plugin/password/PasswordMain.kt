package net.rwhps.plugin.password

import net.rwhps.server.func.StrCons
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.plugin.api.AdminPassword
import net.rwhps.server.util.game.command.CommandHandler
import net.rwhps.server.util.log.Log

class PasswordMain : Plugin() {
    private lateinit var verifier: PasswordVerifierImpl

    override fun onEnable() {
        val configFile = pluginDataFileUtils.toFile("PasswordConfig.json")
        val config = PasswordConfig.get(configFile)
        if (!config.isConfigured) {
            config.save()
        }
        verifier = PasswordVerifierImpl(configFile, config)
    }

    override fun registerCoreCommands(handler: CommandHandler) {
        handler.register("setadminpassword", "<password...>", "password.set") { args: Array<String>, log: StrCons ->
            if (args.isEmpty()) {
                log("用法: setadminpassword <密码>")
                return@register
            }
            val password = args.joinToString(" ")
            try {
                verifier.setPassword(password)
                log("管理员密码已更新")
            } catch (e: Exception) {
                log("设置失败: ${e.message}")
            }
        }

        handler.register("clearadminpassword", "password.clear") { _: Array<String>, log: StrCons ->
            verifier.clearPassword()
            log("管理员密码已清除")
        }

        handler.register("adminpassword", "[status]", "password.status") { args: Array<String>, log: StrCons ->
            when (args.getOrNull(0)) {
                null, "status" -> {
                    if (verifier.isConfigured()) {
                        log("管理员密码: 已设置")
                    } else {
                        log("管理员密码: 未设置 (使用 setadminpassword 设置)")
                    }
                }
                else -> log("用法: adminpassword [status]")
            }
        }
    }

    override fun init() {
        AdminPassword.register(verifier)
        if (verifier.isConfigured()) {
            Log.clog("[Password] 管理员密码服务已注册 (已配置密码)")
        } else {
            Log.clog("[Password] 管理员密码服务已注册 (尚未设置密码，请使用 setadminpassword)")
        }
    }

    override fun onDisable() {
        AdminPassword.unregister(verifier)
    }
}
