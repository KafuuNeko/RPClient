package me.kafuuneko.rpclient.formatting

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** 原生引擎只处理临时副本，格式检查与格式应用使用相同的项目样式和文件路径。 */
internal class AndroidStudioFormatter(
    private val mStudioHome: String? = null,
    private val mReport: (String) -> Unit = {},
) : SourceFormatter {
    override fun format(root: Path, sources: List<SourceSnapshot>): List<ByteArray> {
        val studio = AndroidStudio.locate(root, mStudioHome)
        val workspace = Files.createTempDirectory("rpclient-as-format-")
        try {
            val copies = workspace.resolve("sources")
            Files.createDirectories(copies)
            val settings = workspace.resolve("code-style.xml")
            Files.copy(root.resolve(".idea/codeStyles/Project.xml"), settings)
            sources.forEach { source ->
                val relative = root.relativize(source.path)
                val copy = copies.resolve(relative)
                Files.createDirectories(copy.parent)
                Files.write(copy, source.before)
                copyEditorConfigs(root, relative.parent, copies)
            }
            val properties = workspace.resolve("idea.properties")
            writeProperties(properties, workspace)
            mReport("Android Studio ${studio.build}: formatting ${sources.size} Kotlin source(s).")

            // 使用原生 format 处理整个副本目录，避免命令行长度限制和逐文件启动 IDE。
            runNative(studio, workspace, properties, settings, copies, sources.size, dry = false)
            runNative(studio, workspace, properties, settings, copies, sources.size, dry = true)
            return sources.map { Files.readAllBytes(copies.resolve(root.relativize(it.path))) }
        } finally {
            Files.walk(workspace)
                .use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    private fun copyEditorConfigs(root: Path, parent: Path?, copies: Path) {
        var relative = parent
        while (true) {
            val name = relative?.resolve(".editorconfig") ?: Path.of(".editorconfig")
            val source = root.resolve(name)
            val target = copies.resolve(name)
            if (Files.isRegularFile(source) && !Files.exists(target)) {
                require(
                    source.toRealPath().startsWith(root)
                ) { "EditorConfig path escapes repository: $source" }
                Files.createDirectories(target.parent)
                Files.copy(source, target)
            }
            if (relative == null) break
            relative = relative.parent
        }
    }

    private fun writeProperties(path: Path, workspace: Path) {
        // 配置、缓存和用户插件均隔离，允许与正在使用的编辑器同时运行。
        val properties = Properties().apply {
            setProperty("idea.config.path", workspace.resolve("config").toString())
            setProperty("idea.system.path", workspace.resolve("system").toString())
            setProperty("idea.plugins.path", workspace.resolve("plugins").toString())
            setProperty("idea.log.path", workspace.resolve("log").toString())
        }
        Files.newOutputStream(path).use { properties.store(it, null) }
    }

    private fun runNative(
        studio: AndroidStudio,
        workspace: Path,
        properties: Path,
        settings: Path,
        copies: Path,
        count: Int,
        dry: Boolean,
    ): Boolean {
        val args =
            listOf("-s", settings.toString(), "-charset", "UTF-8", "-r", "-m", "*.kt,*.kts") +
                (if (dry) listOf("-d") else emptyList()) + copies.toString()
        val builder = ProcessBuilder(studio.command + args).directory(workspace.toFile())
        builder.environment().apply {
            put("STUDIO_PROPERTIES", properties.toString())
            // 不继承开发机的自定义 VM 配置；启动器仍加载安装目录内的原生选项。
            put("STUDIO_VM_OPTIONS", workspace.resolve("formatter.vmoptions").toString())
            put("STUDIO_JDK", studio.javaHome.toString())
        }
        Files.writeString(workspace.resolve("formatter.vmoptions"), "", UTF_8)
        val process = builder.start()
        val output = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader(UTF_8).use { it.readText() }
        }
        val errors = CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader(UTF_8).use { it.readText() }
        }
        try {
            check(process.waitFor(5, TimeUnit.MINUTES)) { "Android Studio formatter timed out" }
            val stdout = output.get()
            val stderr = errors.get()
            val operation = if (dry) "checked" else "formatted"
            val processed =
                Regex("(?m)^(\\d+) file\\(s\\) $operation\\.\\r?$").find(stdout)?.groupValues?.get(1)
                    ?.toInt()
            val wellFormed = Regex("(?m)^(\\d+) file\\(s\\) are well formed\\.\\r?$")
                .find(stdout)?.groupValues?.get(1)?.toInt()
            val pending =
                dry && process.exitValue() == 1 && wellFormed != null && wellFormed in 0 until count
            val validExit = if (dry) {
                (process.exitValue() == 0 && wellFormed == count) || pending
            } else {
                process.exitValue() == 0
            }
            check(validExit && processed == count) {
                "Android Studio formatter ${if (dry) "stability check" else "execution"} failed: " +
                    "exit=${process.exitValue()}, processed=$processed/$count\n$stdout\n$stderr"
            }
            return !pending
        } finally {
            if (process.isAlive) process.destroyForcibly().waitFor()
        }
    }
}
