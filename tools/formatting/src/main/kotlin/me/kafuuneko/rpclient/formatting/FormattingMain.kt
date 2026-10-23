package me.kafuuneko.rpclient.formatting

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** Gradle 与命令行共用入口，检查模式不写文件。 */
fun main(arguments: Array<String>) {
    val exit =
        try {
            runFormatting(FormattingOptions.parse(arguments), ::println)
        } catch (exception: Exception) {
            System.err.println("Formatting failed: ${exception.message}")
            2
        }
    exitProcess(exit)
}

/** 全部文件完成原生格式化和稳定性检查后才写入，写入前核对原文以检测并发编辑。 */
internal fun runFormatting(
    options: FormattingOptions,
    report: (String) -> Unit,
    formatter: SourceFormatter = AndroidStudioFormatter(options.studioHome, report),
): Int {
    val root = options.root.toRealPath()
    val paths = SourceFiles(root).select(options)
    if (paths.isEmpty()) {
        report("No Kotlin sources selected.")
        return 0
    }

    // UTF-8 解码不允许替换坏字节；任何文件失败时，其他文件也不应被提前写入。
    val sources = paths.map { path ->
        val bytes = Files.readAllBytes(path)
        validateUtf8(bytes)
        SourceSnapshot(path, bytes)
    }
    val formatted = formatter.format(root, sources)
    check(formatted.size == sources.size) { "Formatter did not return every selected source" }
    val changes = sources.zip(formatted).mapNotNull { (source, result) ->
        validateUtf8(result)
        if (source.before.contentEquals(result)) null else SourceChange(
            source.path,
            source.before,
            result
        )
    }
    changes.forEach {
        report(
            root
                .relativize(it.path)
                .toString()
                .replace('\\', '/'),
        )
    }
    if (options.mode == "check") {
        report("Checked ${paths.size} Kotlin source(s); ${changes.size} need formatting.")
        return if (changes.isEmpty()) 0 else 1
    }

    // 全部并发编辑检查也在首次写入之前完成；原位写入保留文件权限和 ACL。
    changes.forEach { change ->
        check(Files.readAllBytes(change.path).contentEquals(change.before)) {
            "Source changed while formatting: ${root.relativize(change.path)}"
        }
        check(Files.isWritable(change.path)) { "Source is not writable: ${root.relativize(change.path)}" }
    }
    changes.forEach { Files.write(it.path, it.after) }
    report("Formatted ${changes.size} of ${paths.size} Kotlin source(s).")
    return 0
}

private fun validateUtf8(bytes: ByteArray) {
    UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
}

internal data class SourceSnapshot(val path: Path, val before: ByteArray)

internal fun interface SourceFormatter {
    fun format(root: Path, sources: List<SourceSnapshot>): List<ByteArray>
}

private data class SourceChange(
    val path: Path,
    val before: ByteArray,
    val after: ByteArray,
)
