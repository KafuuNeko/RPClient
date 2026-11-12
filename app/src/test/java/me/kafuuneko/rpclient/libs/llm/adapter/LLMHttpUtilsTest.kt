package me.kafuuneko.rpclient.libs.llm.adapter

import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class LLMHttpUtilsTest {
    @Test
    fun anthropicEndpointsAcceptRootAndVersionedBaseUrls() {
        val provider = LLMProviderConfig(
            name = "Claude",
            providerType = LLMProviderType.Claude,
            protocol = LLMProviderProtocol.AnthropicMessages,
            baseUrl = "https://example.invalid",
            model = "claude-model",
        )
        // 覆盖旧预设、反向代理路径和末尾斜杠；聊天与模型目录共用同一地址规则。
        listOf("", "/gateway").forEach { prefix ->
            val root = "https://example.invalid$prefix"
            listOf(root, "$root/", "$root/v1", " $root/v1// ").forEach { baseUrl ->
                val normalized = provider.copy(baseUrl = baseUrl).normalizedBaseUrl()
                assertEquals("$root/v1/messages", "$normalized/v1/messages")
                assertEquals("$root/v1/models", "$normalized/v1/models")
            }
        }
    }

    @Test
    fun otherProtocolsKeepVersionedBaseUrls() {
        // OpenAI 适配器直接拼接资源名，必须保留其配置中的 /v1。
        LLMProviderProtocol.entries.filter { it != LLMProviderProtocol.AnthropicMessages }
            .forEach { protocol ->
                val provider = LLMProviderConfig(
                    name = "provider",
                    providerType = LLMProviderType.Custom,
                    protocol = protocol,
                    baseUrl = " https://example.invalid/api/v1/ ",
                    model = "model",
                )
                assertEquals("https://example.invalid/api/v1", provider.normalizedBaseUrl())
            }
    }

    @Test
    fun alternatingConversationKeepsMidPromptSystemPositionAndMergesUserTurns() {
        val result = listOf(
            LLMMessage(LLMMessageRole.System, "Main"),
            LLMMessage(LLMMessageRole.User, "Hello"),
            LLMMessage(LLMMessageRole.System, "Author note"),
            LLMMessage(LLMMessageRole.User, "Continue"),
            LLMMessage(LLMMessageRole.Assistant, "Reply"),
        ).toAlternatingConversationMessages()

        assertEquals(
            listOf(
                LLMMessage(
                    role = LLMMessageRole.User,
                    content = "Hello\n\nAuthor note\n\nContinue",
                ),
                LLMMessage(LLMMessageRole.Assistant, "Reply"),
            ),
            result,
        )
    }

    @Test
    fun alternatingConversationAddsUserPlaceholderWhenOnlySystemExists() {
        val result =
            listOf(LLMMessage(LLMMessageRole.System, "Main")).toAlternatingConversationMessages()

        assertEquals(
            listOf(LLMMessage(LLMMessageRole.User, "Let's get started.")),
            result,
        )
    }
}
