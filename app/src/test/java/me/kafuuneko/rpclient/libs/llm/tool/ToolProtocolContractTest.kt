package me.kafuuneko.rpclient.libs.llm.tool

import me.kafuuneko.rpclient.libs.llm.model.LLMToolCall
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.libs.llm.model.LLMToolExchange
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMToolChoice
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.llm.model.requireValidToolTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 协议边界纯算法测试，HTTP 及取消释放在仪器矩阵验证。 */
class ToolProtocolContractTest {
    private val mDefinition = AgentToolDefinition(
        "test", "lookup", 1, "Lookup", ToolSchema(
            ToolSchema.Type.Object,
            properties = linkedMapOf(
                "query" to ToolSchema(ToolSchema.Type.String),
                "optional" to ToolSchema(ToolSchema.Type.Integer, nullable = true)
            ), required = setOf("query")
        ), ToolEffect.ReadOnly
    )
    private val mRequest = LLMToolGenerationRequest(
        LLMGenerationRequest(
            listOf(
                LLMMessage(
                    LLMMessageRole.User,
                    "hello"
                )
            )
        ), listOf(mDefinition)
    )

    @Test
    fun optionalAndNullableRemainDistinctAcrossProtocols() {
        val schema = wireSchema(mDefinition.parameters)
        assertFalse(strictCompatible(mDefinition.parameters))
        assertEquals(listOf("query"), schema.getAsJsonArray("required").map { it.asString })
        assertEquals(
            listOf("integer", "null"),
            schema.getAsJsonObject("properties").getAsJsonObject("optional").getAsJsonArray("type")
                .map { it.asString })
        LLMProviderProtocol.entries.forEach { protocol ->
            val encoded = ToolWireRequest.encode(provider(protocol), mRequest, false) { it.uuid }
            assertTrue(encoded.contains("lookup"))
            assertTrue(encoded.contains("required"))
        }
    }

    @Test
    fun patchesCannotOverrideToolChoiceOrContinuation() {
        val patches = mapOf(
            LLMProviderProtocol.OpenAICompatible to "{\"tool_choice\":\"auto\"}",
            LLMProviderProtocol.OpenAIResponses to "{\"previous_response_id\":\"old\"}",
            LLMProviderProtocol.AnthropicMessages to "{\"messages\":[]}",
            LLMProviderProtocol.Gemini to "{\"tool_config\":{}}",
            LLMProviderProtocol.GeminiInteractions to "{\"generation_config\":{\"tool_choice\":{\"type\":\"auto\"}}}"
        )
        patches.forEach { (protocol, patch) ->
            assertThrows(IllegalArgumentException::class.java) {
                ToolWireRequest.encode(
                    provider(protocol).copy(requestBodyPatchJson = patch),
                    mRequest.copy(choice = LLMToolChoice.None),
                    false
                ) { it.uuid }
            }
        }
    }

    @Test
    fun unknownClientToolsAndDuplicateHostedNamesAreRejected() {
        val patches = listOf(
            "{\"tools\":[{\"type\":\"function\",\"name\":\"evil\"}]}",
            "{\"tools\":[{\"type\":\"web_search\",\"name\":\"lookup\"}]}"
        )
        patches.forEach { patch ->
            assertThrows(LLMToolProtocolException::class.java) {
                ToolWireRequest.encode(
                    provider(LLMProviderProtocol.OpenAIResponses).copy(requestBodyPatchJson = patch),
                    mRequest,
                    false
                ) { it.uuid }
            }
        }
    }

    @Test
    fun nativeTruncationAndEofNeverProduceExecutableCalls() {
        val responses = mapOf(
            LLMProviderProtocol.OpenAICompatible to """{"choices":[{"finish_reason":"length","message":{"role":"assistant","tool_calls":[{"id":"c","type":"function","function":{"name":"lookup","arguments":"{}"}}]}}]}""",
            LLMProviderProtocol.OpenAIResponses to """{"status":"incomplete","output":[{"type":"function_call","name":"lookup","arguments":"{}","call_id":"c"}]}""",
            LLMProviderProtocol.AnthropicMessages to """{"stop_reason":"max_tokens","content":[{"type":"tool_use","name":"lookup","input":{},"id":"c"}]}""",
            LLMProviderProtocol.Gemini to """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"functionCall":{"name":"lookup","args":{}}}]}}]}""",
            LLMProviderProtocol.GeminiInteractions to """{"status":"incomplete","steps":[{"type":"function_call","name":"lookup","arguments":{},"id":"c"}]}""",
        )
        responses.forEach { (protocol, response) ->
            val codec = ToolWireRequest.codec(protocol)
            assertThrows(protocol.name, LLMToolProtocolException::class.java) {
                codec.decode(
                    parseObject(response),
                    "model"
                ).requireValidToolTurn()
            }
            assertThrows(protocol.name, LLMToolProtocolException::class.java) {
                codec.stream().finish("model")
            }
        }
    }

