package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LLMResponseValidatorTest {
    @Test
    fun nonStreamingEmptyResponseIncludesFinishReason() {
        val error = assertThrows(LLMEmptyResponseException::class.java) {
            LLMGenerationResponse(
                content = "",
                model = "routed-model",
                provider = LLMProviderType.Custom,
                finishReason = "stop",
                rawResponse = "{}"
            ).requireNonEmptyContent("Test Provider", "requested-model")
        }

        assertTrue(error.message.orEmpty().contains("finish reason: stop"))
        assertTrue(error.message.orEmpty().contains("routed-model"))
    }

    @Test
    fun streamingEmptyResponseThrowsAfterFinishedEvent() {
        val error = assertThrows(LLMEmptyResponseException::class.java) {
            runBlocking {
                flowOf(
                    LLMStreamEvent.Finished(
                        finishReason = "stop",
                        model = "routed-model"
                    )
                )
                    .requireNonEmptyContent("Test Provider", "requested-model")
                    .toList()
            }
        }

        assertTrue(error.message.orEmpty().contains("routed-model"))
        assertTrue(error.message.orEmpty().contains("finish reason: stop"))
    }

    @Test
    fun streamingContentPassesThroughUnchanged() = runBlocking {
        val events = listOf(
            LLMStreamEvent.Delta("Hello", "chunk"),
            LLMStreamEvent.Finished(finishReason = "stop")
        )

        val result = flowOf(*events.toTypedArray())
            .requireNonEmptyContent("Test Provider", "model")
            .toList()

        assertEquals(events, result)
    }
}
