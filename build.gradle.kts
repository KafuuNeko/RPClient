// 集中声明各模块共用的构建插件，具体启用方式由模块自行配置。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
