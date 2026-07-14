package net.rwhps.plugin.password

import net.rwhps.server.data.bean.AbstractBeanConfig
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.inline.toGson

data class PasswordConfig(
    val salt: String = "",
    val passwordHash: String = "",
) : AbstractBeanConfig(this::class.java, "") {
    val isConfigured: Boolean
        get() = salt.isNotBlank() && passwordHash.isNotBlank()

    companion object {
        fun get(fileUtils: FileUtils): PasswordConfig {
            val config: PasswordConfig = PasswordConfig::class.java.toGson(fileUtils.readFileStringData())
            config.bindFile(fileUtils)
            config.readProperty()
            return config
        }
    }
}
