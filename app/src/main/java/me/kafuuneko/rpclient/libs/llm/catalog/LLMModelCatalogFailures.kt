package me.kafuuneko.rpclient.libs.llm.catalog

import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.llm.LLMHttpStatusException
import java.io.IOException

/** 模型目录响应缺少协议要求的基本结构。 */
class LLMModelCatalogInvalidResponseException : IllegalStateException(
    "The provider returned an invalid model catalog response"
)

/** 不包含响应正文、凭据或请求头的模型目录失败分类。 */
sealed class LLMModelCatalogFailure {
    data object Unauthorized : LLMModelCatalogFailure()

    data object Forbidden : LLMModelCatalogFailure()

    data object RateLimited : LLMModelCatalogFailure()

    data object UnsupportedEndpoint : LLMModelCatalogFailure()

    data object Network : LLMModelCatalogFailure()

    data object InvalidResponse : LLMModelCatalogFailure()

    data class HttpFailure(val statusCode: Int) : LLMModelCatalogFailure()

    data object Unknown : LLMModelCatalogFailure()
}

/** 将模型目录异常转换为可安全进入 UiState 的失败原因；取消操作返回 null。 */
fun classifyModelCatalogFailure(
    throwable: Throwable,
): LLMModelCatalogFailure? {
    if (throwable is CancellationException) return null
    return when (throwable) {
        is LLMHttpStatusException -> when (throwable.statusCode) {
            401 -> LLMModelCatalogFailure.Unauthorized
            403 -> LLMModelCatalogFailure.Forbidden
            404, 405 -> LLMModelCatalogFailure.UnsupportedEndpoint
            429 -> LLMModelCatalogFailure.RateLimited
            else -> LLMModelCatalogFailure.HttpFailure(throwable.statusCode)
        }

        is IOException -> LLMModelCatalogFailure.Network
        is LLMModelCatalogInvalidResponseException -> {
            LLMModelCatalogFailure.InvalidResponse
        }

        else -> LLMModelCatalogFailure.Unknown
    }
}