    @Test
    fun fragmentedCompatibleCallsUseIndexAndRepeatedIdsWithoutDuplication() {
        val stream = OpenAICompatibleToolCodec().stream()
        val frames = listOf(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"a","function":{"name":"lookup","arguments":"{\"query\":"}},{"index":1,"id":"b","function":{"name":"lookup","arguments":"{\"query\":"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"id":"b","function":{"name":"lookup","arguments":"\"two\"}"}},{"id":"a","function":{"arguments":"\"one\"}"}}]}}]}""",
            """{"choices":[{"finish_reason":"tool_calls","delta":{}}]}""",
        )
        frames.forEach { stream.accept("", parseObject(it)) }
        val turn = stream.finish("model").requireValidToolTurn()
        assertEquals(listOf("a", "b"), turn.calls.map { it.remoteId })
        assertEquals(listOf("lookup", "lookup"), turn.calls.map { it.name })
        assertEquals(
            listOf("{\"query\":\"one\"}", "{\"query\":\"two\"}"),
            turn.calls.map { it.arguments })
    }

    @Test
    fun duplicateArgumentKeysAndBrokenThinkingSignaturesAreRejected() {
        assertThrows(LLMToolProtocolException::class.java) { parseObject("""{"args":{"q":1,"q":2}}""") }
        val response =
            parseObject("""{"stop_reason":"tool_use","content":[{"type":"thinking","thinking":"x"},{"type":"tool_use","id":"c","name":"lookup","input":{}}]}""")
        assertThrows(LLMToolProtocolException::class.java) {
            AnthropicToolCodec().decode(
                response,
                "model"
            )
        }
    }

    @Test
    fun tailGuidanceRemainsInTheNextRequestPrefixForEveryProtocol() {
        LLMProviderProtocol.entries.forEach { protocol ->
            val call = LLMToolCall("lookup", "{\"query\":\"one\"}", "c")
            val continuation = when (protocol) {
                LLMProviderProtocol.OpenAICompatible -> jsonObject(
                    "role" to "assistant",
                    "tool_calls" to jsonArray(
                        listOf(
                            jsonObject(
                                "id" to "c",
                                "type" to "function",
                                "function" to jsonObject(
                                    "name" to "lookup",
                                    "arguments" to call.arguments
                                )
                            )
                        )
                    )
                )

                LLMProviderProtocol.OpenAIResponses -> jsonObject(
                    "type" to "function_call",
                    "call_id" to "c",
                    "name" to "lookup",
                    "arguments" to call.arguments
                )

                LLMProviderProtocol.AnthropicMessages -> jsonObject(
                    "type" to "tool_use",
                    "id" to "c",
                    "name" to "lookup",
                    "input" to parseObject(call.arguments)
                )

                LLMProviderProtocol.Gemini -> jsonObject(
                    "functionCall" to jsonObject(
                        "name" to "lookup",
                        "args" to parseObject(call.arguments)
                    )
                )

                LLMProviderProtocol.GeminiInteractions -> jsonObject(
                    "type" to "function_call",
                    "id" to "c",
                    "name" to "lookup",
                    "arguments" to parseObject(call.arguments)
                )
            }
            val turn = LLMToolTurn(
                "",
                listOf(call),
                jsonArray(listOf(continuation)),
                "tool_calls",
                "fixture"
            )
            val first = LLMToolExchange(
                turn,
                listOf(jsonObject("kind" to "recoverable_error", "code" to "not_found"))
            )
            val guidance = "Recover from the previous tool error."
            val second = LLMToolExchange(
                turn,
                listOf(jsonObject("kind" to "success", "data" to JsonObject())),
                instructionBefore = guidance
            )
            val before = parseObject(
                ToolWireRequest.encode(
                    provider(protocol),
                    mRequest.copy(exchanges = listOf(first), tailInstruction = guidance),
                    false
                ) { it.uuid })
            val after = parseObject(
                ToolWireRequest.encode(
                    provider(protocol),
                    mRequest.copy(exchanges = listOf(first, second)),
                    false
                ) { it.uuid })
            val key = when (protocol) {
                LLMProviderProtocol.OpenAICompatible, LLMProviderProtocol.AnthropicMessages -> "messages"
                LLMProviderProtocol.Gemini -> "contents"
                else -> "input"
            }
            val prefix = before.getAsJsonArray(key)
            assertEquals(
                protocol.name,
                prefix,
                jsonArray(after.getAsJsonArray(key).take(prefix.size()))
            )
            if (protocol == LLMProviderProtocol.AnthropicMessages) {
                assertTrue(after.getAsJsonArray(key)[2].asJsonObject.getAsJsonArray("content")[0].asJsonObject["is_error"].asBoolean)
            }
        }
    }

    private fun provider(protocol: LLMProviderProtocol) = LLMProviderConfig(
        "Fixture",
        LLMProviderType.Custom,
        protocol,
        "https://example.invalid",
        model = "fixture"
    )
}
