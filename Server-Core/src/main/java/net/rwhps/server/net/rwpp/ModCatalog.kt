/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package net.rwhps.server.net.rwpp

import net.rwhps.server.data.global.Data
import net.rwhps.server.game.manage.ModManage
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.log.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ModCatalogEntry(
    val logicalName: String,
    val path: Path,
    val size: Long,
    val sha256: String,
) {
    val file: File get() = path.toFile()
}

data class ModCatalogSnapshot(
    val generation: Long,
    val entries: List<ModCatalogEntry>,
) {
    val totalSize: Long = entries.fold(0L) { total, entry -> Math.addExact(total, entry.size) }
    val isValid: Boolean get() = generation > 0

    companion object {
        val EMPTY = ModCatalogSnapshot(0, emptyList())
    }
}

data class ModCatalogLimits(
    val maxModSize: Long,
    val maxArchiveCacheSize: Long,
) {
    init {
        require(maxModSize > 0)
        require(maxArchiveCacheSize > 0)
    }
}

/** Builds immutable, atomically published catalogs outside connection handling. */
object ModCatalogManager {
    private const val INFO_FILE = "mod-info.txt"
    private const val CORE_NAME = "RW-HPS CoreUnits"
    private val generation = AtomicLong()
    private val current = AtomicReference(ModCatalogSnapshot.EMPTY)
    private val pinned = mutableMapOf<Long, Pair<Int, Set<Path>>>()

    @JvmStatic
    fun snapshot(): ModCatalogSnapshot = current.get()

    @JvmStatic
    @Synchronized
    fun refresh(): ModCatalogSnapshot {
        val config = Data.configServer
        val mb = 1024L * 1024L
        return refresh(
            FileUtils.getFolder(Data.Plugin_Mods_Path).file.toPath(),
            FileUtils.getFolder(Data.ServerCachePath).file.toPath().resolve("mod-transfer"),
            ModManage.getModsList().filter { it != CORE_NAME },
            ModCatalogLimits(config.maxModTransferSizeMb * mb, config.modTransferArchiveCacheSizeMb * mb),
        )
    }

    @JvmStatic
    @Synchronized
    fun refresh(root: Path, cacheDirectory: Path, enabledNames: Collection<String>, limits: ModCatalogLimits): ModCatalogSnapshot {
        val nextGeneration = generation.incrementAndGet()
        val entries = build(root, cacheDirectory, enabledNames, limits, nextGeneration)
        val snapshot = ModCatalogSnapshot(nextGeneration, Collections.unmodifiableList(entries))
        current.set(snapshot)
        removeOldCacheFiles(cacheDirectory, entries.map { it.path }.toSet() + pinned.values.flatMap { it.second })
        return snapshot
    }

    @JvmStatic
    fun invalidate() {
        current.set(ModCatalogSnapshot.EMPTY)
    }

    @Synchronized
    internal fun pin(snapshot: ModCatalogSnapshot) {
        if (!snapshot.isValid) return
        val old = pinned[snapshot.generation]
        pinned[snapshot.generation] = (old?.first ?: 0) + 1 to snapshot.entries.map { it.path }.toSet()
    }

    @Synchronized
    internal fun release(snapshot: ModCatalogSnapshot) {
        val old = pinned[snapshot.generation] ?: return
        if (old.first <= 1) pinned.remove(snapshot.generation) else pinned[snapshot.generation] = old.first - 1 to old.second
    }

    private data class Candidate(val source: Path, val logicalName: String, val fallbackName: String, val directory: Boolean)

    private fun build(root: Path, cache: Path, enabledNames: Collection<String>, limits: ModCatalogLimits, generation: Long): List<ModCatalogEntry> {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        Files.createDirectories(cache)
        val candidates = Files.newDirectoryStream(root).use { stream ->
            stream.mapNotNull { path ->
                try {
                    candidate(path)
                } catch (e: Exception) {
                    Log.warn("[MODSYNC-HPS] Skip unsafe or unreadable mod source: $path; ${e.message}")
                    null
                }
            }.toList()
        }
        val enabledByKey = enabledNames.groupBy(::nameKey).filterValues { it.size == 1 }
        val matched = candidates.mapNotNull { candidate ->
            val names = listOf(candidate.logicalName, candidate.fallbackName).distinct().map(::nameKey)
            val matches = names.mapNotNull { enabledByKey[it]?.singleOrNull() }.distinct()
            if (matches.size == 1) candidate to matches.single() else null
        }
        val uniqueSources = matched.groupBy { nameKey(it.second) }.filterValues { it.size == 1 }.values.map { it.single() }

        var cachedSize = 0L
        return uniqueSources.sortedBy { nameKey(it.second) }.mapNotNull { (candidate, enabledName) ->
            try {
                val path = if (candidate.directory) {
                    val target = cache.resolve("$generation-${safeFileName(enabledName)}.zip")
                    packDirectory(candidate.source, target, limits.maxModSize)
                    cachedSize = Math.addExact(cachedSize, Files.size(target))
                    if (cachedSize > limits.maxArchiveCacheSize) {
                        Files.deleteIfExists(target)
                        return@mapNotNull null
                    }
                    target
                } else candidate.source
                val size = Files.size(path)
                if (size <= 0 || size > limits.maxModSize) {
                    if (candidate.directory) Files.deleteIfExists(path)
                    null
                } else ModCatalogEntry(enabledName, path.toAbsolutePath().normalize(), size, sha256(path))
            } catch (e: Exception) {
                Log.warn("[MODSYNC-HPS] Skip unsafe or unreadable mod source: ${candidate.source}; ${e.message}")
                null
            }
        }
    }

