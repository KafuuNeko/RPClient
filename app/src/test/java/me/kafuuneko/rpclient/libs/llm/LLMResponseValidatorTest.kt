package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LLMResponseValidatorTest {
    @Test
    fun nonStreamingEmptyResponseUsesFixedSafeMessage() {
        val error = assertThrows(LLMEmptyResponseException::class.java) {
            LLMGenerationResponse(
                content = "",
                model = "routed-model",
                provider = LLMProviderType.Custom,
                finishReason = "stop",
                rawResponse = "{}",
            ).requireNonEmptyContent()
        }

        assertEquals("The model returned an empty response", error.message)
        assertEquals(GenerationFailure.EmptyResponse(), classifyGenerationFailure(error))
    }

    @Test
    fun reasoningOnlyTokenLimitSurvivesValidationAndProviderContext() {
        // 模拟各协议在思考耗尽输出额度后返回空正文的情况。
        listOf("length", "max_tokens", "MAX_TOKENS", "max_output_tokens").forEach { finishReason ->
            val error = assertThrows(LLMEmptyResponseException::class.java) {
                LLMGenerationResponse(
                    content = "",
                    model = "summary-model",
                    provider = LLMProviderType.Custom,
                    finishReason = finishReason,
                    reasoningContent = "The summary is still being planned.",
                    usage = LLMUsage(completionTokens = 512, reasoningTokens = 512),
                    rawResponse = "private response sentinel",
                ).requireNonEmptyContent()
            }
            // 复现 Repository 的异常包装，验证页面依赖的错误分类仍保留截断原因。
            val failure =
                classifyGenerationFailure(LLMProviderRequestException("Summary model", error))
            assertEquals(GenerationFailure.EmptyResponse(outputTokenLimitReached = true), failure)
            assertEquals("The model returned an empty response", error.message)
        }
    }

    @Test
    fun emptyResponseWithoutTokenLimitDoesNotSuggestIncreasingBudget() {
        // 空正文和 Token 用量本身不能证明发生截断，必须依据服务端结束原因。
        listOf(
            null,
            "stop",
            "end_turn",
            "STOP",
            "SAFETY",
            "content_filter"
        ).forEach { finishReason ->
            val error = assertThrows(LLMEmptyResponseException::class.java) {
                LLMGenerationResponse(
                    content = " ",
                    model = "summary-model",
                    provider = LLMProviderType.Custom,
                    finishReason = finishReason,
                    usage = LLMUsage(completionTokens = 512, reasoningTokens = 512),
                    rawResponse = "{}",
                ).requireNonEmptyContent()
            }
            assertEquals(
                GenerationFailure.EmptyResponse(),
                classifyGenerationFailure(LLMProviderRequestException("Summary model", error)),
            )
        }
    }

    @Test
    fun streamingEmptyResponseThrowsAfterFinishedEvent() {
        val error = assertThrows(LLMEmptyResponseException::class.java) {
            runBlocking {
                flowOf(
                    value = LLMStreamEvent.Finished(
                        finishReason = "stop",
                        model = "routed-model",
                    ),
                )
                    .requireNonEmptyContent()
                    .toList()
            }
        }

        assertEquals("The model returned an empty response", error.message)
    }

    @Test
    fun streamingContentPassesThroughUnchanged() = runBlocking {
        val events = listOf(
            LLMStreamEvent.Delta("Hello", "chunk"),
            LLMStreamEvent.Finished(finishReason = "stop"),
        )

        val result = flowOf(*events.toTypedArray())
            .requireNonEmptyContent()
            .toList()

        assertEquals(events, result)
    }
}
