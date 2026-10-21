package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.AppDatabase
import org.json.JSONObject

/** Full app snapshot. ZIP is intentionally editable; it may contain provider credentials. */
class BackupArchiveRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        private const val FORMAT = "rpclient-backup"
        private const val VERSION = 1
        private const val DATABASE_NAME = "primary.sqlite"
        private const val MAX_ENTRIES = 50_000
        private const val MAX_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024
        private const val BUFFER_SIZE = 32 * 1024
    }

    suspend fun exportArchive(destination: Uri) = withContext(Dispatchers.IO) {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        require(databaseFile.isFile) { "The application database is missing." }
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: error("Could not open the selected destination.")
        output.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                addFile(zip, databaseFile, "database/$DATABASE_NAME")
                addDirectory(zip, File(context.dataDir, "shared_prefs"), "settings/shared_prefs")
                addDirectory(
                    zip,
                    context.getDir("repository", Context.MODE_PRIVATE),
                    "files/repository",
                    excludedDirectoryNames = setOf("staging")
                )
                val manifest = JSONObject()
                    .put("format", FORMAT)
                    .put("version", VERSION)
                    .put("createdAt", System.currentTimeMillis())
                    .put("databaseSha256", sha256(databaseFile))
                    .put("notes", "Editable ZIP snapshot; may contain API keys and private data.")
                    .toString(2)
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.finish()
            }
        }
    }

    /** Validate and stage the archive before replacing live files; keep a rollback snapshot. */
    suspend fun restoreArchive(source: Uri) = withContext(Dispatchers.IO) {
        val stageRoot = File(context.cacheDir, "backup-restore-" + System.nanoTime())
        val rollbackRoot = File(context.cacheDir, "backup-rollback-" + System.nanoTime())
        stageRoot.mkdirs()
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: error("Could not read the selected archive.")
            input.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entryCount = 0
                    var totalBytes = 0L
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entryCount++
                        require(entryCount <= MAX_ENTRIES) { "The archive contains too many files." }
                        val name = entry.name
                        require(isSafeArchivePath(name)) { "Unsafe or unsupported archive path: $name" }
                        if (!entry.isDirectory) {
                            val target = File(stageRoot, name).canonicalFile
                            val root = stageRoot.canonicalFile
                            require(target.path.startsWith(root.path + File.separator)) {
                                "Unsafe archive path: $name"
                            }
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    totalBytes += count
                                    require(totalBytes <= MAX_UNCOMPRESSED_BYTES) {
                                        "The archive exceeds the 2 GiB restore limit."
                                    }
                                    output.write(buffer, 0, count)
                                }
                                output.fd.sync()
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }

            val manifestFile = File(stageRoot, "manifest.json")
            require(manifestFile.isFile) { "This ZIP is missing manifest.json." }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.optString("format") == FORMAT) { "This is not an RPClient backup." }
            require(manifest.optInt("version", -1) == VERSION) {
                "Unsupported backup format version: " + manifest.optInt("version", -1)
            }

            val stagedDatabase = File(stageRoot, "database/$DATABASE_NAME")
            require(stagedDatabase.isFile) { "The archive is missing database/$DATABASE_NAME." }
            require(stagedDatabase.inputStream().use { inputStream ->
                val header = ByteArray(16)
                inputStream.read(header) == 16 && String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
            }) { "The backup database is not a valid SQLite database." }
            require(sha256(stagedDatabase).equals(manifest.optString("databaseSha256"), ignoreCase = true)) {
                "The database checksum does not match manifest.json."
            }

            val stagedPrefs = File(stageRoot, "settings/shared_prefs")
            val stagedRepository = File(stageRoot, "files/repository")
            val liveDatabase = context.getDatabasePath(DATABASE_NAME)
            val livePrefs = File(context.dataDir, "shared_prefs")
            val liveRepository = context.getDir("repository", Context.MODE_PRIVATE)
            rollbackRoot.mkdirs()
            copyFileIfExists(liveDatabase, File(rollbackRoot, "database/$DATABASE_NAME"))
            copyDirectoryIfExists(livePrefs, File(rollbackRoot, "settings/shared_prefs"))
            copyDirectoryIfExists(liveRepository, File(rollbackRoot, "files/repository"))

            database.close()
            try {
                replaceFile(stagedDatabase, liveDatabase)
                deleteDatabaseSidecars(liveDatabase)
                replaceDirectory(stagedPrefs, livePrefs)
                replaceDirectory(stagedRepository, liveRepository)
            } catch (error: Throwable) {
                restoreRollback(rollbackRoot, liveDatabase, livePrefs, liveRepository)
                throw BackupRestoreException(
                    "Restore failed. Previous data was recovered; restart the app to reopen its database.",
                    restartRequired = true,
                    cause = error
                )
            }
        } finally {
            stageRoot.deleteRecursively()
            rollbackRoot.deleteRecursively()
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, archivePath: String) {
        zip.putNextEntry(ZipEntry(archivePath))
        FileInputStream(file).use { input -> input.copyTo(zip, BUFFER_SIZE) }
        zip.closeEntry()
    }

    private fun addDirectory(
        zip: ZipOutputStream,
        directory: File,
        archivePrefix: String,
        excludedDirectoryNames: Set<String> = emptySet()
    ) {
        if (!directory.isDirectory) return
        directory.walkTopDown()
            .filter { it.isFile && it.relativeTo(directory).path.split(File.separator).none(excludedDirectoryNames::contains) }
            .forEach { file ->
                val relative = file.relativeTo(directory).invariantSeparatorsPath
                addFile(zip, file, "$archivePrefix/$relative")
            }
    }

    private fun isSafeArchivePath(name: String): Boolean {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\") || name.contains('\\')) return false
        // ZIP tools commonly add explicit directory entries with a trailing slash.
        val normalized = name.removeSuffix("/")
        val parts = normalized.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return false
        return normalized == "manifest.json" ||
            normalized == "database" || normalized.startsWith("database/") ||
            normalized == "settings" || normalized == "settings/shared_prefs" ||
            normalized.startsWith("settings/shared_prefs/") ||
            normalized == "files" || normalized == "files/repository" ||
            normalized.startsWith("files/repository/")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyFileIfExists(source: File, destination: File) {
        if (!source.isFile) return
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = true)
    }

    private fun copyDirectoryIfExists(source: File, destination: File) {
        if (!source.isDirectory) return
        source.walkTopDown().forEach { file ->
            val target = File(destination, file.relativeTo(source).invariantSeparatorsPath)
            if (file.isDirectory) target.mkdirs()
            else {
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }
        }
    }

    private fun replaceFile(source: File, destination: File) {
        require(source.isFile) { "The staged database is missing." }
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, destination.name + ".restore.tmp")
        source.copyTo(temporary, overwrite = true)
        if (destination.exists() && !destination.delete()) error("Could not replace the application database.")
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    private fun replaceDirectory(source: File, destination: File) {
        val temporary = File(destination.parentFile, destination.name + ".restore.tmp")
        temporary.deleteRecursively()
        if (source.isDirectory) copyDirectoryIfExists(source, temporary) else temporary.mkdirs()
        if (destination.exists() && !destination.deleteRecursively()) {
            error("Could not replace " + destination.name + ".")
        }
        if (!temporary.renameTo(destination)) {
            if (!temporary.isDirectory) destination.mkdirs()
            else copyDirectoryIfExists(temporary, destination)
            temporary.deleteRecursively()
        }
    }

    private fun deleteDatabaseSidecars(databaseFile: File) {
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-journal").delete()
    }

    private fun restoreRollback(root: File, databaseFile: File, prefs: File, repository: File) {
        val oldDatabase = File(root, "database/$DATABASE_NAME")
        if (oldDatabase.isFile) replaceFile(oldDatabase, databaseFile)
        deleteDatabaseSidecars(databaseFile)
        replaceDirectory(File(root, "settings/shared_prefs"), prefs)
        replaceDirectory(File(root, "files/repository"), repository)
    }
}

class BackupRestoreException(
    message: String,
    val restartRequired: Boolean,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
