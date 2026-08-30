package me.kafuuneko.rpclient.libs.llm.model

/** 新模型配置为模型回复预留的默认 Token 数。 */
const val DEFAULT_LLM_MAX_TOKENS = 8192

/** 新模型配置用于输入与输出的默认总上下文预算。 */
const val DEFAULT_LLM_CONTEXT_TOKENS = 32768

/** 高级请求 JSON 中用于引用稳定匿名会话路由 ID 的系统变量名。 */
const val LLM_REQUEST_VARIABLE_ROUTING_SESSION_ID = "\$rpclient.routing_session_id"

/** 历史配置迁移时仅补写会话粘性，不额外改变用户的推理行为。 */
const val OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON =
    "{\"session_id\":\"\$rpclient.routing_session_id\"}"

/** 内置 Gemini 模板显式展示默认思考等级和摘要返回开关，用户可在高级 JSON 中调整。 */
const val DEFAULT_GEMINI_REQUEST_BODY_PATCH_JSON =
    "{\"generationConfig\":{\"thinkingConfig\":{\"thinkingLevel\":\"low\",\"includeThoughts\":true}}}"

/** 内置 Claude 模板只展示默认 effort，不隐式开启 adaptive thinking。 */
const val DEFAULT_CLAUDE_REQUEST_BODY_PATCH_JSON =
    "{\"output_config\":{\"effort\":\"low\"}}"

/** 内置 DeepSeek 模板显式展示其默认启用的思考模式和强度。 */
const val DEFAULT_DEEPSEEK_REQUEST_BODY_PATCH_JSON =
    "{\"thinking\":{\"type\":\"enabled\"},\"reasoning_effort\":\"low\"}"

/** 内置 Grok 模板显式展示其默认思考强度。 */
const val DEFAULT_GROK_REQUEST_BODY_PATCH_JSON =
    "{\"reasoning_effort\":\"low\"}"

/** 内置 OpenRouter 模板同时启用会话粘性并展示统一推理参数。 */
const val DEFAULT_OPENROUTER_REQUEST_BODY_PATCH_JSON =
    "{\"session_id\":\"\$rpclient.routing_session_id\",\"reasoning\":{\"effort\":\"low\"}}"

/**
 * 在线模型供应商类型，用于 UI 展示和统计归类。
 */
enum class LLMProviderType {
    ChatGPT,
    Gemini,
    Claude,
    DeepSeek,
    Grok,
    OpenRouter,
    Custom
}

/**
 * 模型配置实际使用的 HTTP 协议。
 *
 * ChatGPT、DeepSeek、OpenRouter 以及大多数第三方网关都归入 OpenAICompatible。
 */
enum class LLMProviderProtocol {
    OpenAICompatible,
    Gemini,
    AnthropicMessages
}

/**
 * 模型配置使用的本地 Token 预估器类型。
 *
 * 枚举名称会作为 Room 持久化格式保存，已有成员不得直接重命名。
 */
enum class LocalTokenEstimatorType {
    /** 按已知模型、协议和供应商信息选择预估器，无法识别时使用安全回退。 */
    Automatic,

    /** 固定使用 CL100K BPE 进行本地代理估算。 */
    Cl100kBase,

    /** 固定使用 O200K BPE 进行本地代理估算。 */
    O200kBase
}

/**
 * LLM 模块运行时使用的模型配置。
 */
data class LLMProviderConfig(
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 模型配置所属的供应商类型。 */
    val providerType: LLMProviderType,
    /** 模型配置实际采用的通信协议。 */
    val protocol: LLMProviderProtocol,
    /** 模型服务 API 的基础地址。 */
    val baseUrl: String,
    /** 访问模型服务所需的密钥，仅在受控业务层持有。 */
    val apiKey: String = "",
    /** 当前配置或请求使用的模型名称。 */
    val model: String,
    /** 用户配置的自定义请求头 JSON，仅在受控业务层解析。 */
    val customHeadersJson: String = "",
    /** 合并到协议请求体的 JSON Merge Patch；结构字段由各协议适配器保护。 */
    val requestBodyPatchJson: String = "{}",
    /** 控制模型输出随机性的温度参数。 */
    val temperature: Float = 0.8f,
    /** 限制模型候选词累计概率的 Top P 参数。 */
    val topP: Float = 1.0f,
    /** 单次生成允许返回的最大 Token 数。 */
    val maxTokens: Int = DEFAULT_LLM_MAX_TOKENS,
    /** 模型输入与输出共享的上下文 Token 上限。 */
    val contextTokens: Int = DEFAULT_LLM_CONTEXT_TOKENS,
    /** 本地 Prompt 预算与用量回退共同使用的 Token 预估器。 */
    val localTokenEstimatorType: LocalTokenEstimatorType = LocalTokenEstimatorType.Automatic,
    /** 是否向模型服务发送 temperature 参数。 */
    val sendTemperature: Boolean = true,
    /** 是否向模型服务发送 top_p 参数。 */
    val sendTopP: Boolean = true,
    /** 是否优先采用服务端上报的 Token 用量；关闭后完全使用本地估算。 */
    val useServerReportedUsage: Boolean = false,
    /** 已持久化配置的主键；编辑页未保存的临时配置为空。 */
    val providerId: Long? = null
)

/**
 * 通用聊天消息角色，适配器会转换成各协议自己的角色名称。
 */
enum class LLMMessageRole {
    System,
    User,
    Assistant
}

/**
 * 通用聊天消息。
 */
data class LLMMessage(
    /** 当前对象在业务流程中承担的角色。 */
    val role: LLMMessageRole,
    /** 当前对象承载的正文内容。 */
    val content: String
)

