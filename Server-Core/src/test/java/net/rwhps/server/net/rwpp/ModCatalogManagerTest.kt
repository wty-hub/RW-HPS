package net.rwhps.server.net.rwpp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ModCatalogManagerTest {
    @TempDir lateinit var temp: Path

    @Test fun `packs enabled directory with stable safe paths`() {
        val root = Files.createDirectory(temp.resolve("mods")); val cache = temp.resolve("cache")
        val mod = Files.createDirectory(root.resolve("folder-name"))
        Files.writeString(mod.resolve("mod-info.txt"), "[mod]\nauthor: ignored\ntitle = Directory Mod\n")
        Files.createDirectories(mod.resolve("units/sub")); Files.writeString(mod.resolve("units/sub/unit.ini"), "[unit]\nname=test\n")
        val entry = refresh(root, cache, listOf("Directory Mod")).entries.single()
        assertEquals("Directory Mod", entry.logicalName); assertTrue(entry.sha256.matches(Regex("[0-9a-f]{64}")))
        ZipFile(entry.file).use { zip ->
            assertEquals(listOf("mod-info.txt", "units/sub/unit.ini"), zip.entries().asSequence().map { it.name }.toList())
            assertEquals("[unit]\nname=test\n", zip.getInputStream(zip.getEntry("units/sub/unit.ini")).reader().readText())
        }
    }

    @Test fun `references rwmod directly with exact size hash generation and total`() {
        val root = Files.createDirectory(temp.resolve("mods")); val source = root.resolve("direct.rwmod")
        archive(source, "Direct"); val before = ModCatalogManager.snapshot().generation
        val snapshot = refresh(root, temp.resolve("cache"), listOf("Direct")); val entry = snapshot.entries.single()
        assertSame(snapshot, ModCatalogManager.snapshot()); assertTrue(snapshot.generation > before)
        assertEquals(source.toAbsolutePath().normalize(), entry.path); assertEquals(Files.size(source), entry.size)
        assertEquals(sha256(Files.readAllBytes(source)), entry.sha256); assertEquals(entry.size, snapshot.totalSize)
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.entries as MutableList<ModCatalogEntry>).add(entry) }
    }

    @Test fun `parses title separators case and filename fallback while filtering disabled`() {
        val root = Files.createDirectory(temp.resolve("mods")); val cache = temp.resolve("cache")
        archiveWithInfo(root.resolve("colon.rwmod"), "[MOD]\nTiTlE: Name:With=Both\n")
        archiveWithInfo(root.resolve("equal.rwmod"), "[mod]\ntitle = Equal=Value:Tail\n")
        archiveWithInfo(root.resolve("Fallback Name.rwmod"), "[other]\ntitle=Ignored\n")
        archive(root.resolve("disabled.rwmod"), "Disabled")
        assertEquals(listOf("Equal=Value:Tail", "fallback name", "name:with=both"),
            refresh(root, cache, listOf("name:with=both", "Equal=Value:Tail", "fallback name")).entries.map { it.logicalName })
    }

    @Test fun `missing root duplicates and zero byte or oversized sources produce no entries`() {
        val cache = temp.resolve("cache")
        assertTrue(refresh(temp.resolve("missing"), cache, listOf("Anything")).entries.isEmpty())
        val root = Files.createDirectory(temp.resolve("mods")); archive(root.resolve("one.rwmod"), "Duplicate")
        archive(root.resolve("two.zip"), "duplicate"); Files.createFile(root.resolve("empty.rwmod"))
        archive(root.resolve("large.rwmod"), "Large", ByteArray(4096).also { Random(7).nextBytes(it) })
        val result = ModCatalogManager.refresh(root, cache, listOf("Duplicate", "empty", "Large"), ModCatalogLimits(512, 1024 * 1024))
        assertTrue(result.entries.isEmpty())
    }

    @Test fun `rejects traversal absolute backslash and directory symlinks`() {
        val root = Files.createDirectory(temp.resolve("mods")); val cache = temp.resolve("cache")
        unsafeArchive(root.resolve("parent.rwmod"), "Parent", "../escape.txt")
        unsafeArchive(root.resolve("absolute.rwmod"), "Absolute", "/escape.txt")
        unsafeArchive(root.resolve("backslash.rwmod"), "Backslash", "..\\escape.txt")
        val directory = Files.createDirectory(root.resolve("Linked")); Files.writeString(directory.resolve("mod-info.txt"), "[mod]\ntitle=Linked\n")
        Files.createSymbolicLink(directory.resolve("outside.txt"), Files.writeString(temp.resolve("outside.txt"), "secret"))
        assertTrue(refresh(root, cache, listOf("Parent", "Absolute", "Backslash", "Linked")).entries.isEmpty())
        assertFalse(Files.exists(temp.resolve("escape.txt")))
    }

    @Test fun `directory archive order content and cache size limit are enforced`() {
        val root = Files.createDirectory(temp.resolve("mods")); val cache = temp.resolve("cache")
        val mod = Files.createDirectory(root.resolve("Nested")); Files.writeString(mod.resolve("mod-info.txt"), "[mod]\ntitle=Nested\n")
        Files.createDirectories(mod.resolve("z/deep")); Files.createDirectories(mod.resolve("a"))
        Files.writeString(mod.resolve("z/deep/last.txt"), "last"); Files.writeString(mod.resolve("a/first.txt"), "first")
        val entry = refresh(root, cache, listOf("Nested")).entries.single()
        ZipFile(entry.file).use { zip ->
            assertEquals(listOf("a/first.txt", "mod-info.txt", "z/deep/last.txt"), zip.entries().asSequence().map { it.name }.toList())
            assertEquals("first", zip.getInputStream(zip.getEntry("a/first.txt")).reader().readText())
        }
        assertTrue(ModCatalogManager.refresh(root, cache, listOf("Nested"), ModCatalogLimits(1024 * 1024, 32)).entries.isEmpty())
    }

    @Test fun `refresh cleans stale cache unless pinned and release permits cleanup`() {
        val root = Files.createDirectory(temp.resolve("mods")); val cache = temp.resolve("cache")
        val mod = Files.createDirectory(root.resolve("Pinned")); Files.writeString(mod.resolve("mod-info.txt"), "[mod]\ntitle=Pinned\n")
        Files.writeString(mod.resolve("value.txt"), "one"); val first = refresh(root, cache, listOf("Pinned")); val old = first.entries.single().path
        ModCatalogManager.pin(first)
        try { Files.writeString(mod.resolve("value.txt"), "two"); val second = refresh(root, cache, listOf("Pinned")); assertTrue(second.generation > first.generation); assertTrue(Files.exists(old)) }
        finally { ModCatalogManager.release(first) }
        refresh(root, cache, listOf("Pinned")); assertFalse(Files.exists(old))
    }

    private fun refresh(root: Path, cache: Path, enabled: Collection<String>) =
        ModCatalogManager.refresh(root, cache, enabled, ModCatalogLimits(1024 * 1024, 1024 * 1024))
    private fun archive(path: Path, title: String, payload: ByteArray = byteArrayOf(1, 2, 3)) =
        archiveWithInfo(path, "[mod]\ntitle=$title\n", payload)
    private fun archiveWithInfo(path: Path, info: String, payload: ByteArray? = null) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("mod-info.txt")); zip.write(info.toByteArray()); zip.closeEntry()
            payload?.let { zip.putNextEntry(ZipEntry("payload.bin")); zip.write(it); zip.closeEntry() }
        }
    }
    private fun unsafeArchive(path: Path, title: String, unsafeName: String) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("mod-info.txt")); zip.write("[mod]\ntitle=$title\n".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry(unsafeName)); zip.write(1); zip.closeEntry()
        }
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
