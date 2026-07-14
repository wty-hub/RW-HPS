package net.rwhps.plugin.namefilter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NameFilterPatternTest {
    private val defaultPattern = Regex(NameFilterConfig().namePattern)

    @Test
    fun acceptsValidNames() {
        assertTrue(defaultPattern.matches("Player_01"))
        assertTrue(defaultPattern.matches("测试玩家"))
        assertTrue(defaultPattern.matches("abc123"))
        assertTrue(defaultPattern.matches("a".repeat(20)))
    }

    @Test
    fun rejectsInvalidNames() {
        assertFalse(defaultPattern.matches("a"))
        assertFalse(defaultPattern.matches(""))
        assertFalse(defaultPattern.matches("bad name"))
        assertFalse(defaultPattern.matches("name@mail"))
        assertFalse(defaultPattern.matches("a".repeat(21)))
    }

    @Test
    fun customPatternWorks() {
        val tagPattern = Regex("^\\[.+\\].+$")
        assertTrue(tagPattern.matches("[TAG]Player"))
        assertFalse(tagPattern.matches("Player"))
        assertFalse(tagPattern.matches("[TAG]"))
    }
}
