package net.rwhps.plugin.roomreset

import net.rwhps.server.func.StrCons
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.plugin.api.AdminPassword
import net.rwhps.server.util.game.command.CommandHandler

class RoomResetMain : Plugin() {
    override fun registerCoreCommands(handler: CommandHandler) {
        handler.register("resetroom", "<password>", "#将房间设置恢复为原版默认值") { args: Array<String>, log: StrCons ->
            val error = validateReset(args)
            if (error != null) {
                log(error)
                return@register
            }

            val gameModule = HeadlessModuleManage.hps
            gameModule.gameLinkServerData.resetRoomToDefaults()
            gameModule.room.call.sendSystemMessage(SUCCESS_MESSAGE)
            log(SUCCESS_MESSAGE)
        }
    }

    internal fun validateReset(args: Array<String>): String? {
        if (args.size != 1) {
            return USAGE
        }
        if (!HeadlessModuleManage.initHPS()) {
            return "游戏服务器尚未启动"
        }
        if (HeadlessModuleManage.hps.room.isStartGame) {
            return "游戏已开始，无法重置房间设置"
        }
        if (!AdminPassword.isAvailable()) {
            return "Password 插件未加载，无法校验密码"
        }
        if (!AdminPassword.isConfigured()) {
            return "尚未设置管理员密码，请先用 setadminpassword 设置"
        }
        if (!AdminPassword.verify(args[0])) {
            return "管理员密码错误"
        }
        return null
    }

    companion object {
        internal const val USAGE = "用法: resetroom <密码>"
        private const val SUCCESS_MESSAGE = "房间设置已恢复为原版默认值"
    }
}
