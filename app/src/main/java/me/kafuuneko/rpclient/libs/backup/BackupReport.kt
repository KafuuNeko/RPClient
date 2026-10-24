package me.kafuuneko.rpclient.libs.backup

/**
 * Technical log for backup and restore operations.
 * The UI can show a concise status separately from this complete, copyable diagnostic detail.
 * Logs contain archive-relative paths, table names, and counts, not conversation contents or credentials.
 */
class BackupReport {
    private val mLines = mutableListOf<String>()

    /** Record progress for a named operation or dataset. */
    @Synchronized
    fun info(message: String) {
        mLines += "[INFO] $message"
    }

    /** Record recoverable conditions, such as skipped files or removed orphan rows. */
    @Synchronized
    fun warn(message: String) {
        mLines += "[WARN] $message"
    }

    /** Return all recorded log lines. */
    @Synchronized
    fun text(): String = mLines.joinToString("\n")

    /**
     * Return a concise user-facing status for common failures.
     * Keep implementation details and stack frames in [textWithError] instead.
     */
    fun statusMessage(error: Throwable, operation: String = "Backup/restore"): String {
        val chain = generateSequence(error) { it.cause }.toList()
        val messages = chain.mapNotNull { it.message }
        val fullMessage = messages.joinToString(" | ")
        val stage = messages.firstNotNullOfOrNull { message ->
            Regex("""(?:Export failed while writing|Restore failed while reading) ([^:]+):""")
                .find(message)?.groupValues?.get(1)
        }

        return when {
            fullMessage.contains("fields do not match database columns", ignoreCase = true) ||
                fullMessage.contains("Room entity field names must be kept by R8", ignoreCase = true) ->
                "$operation gagal: nama field database tidak dipertahankan oleh konfigurasi R8. Pastikan aturan ada di app/proguard-rules.pro lalu build ulang varian release."
            chain.any { it is SecurityException } ->
                "$operation gagal: akses ke berkas atau lokasi tujuan ditolak. Periksa izin dan pilih lokasi yang dapat ditulis."
            chain.any { it is java.io.IOException } ->
                "$operation gagal saat membaca atau menulis berkas. Periksa ruang penyimpanan, akses lokasi, dan kondisi ZIP."
            fullMessage.contains("Unsupported portable backup version", ignoreCase = true) ->
                "$operation gagal: versi format backup tidak didukung oleh aplikasi ini."
            fullMessage.contains("manifest", ignoreCase = true) ||
                fullMessage.contains("not an RPClient portable data backup", ignoreCase = true) ||
                fullMessage.contains("ZIP", ignoreCase = true) ->
                "$operation gagal: arsip backup tidak valid atau tidak lengkap. Periksa manifest dan isi ZIP."
            fullMessage.contains("SQLite", ignoreCase = true) ||
                fullMessage.contains("database", ignoreCase = true) ||
                fullMessage.contains("foreign key", ignoreCase = true) ->
                "$operation gagal saat memproses database. Detail validasi tersedia di log teknis."
            stage != null ->
                "$operation gagal pada tahap '$stage'. Buka detail teknis untuk melihat item dan penyebab spesifik."
            else ->
                "$operation gagal. Buka detail teknis untuk melihat penyebab spesifik."
        }
    }

    /**
     * Append every exception frame without the JVM's "... N more" compression.
     * The result is suitable for an expandable, selectable/copyable diagnostic block.
     */
    fun textWithError(error: Throwable): String {
        return text() + "\n\n[ERROR DETAILS]\n" + buildString {
            appendThrowable(error, prefix = "", seen = java.util.Collections.newSetFromMap(
                java.util.IdentityHashMap<Throwable, Boolean>()
            ))
        }
    }

    private fun StringBuilder.appendThrowable(
        error: Throwable,
        prefix: String,
        seen: MutableSet<Throwable>
    ) {
        if (!seen.add(error)) {
            append(prefix).append("[CIRCULAR EXCEPTION REFERENCE]\n")
            return
        }

        append(prefix)
            .append(error.javaClass.name)
            .append(error.message?.let { ": $it" }.orEmpty())
            .append('\n')

        error.stackTrace.forEach { frame ->
            append(prefix).append("\tat ").append(frame).append('\n')
        }

        error.suppressed.forEach { suppressed ->
            appendThrowable(suppressed, "$prefix\tSuppressed: ", seen)
        }

        error.cause?.let { cause ->
            appendThrowable(cause, "Caused by: ", seen)
        }
    }
}
