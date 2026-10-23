package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.prompt.PromptBudgetExceededException
import java.io.IOException

/** 当前没有可用于生成的已启用模型配置。 */
class NoEnabledLLMProviderException : IllegalStateException(
    "No enabled LLM provider is configured"
)

/**
 * HTTP 请求失败。
 *
 * [responseDetail] 仅供用户主动开启的请求日志记录原始响应；普通错误提示必须通过
 * [classifyGenerationFailure] 生成，不得直接展示异常消息。
 */
class LLMHttpStatusException(
    val statusCode: Int,
    val responseDetail: String = ""
) : IllegalStateException(
    "HTTP $statusCode: $responseDetail"
) {
    init {
        require(statusCode in 100..599) { "Invalid HTTP status code" }
    }
}

/** 模型请求构建或响应解析失败，原始异常仅作为内部原因保留。 */
class LLMRequestException(cause: Throwable) : IllegalStateException(
    "The model request could not be completed",
    cause
)

/** HTTP 已成功建立后由模型协议返回的失败；普通提示只使用错误码分类。 */
class LLMResponseException(val code: String? = null) : IllegalStateException(
    "The model service could not complete the response"
)

/** 为模型调用异常附加可安全展示的配置名称，不包含凭据或请求内容。 */
class LLMProviderRequestException(
    val providerName: String,
    val requestCause: Throwable
) : IllegalStateException(
    "The model request failed for the selected configuration",
    requestCause
)

/** 不依赖 Android 资源且只包含安全字段的生成失败分类。 */
sealed class GenerationFailure {
    data class Image(val kind: ImageRequestFailure) : GenerationFailure()

    data object NoProvider : GenerationFailure()

    data object CharacterProviderUnavailable : GenerationFailure()

    data object SummaryProviderUnavailable : GenerationFailure()

    data class PromptBudget(val requiredTokens: Int, val promptBudget: Int) : GenerationFailure()

    data object Unauthorized : GenerationFailure()

    data object Forbidden : GenerationFailure()

    data object RateLimited : GenerationFailure()

    data class HttpFailure(val statusCode: Int) : GenerationFailure()

    data object RequestFailure : GenerationFailure()

    data object Network : GenerationFailure()

    data class EmptyResponse(val outputTokenLimitReached: Boolean = false) : GenerationFailure()

    data object Unknown : GenerationFailure()
}

/**
 * 将异常归类为安全且可行动的失败；取消返回 null，调用方不得把它显示为错误。
 */
fun classifyGenerationFailure(throwable: Throwable): GenerationFailure? {
    if (throwable is CancellationException) return null
    return when (throwable) {
        is LLMProviderRequestException -> classifyGenerationFailure(throwable.requestCause)
        is ImageRequestException -> GenerationFailure.Image(throwable.failure)
        is NoEnabledLLMProviderException -> GenerationFailure.NoProvider
        is UnavailableLLMProviderSelectionException -> when (throwable.scope) {
            LLMProviderSelectionScope.Character -> GenerationFailure.CharacterProviderUnavailable
            LLMProviderSelectionScope.Summary -> GenerationFailure.SummaryProviderUnavailable
        }
        is PromptBudgetExceededException -> GenerationFailure.PromptBudget(
            requiredTokens = throwable.requiredTokens.coerceIn(minimumValue = 0, maximumValue = MaxReportedTokenCount),
            promptBudget = throwable.promptBudget.coerceIn(minimumValue = 0, maximumValue = MaxReportedTokenCount),
        )
        is LLMHttpStatusException -> when (throwable.statusCode) {
            401 -> GenerationFailure.Unauthorized
            403 -> GenerationFailure.Forbidden
            429 -> GenerationFailure.RateLimited
            else -> GenerationFailure.HttpFailure(throwable.statusCode)
        }
        is LLMResponseException -> when (throwable.code) {
            "invalid_api_key", "authentication_error" -> GenerationFailure.Unauthorized
            "permission_denied" -> GenerationFailure.Forbidden
            "rate_limit_exceeded", "insufficient_quota" -> GenerationFailure.RateLimited
            else -> GenerationFailure.RequestFailure
        }
        is LLMRequestException -> if (throwable.cause is ImageRequestException) classifyGenerationFailure(throwable.cause!!) else GenerationFailure.RequestFailure
        is IOException -> GenerationFailure.Network
        is LLMEmptyResponseException -> GenerationFailure.EmptyResponse(throwable.outputTokenLimitReached)
        else -> GenerationFailure.Unknown
    }
}

private const val MaxReportedTokenCount = 100_000_000
