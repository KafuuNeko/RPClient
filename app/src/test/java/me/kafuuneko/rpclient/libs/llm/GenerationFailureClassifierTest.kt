package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.prompt.PromptBudgetExceededException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GenerationFailureClassifierTest {
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
}
