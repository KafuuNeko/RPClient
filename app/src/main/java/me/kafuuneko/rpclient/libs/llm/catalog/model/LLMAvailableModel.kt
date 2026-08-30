package me.kafuuneko.rpclient.libs.llm.catalog.model

/** 模型服务目录中可供用户选择的非敏感模型信息。 */
data class LLMAvailableModel(
    /** 当前记录或列表项的唯一标识。 */
    val id: String,
    /** 模型目录中供用户选择的模型显示名称。 */
    val displayName: String = id,
    /** 用于说明当前对象的描述文本。 */
    val description: String? = null,
    /** 模型输入与输出共享的上下文 Token 上限。 */
    val contextTokens: Int? = null,
    /** 模型目录声明的最大输出 Token 数；未知时为空。 */
    val maxOutputTokens: Int? = null,
    /** 模型目录声明支持的生成参数集合。 */
    val supportedParameters: Set<String> = emptySet()
)
