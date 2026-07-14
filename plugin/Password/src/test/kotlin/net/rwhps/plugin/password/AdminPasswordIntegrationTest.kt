package net.rwhps.plugin.password

import net.rwhps.server.plugin.api.AdminPassword
import net.rwhps.server.util.file.FileUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AdminPasswordIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var verifier: PasswordVerifierImpl

    @BeforeEach
    fun setUp() {
        val configFile = tempDir.resolve("PasswordConfig.json").toFile()
        configFile.writeText("{}")
        val fileUtils = FileUtils.getFile(configFile.absolutePath)
        val config = PasswordConfig.get(fileUtils)
        config.save()
        verifier = PasswordVerifierImpl(fileUtils, config)
        AdminPassword.register(verifier)
    }

    @AfterEach
    fun tearDown() {
        AdminPassword.unregister(verifier)
    }

    @Test
    fun registryReflectsPluginLifecycle() {
        assertTrue(AdminPassword.isAvailable())
        assertFalse(AdminPassword.isConfigured())
        assertFalse(AdminPassword.verify("anything"))

        verifier.setPassword("admin-pass")
        assertTrue(AdminPassword.isConfigured())
        assertTrue(AdminPassword.verify("admin-pass"))
        assertFalse(AdminPassword.verify("wrong"))

        AdminPassword.unregister(verifier)
        assertFalse(AdminPassword.isAvailable())
        assertFalse(AdminPassword.verify("admin-pass"))
    }
}
