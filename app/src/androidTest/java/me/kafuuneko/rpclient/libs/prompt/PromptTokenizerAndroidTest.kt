package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTokenizerAndroidTest {
    @Test
    fun openAiVocabularyLoadsOnAndroidRuntime() {
        val tokenizer = PromptTokenizerRegistry().resolve(
            LLMProvider(
                name = "OpenAI",
                providerType = LLMProviderType.ChatGPT,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o-mini"
            )
        )

        assertEquals(PromptTokenizerStrategy.ModelAware, tokenizer.strategy)
        assertEquals(2, tokenizer.countText("hello world"))
    }
}
