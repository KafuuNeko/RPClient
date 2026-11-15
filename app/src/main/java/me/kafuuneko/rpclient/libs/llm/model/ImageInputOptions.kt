package me.kafuuneko.rpclient.libs.llm.model

/** 通用的图片理解画质；具体可选范围和协议字段由适配层决定。 */
enum class ImageQuality(val persistedValue: String) {
    Auto("auto"),
    Low("low"),
    Medium("medium"),
    High("high"),
    UltraHigh("ultra_high"),
    Original("original")
}

/**
 * 模型请求的图片处理设置，独立于图片资源的尺寸、格式与生命周期。
 * - 发送和预估共同读取本设置；具体模型决定自动档位的实际行为。
 * - 调整画质不会转码或修改已保存的图片文件。
 */
data class ImageInputOptions(val quality: ImageQuality = ImageQuality.Auto)
