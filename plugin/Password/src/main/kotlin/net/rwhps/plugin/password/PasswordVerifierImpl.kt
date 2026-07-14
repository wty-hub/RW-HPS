package net.rwhps.plugin.password

import net.rwhps.server.plugin.api.AdminPasswordVerifier
import net.rwhps.server.util.algorithms.digest.DigestUtils
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.math.RandomUtils

class PasswordVerifierImpl(
    private val configFile: FileUtils,
    private var config: PasswordConfig,
) : AdminPasswordVerifier {
    override fun isConfigured(): Boolean = config.isConfigured

    override fun verify(password: String): Boolean {
        if (!config.isConfigured || password.isEmpty()) {
            return false
        }
        return hash(config.salt, password) == config.passwordHash
    }

    fun setPassword(password: String) {
        require(password.isNotBlank()) { "password must not be blank" }
        val salt = RandomUtils.getRandomString(32)
        config = PasswordConfig(salt = salt, passwordHash = hash(salt, password)).apply {
            bindFile(configFile)
            save()
        }
    }

    fun clearPassword() {
        config = PasswordConfig().apply {
            bindFile(configFile)
            save()
        }
    }

    fun currentConfig(): PasswordConfig = config

    private fun hash(salt: String, password: String): String {
        return DigestUtils.sha256Hex(salt + password)
    }
}
