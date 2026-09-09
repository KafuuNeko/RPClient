package me.kafuuneko.rpclient.libs.llm.model

/**
 * 模型配置中图片输入能力的用户设置。
 *
 * - 枚举名称按 Room Converter 约定直接持久化为字符串，已有成员不可直接重命名。
 * - MODEL 阶段会将此设置与模型目录信息结合，解析为运行时的 Supported / Unsupported / Unknown 状态。
 */
enum class ImageInputSetting {
    /** 按模型目录信息自动判断；信息缺失时保持未知，允许尝试。 */
    Auto,

    /** 用户明确指定当前模型支持图片输入。 */
    Supported,

    /** 用户明确指定当前模型不支持图片输入。 */
    Unsupported
}