    private fun candidate(path: Path): Candidate? {
        if (Files.isSymbolicLink(path)) return null
        val fallback = fileBaseName(path.fileName.toString())
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            val info = path.resolve(INFO_FILE)
            if (!Files.isRegularFile(info, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(info)) return null
            return Candidate(path, readTitle(Files.newInputStream(info)) ?: fallback, fallback, true)
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
        val extension = path.fileName.toString().substringAfterLast('.', "").lowercase()
        if (extension != "rwmod" && extension != "zip") return null
        return Candidate(path, readArchiveTitle(path) ?: fallback, fallback, false)
    }

    private fun readArchiveTitle(path: Path): String? = ZipFile(path.toFile()).use { zip ->
        val entries = zip.entries().asSequence().toList()
        if (entries.any { !safeZipName(it.name) }) throw IOException("unsafe ZIP entry")
        val infoEntries = entries.filter { it.name.replace('\\', '/') == INFO_FILE }
        if (infoEntries.size != 1) null else readTitle(zip.getInputStream(infoEntries.single()))
    }

    private fun readTitle(input: InputStream): String? = input.use {
        var inModSection = false
        it.bufferedReader(Charsets.UTF_8).lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
                inModSection = trimmed.equals("[mod]", ignoreCase = true)
            } else if (inModSection) {
                val separator = listOf(trimmed.indexOf('='), trimmed.indexOf(':')).filter { index -> index >= 0 }.minOrNull() ?: -1
                if (separator > 0 && trimmed.substring(0, separator).trim().equals("title", ignoreCase = true)) {
                    return trimmed.substring(separator + 1).trim().takeIf(String::isNotEmpty)
                }
            }
        }
        null
    }

    private fun packDirectory(root: Path, target: Path, maxSize: Long) {
        val canonicalRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS)
        val temporary = Files.createTempFile(target.parent, "mod-", ".tmp")
        try {
            var written = 0L
            ZipOutputStream(BufferedOutputStream(Files.newOutputStream(temporary))).use { zip ->
                Files.walk(canonicalRoot).use { paths ->
                    paths.filter { it != canonicalRoot }.sorted().forEach { path ->
                        if (Files.isSymbolicLink(path)) throw IOException("symbolic links are forbidden")
                        val real = path.toRealPath(LinkOption.NOFOLLOW_LINKS)
                        if (!real.startsWith(canonicalRoot)) throw IOException("path escapes mod root")
                        if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) return@forEach
                        val relative = canonicalRoot.relativize(real).joinToString("/") { it.toString() }
                        if (!safeZipName(relative)) throw IOException("unsafe archive path")
                        written = Math.addExact(written, Files.size(real))
                        if (written > maxSize) throw IOException("mod exceeds size limit")
                        zip.putNextEntry(ZipEntry(relative).apply { time = 0L })
                        BufferedInputStream(Files.newInputStream(real)).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            if (Files.size(temporary) > maxSize) throw IOException("archive exceeds size limit")
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(Files.newInputStream(path)).use { input ->
            val buffer = ByteArray(RwppConstants.CHUNK_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun removeOldCacheFiles(cache: Path, retained: Set<Path>) {
        if (!Files.isDirectory(cache)) return
        Files.newDirectoryStream(cache).use { stream ->
            stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && it !in retained }.forEach {
                runCatching { Files.deleteIfExists(it) }
            }
        }
    }

    private fun safeZipName(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        return normalized.isNotBlank() && !normalized.startsWith('/') &&
            normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    private fun fileBaseName(name: String): String = name.substringBeforeLast('.', name).trim()
    private fun nameKey(name: String): String = name.trim().lowercase()
    private fun safeFileName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifEmpty { "mod" }
}
