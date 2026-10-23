package me.kafuuneko.rpclient.libs.llm.model

/** 图片预估策略的稳定存储名称；只影响本地计数，不改变能力或发送质量。 */
enum class ImageTokenEstimatorType {
    Automatic,

    Generic,

    OpenAiTile4o,

    OpenAiTile4oMini,

    OpenAiPatch41Mini,

    OpenAiPatch54,

    ClaudeStandard,

    ClaudeHighResolution,

    Gemini3
}
