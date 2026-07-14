package net.rwhps.plugin.password

import net.rwhps.server.util.algorithms.digest.DigestUtils
import net.rwhps.server.util.file.FileUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PasswordVerifierTest {
    @TempDir
    lateinit var tempDir: Path

    private fun newVerifier(): PasswordVerifierImpl {
        val configFile = tempDir.resolve("PasswordConfig.json").toFile()
        configFile.writeText("{}")
        val fileUtils = FileUtils.getFile(configFile.absolutePath)
        val config = PasswordConfig.get(fileUtils)
        config.save()
        return PasswordVerifierImpl(fileUtils, config)
    }

    @Test
    fun notConfiguredByDefault() {
        val verifier = newVerifier()
        assertFalse(verifier.isConfigured())
        assertFalse(verifier.verify("anything"))
    }

    @Test
    fun setAndVerifyPassword() {
        val verifier = newVerifier()
        verifier.setPassword("secret123")

        assertTrue(verifier.isConfigured())
        assertTrue(verifier.verify("secret123"))
        assertFalse(verifier.verify("wrong"))
        assertFalse(verifier.verify(""))
    }

    @Test
    fun clearPassword() {
        val verifier = newVerifier()
        verifier.setPassword("secret123")
        verifier.clearPassword()

        assertFalse(verifier.isConfigured())
        assertFalse(verifier.verify("secret123"))
    }

    @Test
    fun passwordHashUsesSalt() {
        val verifier = newVerifier()
        verifier.setPassword("same")

        val firstSalt = verifier.currentConfig().salt
        val firstHash = verifier.currentConfig().passwordHash

        verifier.setPassword("same")
        val secondSalt = verifier.currentConfig().salt
        val secondHash = verifier.currentConfig().passwordHash

        assertTrue(firstSalt.isNotBlank())
        assertTrue(secondSalt.isNotBlank())
        assertTrue(firstHash.isNotBlank())
        assertTrue(secondHash.isNotBlank())
        assertEquals(
            DigestUtils.sha256Hex(firstSalt + "same"),
            firstHash,
        )
        assertEquals(
            DigestUtils.sha256Hex(secondSalt + "same"),
            secondHash,
        )
    }

    @Test
    fun configPersistsToFile() {
        val configFile = tempDir.resolve("PasswordConfig.json").toFile()
        configFile.writeText("{}")
        val fileUtils = FileUtils.getFile(configFile.absolutePath)

        val verifier = PasswordVerifierImpl(fileUtils, PasswordConfig.get(fileUtils).also { it.save() })
        verifier.setPassword("persist-me")

        val reloaded = PasswordVerifierImpl(fileUtils, PasswordConfig.get(fileUtils))
        assertTrue(reloaded.isConfigured())
        assertTrue(reloaded.verify("persist-me"))
        assertFalse(reloaded.verify("wrong"))
    }
}
