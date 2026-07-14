package net.rwhps.plugin.namefilter

import net.rwhps.server.data.bean.AbstractBeanConfig
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.inline.toGson

data class NameFilterConfig(
    val enabled: Boolean = true,
    val namePattern: String = "^[\\w\\u4e00-\\u9fa5]{2,20}$",
    val kickMessage: String = "您的昵称不符合服务器要求，请修改后重试",
    val kickDurationSeconds: Int = 0,
) : AbstractBeanConfig(this::class.java, "") {
    companion object {
        fun get(fileUtils: FileUtils): NameFilterConfig {
            val config: NameFilterConfig = NameFilterConfig::class.java.toGson(fileUtils.readFileStringData())
            config.bindFile(fileUtils)
            config.readProperty()
            return config
        }
    }
}
