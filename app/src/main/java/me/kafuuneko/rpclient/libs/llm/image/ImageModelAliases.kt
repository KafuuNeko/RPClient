package me.kafuuneko.rpclient.libs.llm.image

import me.kafuuneko.rpclient.libs.llm.model.ImageTokenEstimatorType

/**
 * 集中登记已核实的模型名称，不根据宽泛品牌前缀猜测未来模型规则。
 * - 原生日期快照与 OpenRouter 的点号名称归入同一规则。
 * - 只移除已知路由后缀；未知命名空间、功能变体和未来模型保留通用估算。
 */
internal object ImageModelAliases {
    private val mSnapshot = Regex("-(?:\\d{4}-\\d{2}-\\d{2}|\\d{8})$")
    private val mRoutingSuffixes = setOf("free", "nitro", "floor", "exacto")

    /** 返回模型的稳定估算类别；匹配名称不会据此开启图片能力。 */
    fun typeFor(model: String): ImageTokenEstimatorType {
        var normalized = model.trim().lowercase()
        if ('/' in normalized && normalized.substringAfterLast(':') in mRoutingSuffixes) {
            normalized = normalized.substringBeforeLast(':')
        }
        val name = normalized.replace(mSnapshot, "")
        return mAliases[name] ?: ImageTokenEstimatorType.Generic
    }

    private val mAliases = buildMap {
        fun aliases(type: ImageTokenEstimatorType, namespace: String, vararg names: String) {
            for (name in names) {
                put(name, type)
                put("$namespace/$name", type)
                if (namespace == "anthropic") {
                    val dotted = name.replace(Regex("-(\\d+)-(\\d+)$"), "-$1.$2")
                    put("$namespace/$dotted", type)
                }
            }
        }

        // OpenAI 仅登记官方 sizing 表明确列出的系列，不推断 GPT-6.1 与 Luna。
        aliases(ImageTokenEstimatorType.OpenAiTile4o, "openai", "gpt-4o", "gpt-4.1")
        aliases(ImageTokenEstimatorType.OpenAiTile4oMini, "openai", "gpt-4o-mini")
        aliases(ImageTokenEstimatorType.OpenAiTile51, "openai", "gpt-5", "gpt-5.1")
        aliases(ImageTokenEstimatorType.OpenAiTileReasoning, "openai", "o1", "o1-pro", "o3")
        aliases(ImageTokenEstimatorType.OpenAiPatch41Mini, "openai", "gpt-4.1-mini")
        aliases(ImageTokenEstimatorType.OpenAiPatch52, "openai", "gpt-5.2")
        aliases(
            ImageTokenEstimatorType.OpenAiPatch54,
            "openai",
            "gpt-5.4",
            "gpt-5.4-mini",
            "gpt-5.4-nano"
        )
        aliases(ImageTokenEstimatorType.OpenAiPatch55, "openai", "gpt-5.5")
        aliases(
            ImageTokenEstimatorType.OpenAiPatch56,
            "openai",
            "gpt-5.6-sol",
            "gpt-5.6-terra",
            "gpt-5.6-luna"
        )
        aliases(ImageTokenEstimatorType.OpenAiPatch6, "openai", "gpt-6-astra")

        // Claude 4.7 之后采用高分辨率，旧系列仍使用标准档位。
        aliases(
            ImageTokenEstimatorType.ClaudeStandard, "anthropic",
            "claude-sonnet-4-6", "claude-opus-4-6", "claude-haiku-4-5", "claude-sonnet-4-5",
            "claude-opus-4-5", "claude-opus-4-1", "claude-opus-4", "claude-sonnet-4",
        )
        aliases(
            ImageTokenEstimatorType.ClaudeHighResolution, "anthropic",
            "claude-opus-4-7", "claude-opus-4-8", "claude-opus-5", "claude-opus-5-5",
            "claude-sonnet-5", "claude-sonnet-5-5", "claude-haiku-5-5",
            "claude-fable-5", "claude-fable-5-1", "claude-fable-5-2",
            "claude-mythos-5", "claude-mythos-5-1", "claude-mythos-5-2",
        )

        // Gemini 3 与 4 共用分辨率档位；2.5 的 Pan & Scan 只能近似预算。
        aliases(
            ImageTokenEstimatorType.Gemini3,
            "google",
            "gemini-3-pro-preview",
            "gemini-3-flash-preview",
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash-lite",
            "gemini-3.1-flash-lite-preview",
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash",
            "gemini-3.8-flash",
            "gemini-4-carbon",
            "gemini-4-argon",
            "gemini-4-hydrogen",
        )
        aliases(
            ImageTokenEstimatorType.Gemini25, "google",
            "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite",
        )
    }
}