/**
 * 通用生成参数。为空时使用当前模型配置的默认值。
 */
data class LLMGenerationOptions(
    /** 控制模型输出随机性的温度参数。 */
    val temperature: Float? = null,
    /** 单次生成允许返回的最大 Token 数。 */
    val maxTokens: Int? = null,
    /** 限制模型候选词累计概率的 Top P 参数。 */
    val topP: Float? = null,
    /** 命中后要求模型停止生成的文本序列。 */
    val stop: List<String> = emptyList()
)

/** 已按模型配置的能力开关收敛的实际请求参数。 */
data class ResolvedLLMGenerationOptions(
    /** 控制模型输出随机性的温度参数。 */
    val temperature: Float?,
    /** 单次生成允许返回的最大 Token 数。 */
    val maxTokens: Int,
    /** 限制模型候选词累计概率的 Top P 参数。 */
    val topP: Float?,
    /** 命中后要求模型停止生成的文本序列。 */
    val stop: List<String>
)

/** 将业务请求参数与模型配置默认值合并，并过滤未启用的可选参数。 */
fun LLMGenerationOptions.resolveFor(
    provider: LLMProviderConfig
): ResolvedLLMGenerationOptions {
    return ResolvedLLMGenerationOptions(
        temperature = if (provider.sendTemperature) {
            temperature ?: provider.temperature
        } else {
            null
        },
        maxTokens = maxTokens ?: provider.maxTokens,
        topP = if (provider.sendTopP) topP ?: provider.topP else null,
        stop = stop
    )
}

/**
 * 通用生成请求，非流式与流式接口共用同一个请求模型。
 */
data class LLMGenerationRequest(
    /** 当前状态或请求包含的消息列表。 */
    val messages: List<LLMMessage>,
    /** 当前配置或请求使用的模型名称。 */
    val model: String? = null,
    /** 覆盖当前模型配置默认值的本次生成参数。 */
    val options: LLMGenerationOptions = LLMGenerationOptions(),
    /** 是否将推理文本合并进最终正文。 */
    val includeReasoningInContent: Boolean = false,
    /** 是否请求并接收模型服务可提供的推理文本；展示策略由业务层决定。 */
    val captureReasoning: Boolean = includeReasoningInContent,
    /** 请求模板可用于会话粘性路由的不透明 ID；字段位置由模型配置决定。 */
    val routingSessionId: String? = null,
    /** 已完成宏展开、后处理和最终上下文预算，不应在 Repository 中再次改写。 */
    val isPromptFinalized: Boolean = false
)

/**
 * Token 用量信息。不同模型服务的字段不完全一致，因此允许为空。
 */
data class LLMUsage(
    /** 模型服务上报的输入 Token 数。 */
    val promptTokens: Int? = null,
    /** 模型服务上报的输出 Token 数。 */
    val completionTokens: Int? = null,
    /** 模型服务上报的总 Token 数。 */
    val totalTokens: Int? = null,
    /** 输入 Token 中由服务端缓存命中的部分，仅作为明细展示，不重复计入输入总量。 */
    val cachedPromptTokens: Int? = null,
    /** 输出 Token 中服务端标记为推理过程的部分，仅作为明细展示。 */
    val reasoningTokens: Int? = null
)

/**
 * 一次性生成完成后的完整响应。
 */
data class LLMGenerationResponse(
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 当前配置或请求使用的模型名称。 */
    val model: String,
    /** 当前请求关联的模型供应商类型。 */
    val provider: LLMProviderType,
    /** 模型服务上报或本地估算的 Token 用量。 */
    val usage: LLMUsage? = null,
    /** 未并入正文的推理文本，仅用于本次响应的本地用量估算。 */
    val reasoningContent: String = "",
    /** 模型服务给出的停止原因，用于区分正常完成、长度限制和空响应。 */
    val finishReason: String? = null,
    /** 模型服务返回的原始响应，仅限受控调试流程使用。 */
    val rawResponse: String
)

/**
 * 流式生成事件。
 */
sealed class LLMStreamEvent {
    /** 模型服务已接受请求并建立响应流。 */
    data object Connected : LLMStreamEvent()

    /**
     * 模型增量输出的文本片段。
     */
    data class Delta(
        /** 当前对象承载的正文内容。 */
        val content: String,
        /** 流式协议返回的原始数据块，仅限受控调试流程使用。 */
        val rawChunk: String
    ) : LLMStreamEvent()

    /**
     * 模型服务明确返回的推理文本片段，不应直接并入最终正文。
     */
    data class ReasoningDelta(
        /** 当前对象承载的正文内容。 */
        val content: String,
        /** 流式协议返回的原始数据块，仅限受控调试流程使用。 */
        val rawChunk: String,
        /** 当前 Prompt 来源或推理文本的细分类型。 */
        val kind: LLMReasoningKind = LLMReasoningKind.Detailed
    ) : LLMStreamEvent()

    /**
     * 模型服务明确返回的完成事件。
     */
    data class Finished(
        /** 流式协议返回的原始数据块，仅限受控调试流程使用。 */
        val rawChunk: String? = null,
        /** 流式协议在结束块中返回的停止原因。 */
        val finishReason: String? = null,
        /** 网关实际路由到的模型名；没有提供时由调用方使用请求模型。 */
        val model: String? = null,
        /** 模型服务在流结束前上报的用量；缺失字段由统计层单独估算。 */
        val usage: LLMUsage? = null
    ) : LLMStreamEvent()
}

/** 模型服务返回的推理文本粒度。 */
enum class LLMReasoningKind {
    Summary,
    Detailed
}
