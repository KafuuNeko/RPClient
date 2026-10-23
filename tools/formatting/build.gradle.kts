import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

application {
    mainClass = "me.kafuuneko.rpclient.formatting.FormattingMainKt"
}

val repositoryRoot = layout.projectDirectory.dir("../..").asFile

// 独立构建调用已安装的 Android Studio，无需配置 Android 模块或查找 Android SDK。
fun registerFormattingTask(
    name: String,
    mode: String,
) = tasks.register<JavaExec>(name) {
    group = "formatting"
    description =
        if (mode == "apply") {
            "Format selected Kotlin sources."
        } else {
            "Check selected Kotlin sources without writing."
        }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    workingDir = repositoryRoot
    maxHeapSize = "1g"
    args("--root", repositoryRoot.absolutePath, "--mode", mode)

    // 使用参数列表传递路径，不拼接 shell 命令，兼容带空格和中文的文件名。
    mapOf(
        "formatFiles" to "--files",
        "formatFilesFile" to "--files-from",
        "formatBase" to "--base",
        "androidStudioHome" to "--studio-home",
    ).forEach { (property, option) ->
        providers.gradleProperty(property).orNull?.let { value -> args(option, value) }
    }
    if (providers.gradleProperty("formatAll").orNull == "true") args("--all")
}

registerFormattingTask("formatApply", "apply")
registerFormattingTask("formatCheck", "check")

tasks.test {
    systemProperty("rpclient.repository", repositoryRoot.absolutePath)
    providers.gradleProperty("androidStudioHome").orNull?.let {
        systemProperty(
            "rpclient.studio.home",
            it
        )
    }
}
