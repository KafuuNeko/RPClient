package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.prompt.request.PromptBudgetExceededException
import me.kafuuneko.rpclient.model.GenerationFailureAction
import me.kafuuneko.rpclient.model.toFailureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GenerationFailureClassifierTest {
    /** 协议在 HTTP 建连后返回的错误码也需归类，大小写与外层空白不影响引导。 */
    @Test
    fun classifiesProtocolErrorCodesWithoutCaseSensitivity() {
        val codes = mapOf(
            "invalid_api_key" to GenerationFailure.Unauthorized,
            "authentication_error" to GenerationFailure.Unauthorized,
            "authentication" to GenerationFailure.Unauthorized,
            "unauthenticated" to GenerationFailure.Unauthorized,
            "permission_denied" to GenerationFailure.Forbidden,
            "rate_limit_exceeded" to GenerationFailure.RateLimited,
            "insufficient_quota" to GenerationFailure.RateLimited,
            "quota_exceeded" to GenerationFailure.RateLimited,
            "resource_exhausted" to GenerationFailure.RateLimited,
            "too_many_requests" to GenerationFailure.RateLimited,
        )
        // 同时覆盖既有服务商和 Interactions，避免新增分类破坏原有错误提示。
        codes.forEach { (code, expected) ->
            for (variant in listOf(code, " ${code.uppercase()}\t")) {
                assertEquals(
                    variant,
                    expected,
                    classifyGenerationFailure(LLMResponseException(variant))
                )
            }
        }
        for (code in listOf(null, "", "unknown_error")) {
            assertEquals(
                GenerationFailure.RequestFailure,
                classifyGenerationFailure(LLMResponseException(code))
            )
        }
    }

    @Test
    fun classifiesKnownFailuresUsingOnlySafeFields() {
        assertEquals(
            GenerationFailure.NoProvider,
            classifyGenerationFailure(NoEnabledLLMProviderException()),
        )
        assertEquals(
            GenerationFailure.CharacterProviderUnavailable,
            classifyGenerationFailure(
                throwable = UnavailableLLMProviderSelectionException(
                    scope = LLMProviderSelectionScope.Character,
                    providerId = 7L,
                ),
            ),
        )
        assertEquals(
            GenerationFailure.SummaryProviderUnavailable,
            classifyGenerationFailure(
                throwable = UnavailableLLMProviderSelectionException(
                    scope = LLMProviderSelectionScope.Summary,
                    providerId = 8L,
                ),
            ),
        )
        assertEquals(
            GenerationFailure.PromptBudget(requiredTokens = 12, promptBudget = 8),
            classifyGenerationFailure(
                PromptBudgetExceededException(
                    requiredTokens = 12,
                    promptBudget = 8
                )
            ),
        )
        assertEquals(
            GenerationFailure.Unauthorized,
            classifyGenerationFailure(LLMHttpStatusException(401)),
        )
        assertEquals(
            GenerationFailure.Forbidden,
            classifyGenerationFailure(LLMHttpStatusException(403)),
        )
        assertEquals(
            GenerationFailure.RateLimited,
            classifyGenerationFailure(LLMHttpStatusException(429)),
        )
        assertEquals(
            GenerationFailure.HttpFailure(500),
            classifyGenerationFailure(LLMHttpStatusException(500)),
        )
        assertEquals(
            GenerationFailure.RequestFailure,
            classifyGenerationFailure(LLMRequestException(IllegalStateException("secret"))),
        )
        assertEquals(
            GenerationFailure.Unauthorized,
            classifyGenerationFailure(
                throwable = LLMProviderRequestException(
                    providerName = "Role model",
                    requestCause = LLMHttpStatusException(401),
                ),
            ),
        )
        assertEquals(GenerationFailure.Network, classifyGenerationFailure(IOException("secret")))
        assertEquals(
            GenerationFailure.EmptyResponse(),
            classifyGenerationFailure(LLMEmptyResponseException()),
        )
    }

    @Test
    fun unknownFailureDoesNotCopyThrowableMessage() {
        val failure = classifyGenerationFailure(IllegalStateException("sensitive sentinel"))

        assertEquals(GenerationFailure.Unknown, failure)
    }

    @Test
    fun httpFailureKeepsResponseDetailForDebugLogsButClassifiesByStatus() {
        val responseDetail = """{"error":{"message":"provider detail"}}"""
        val error = LLMHttpStatusException(statusCode = 500, responseDetail = responseDetail)

        assertTrue(error.message.orEmpty().contains(responseDetail))
        assertEquals(GenerationFailure.HttpFailure(500), classifyGenerationFailure(error))
    }

    @Test
    fun cancellationIsNotAUserVisibleFailure() {
        assertNull(classifyGenerationFailure(CancellationException("cancelled")))
    }

    @Test
    fun mapsGenerationFailureActionsByRootCause() {
        // 瞬时网络、限流、超时及 5xx 服务端波动归入重试弹窗。
        val retryable = listOf(
            GenerationFailure.Network,
            GenerationFailure.RateLimited,
            GenerationFailure.HttpFailure(408),
            GenerationFailure.HttpFailure(500),
            GenerationFailure.HttpFailure(503),
            GenerationFailure.EmptyResponse(),
            GenerationFailure.RequestFailure,
            GenerationFailure.Unknown,
        )
        retryable.forEach { failure ->
            assertEquals(failure.toString(), GenerationFailureAction.Retry, failure.toFailureAction())
        }

        // 凭据、权限、4xx 客户端错误、上下文预算及模型图片能力问题归入模型配置引导。
        val configurable = listOf(
            GenerationFailure.NoProvider,
            GenerationFailure.CharacterProviderUnavailable,
            GenerationFailure.SummaryProviderUnavailable,
            GenerationFailure.Unauthorized,
            GenerationFailure.Forbidden,
            GenerationFailure.HttpFailure(400),
            GenerationFailure.HttpFailure(404),
            GenerationFailure.HttpFailure(422),
            GenerationFailure.PromptBudget(requiredTokens = 100, promptBudget = 50),
            GenerationFailure.Image(ImageRequestFailure.Unsupported),
            GenerationFailure.Image(ImageRequestFailure.UnsupportedDetail),
        )
        configurable.forEach { failure ->
            assertEquals(
                failure.toString(),
                GenerationFailureAction.ConfigureModel,
                failure.toFailureAction(),
            )
        }

        // 附件损坏、缺失或数量体积超限仅提示关闭，由用户直接调整消息或附件。
        val dismissOnly = listOf(
            ImageRequestFailure.Missing,
            ImageRequestFailure.InvalidImage,
            ImageRequestFailure.TooLarge,
            ImageRequestFailure.TooMany,
            ImageRequestFailure.OriginalUnsupported,
            ImageRequestFailure.OriginalTooLarge,
            ImageRequestFailure.UnsupportedFormat,
        ).map(GenerationFailure::Image)
        dismissOnly.forEach { failure ->
            assertEquals(
                failure.toString(),
                GenerationFailureAction.DismissOnly,
                failure.toFailureAction(),
            )
        }
    }
}
