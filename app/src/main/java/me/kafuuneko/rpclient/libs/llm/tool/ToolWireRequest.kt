package me.kafuuneko.rpclient.libs.llm.tool

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.llm.adapter.GeminiInteractionsRequestCodec
import me.kafuuneko.rpclient.libs.llm.adapter.ImageContentCodec
import me.kafuuneko.rpclient.libs.llm.adapter.OpenAIResponsesRequestCodec
import me.kafuuneko.rpclient.libs.llm.adapter.cleanString
import me.kafuuneko.rpclient.libs.llm.adapter.leadingSystemPrompt
import me.kafuuneko.rpclient.libs.llm.adapter.mergeRequestBodyExtensionsJson
import me.kafuuneko.rpclient.libs.llm.adapter.openAICompatibleTokenLimitField
import me.kafuuneko.rpclient.libs.llm.adapter.protectedRequestBodyPaths
import me.kafuuneko.rpclient.libs.llm.adapter.toAlternatingConversationMessages
import me.kafuuneko.rpclient.libs.llm.adapter.toAnthropicRole
import me.kafuuneko.rpclient.libs.llm.adapter.toGeminiRole
import me.kafuuneko.rpclient.libs.llm.adapter.toOpenAIRole
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.llm.model.resolveFor
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/** Agent 请求投影与真实发送共用编码器，预算包含工具、系统指令及原生续接。 */
internal object ToolWireRequest {
    /** 按协议选择无状态工具编解码器。 */
    fun codec(protocol: LLMProviderProtocol): ToolProtocolCodec = when (protocol) {
        LLMProviderProtocol.OpenAICompatible -> OpenAICompatibleToolCodec()
        LLMProviderProtocol.OpenAIResponses -> OpenAIResponsesToolCodec()
        LLMProviderProtocol.AnthropicMessages -> AnthropicToolCodec()
        LLMProviderProtocol.Gemini -> GeminiToolCodec()
        LLMProviderProtocol.GeminiInteractions -> GeminiInteractionsToolCodec()
    }

    /** 先合并受保护的扩展，再合入已校验的服务端工具；授权字段不能被补丁覆盖。 */
    fun encode(
        provider: LLMProviderConfig,
        request: LLMToolGenerationRequest,
        stream: Boolean,
        image: (LLMImageReference) -> String
    ): String {
        val patch = parseObject(provider.requestBodyPatchJson.ifBlank { "{}" })
        val hosted = patch.remove("tools")
        val protected = protectedRequestBodyPaths(provider.protocol) + setOf(
            "tools",
            "tool_choice",
            "toolChoice",
            "toolConfig",
            "tool_config",
            "functions",
            "function_call",
            "generation_config.tool_choice",
            "generationConfig.toolChoice",
            "stream_function_call_arguments",
            "streamFunctionCallArguments"
        )
        val payload = base(provider.copy(requestBodyPatchJson = "{}"), request, stream, image)
        codec(provider.protocol).extend(payload, request)
        val merged = parseObject(
            mergeRequestBodyExtensionsJson(
                payload.toString(),
                patch.toString(),
                protected,
                request.base.routingSessionId,
                provider.protocol
            )
        )
        // 补丁只允许显式的服务端托管类型，禁止借此注册客户端函数绕过白名单。
        if (hosted != null) {
            if (!hosted.isJsonArray) throw LLMToolProtocolException("invalid_hosted_tools")
            val allowed = when (provider.protocol) {
                LLMProviderProtocol.OpenAIResponses -> setOf(
                    "web_search",
                    "web_search_preview",
                    "file_search",
                    "code_interpreter"
                )

                LLMProviderProtocol.AnthropicMessages -> setOf(
                    "web_search_20250305",
                    "web_search_20260209",
                    "web_fetch_20250910",
                    "web_fetch_20260209"
                )

                LLMProviderProtocol.GeminiInteractions -> setOf(
                    "google_search",
                    "url_context",
                    "code_execution"
                )

                else -> emptySet()
            }
            val names = request.tools.map { it.name }.toMutableSet()
            hosted.asJsonArray.forEach { value ->
                if (!value.isJsonObject || value.asJsonObject.cleanString("type") !in allowed) throw LLMToolProtocolException(
                    "unsupported_hosted_tool"
                )
                val name = value.asJsonObject.cleanString("name")
                    .ifEmpty { value.asJsonObject.cleanString("type") }
                if (!names.add(name)) throw LLMToolProtocolException("duplicate_tool_name")
                merged.getAsJsonArray("tools").add(value.deepCopy())
            }
        }
        if (request.tools.isEmpty() && (hosted == null || hosted.asJsonArray.isEmpty)) {
            merged.remove("tools")
            merged.remove("tool_choice")
            merged.remove("toolConfig")
            merged.getAsJsonObject("generation_config")?.remove("tool_choice")
        }
        return merged.toString()
    }

    private fun base(
        provider: LLMProviderConfig,
        request: LLMToolGenerationRequest,
        stream: Boolean,
        image: (LLMImageReference) -> String
    ): JsonObject {
        val model = request.base.model ?: provider.model
        if (provider.protocol == LLMProviderProtocol.OpenAIResponses) return parseObject(
            OpenAIResponsesRequestCodec.encode(provider, request.base, model, stream, image)
        )
        if (provider.protocol == LLMProviderProtocol.GeminiInteractions) return parseObject(
            GeminiInteractionsRequestCodec.encode(
                provider.copy(model = model),
                request.base,
                stream,
                image
            )
        )
        val options = request.base.options.resolveFor(provider)
        val payload = jsonObject("model" to model, "stream" to stream)
        val messages = request.base.messages
        // 仅协议要求时提升系统指令，其余消息保持已最终化的顺序与内容。
        when (provider.protocol) {
            LLMProviderProtocol.OpenAICompatible -> {
                payload.add(
                    "messages",
                    jsonArray(messages.map {
                        jsonObject(
                            "role" to it.toOpenAIRole(),
                            "content" to content(it, provider, image)
                        )
                    })
                )
                payload.addProperty(
                    openAICompatibleTokenLimitField(provider.providerType),
                    options.maxTokens
                )
                if (stream) payload.add("stream_options", jsonObject("include_usage" to true))
            }

            LLMProviderProtocol.AnthropicMessages -> {
                payload.addProperty("system", messages.leadingSystemPrompt())
                payload.add(
                    "messages",
                    jsonArray(
                        messages.toAlternatingConversationMessages().map {
                            jsonObject(
                                "role" to it.toAnthropicRole(),
                                "content" to content(it, provider, image)
                            )
                        })
                )
                payload.addProperty("max_tokens", options.maxTokens)
            }

            LLMProviderProtocol.Gemini -> {
                payload.remove("model")
                payload.remove("stream")
                payload.add(
                    "systemInstruction",
                    jsonObject("parts" to jsonArray(listOf(jsonObject("text" to messages.leadingSystemPrompt()))))
                )
                payload.add(
                    "contents",
                    jsonArray(
                        messages.toAlternatingConversationMessages().map {
                            jsonObject(
                                "role" to it.toGeminiRole(),
                                "parts" to content(it, provider, image)
                            )
                        })
                )
                payload.add(
                    "generationConfig",
                    jsonObject("maxOutputTokens" to options.maxTokens).apply {
                        options.temperature?.let { addProperty("temperature", it) }
                        options.topP?.let { addProperty("topP", it) }
                        if (request.base.captureReasoning) add(
                            "thinkingConfig",
                            jsonObject("includeThoughts" to true)
                        )
                    })
            }

            else -> error("Unexpected tool protocol")
        }
        if (provider.protocol != LLMProviderProtocol.Gemini) {
            options.temperature?.let { payload.addProperty("temperature", it) }
            options.topP?.let { payload.addProperty("top_p", it) }
        }
        if (options.stop.isNotEmpty()) {
            val stop = JsonArray().apply { options.stop.forEach(::add) }
            when (provider.protocol) {
                LLMProviderProtocol.AnthropicMessages -> payload.add("stop_sequences", stop)
                LLMProviderProtocol.Gemini -> payload.getAsJsonObject("generationConfig")
                    .add("stopSequences", stop)

                else -> payload.add("stop", stop)
            }
        }
        return payload
    }

    private fun content(
        message: LLMMessage,
        provider: LLMProviderConfig,
        image: (LLMImageReference) -> String
    ): JsonElement {
        if (message.images.isEmpty() && provider.protocol != LLMProviderProtocol.Gemini) return JsonPrimitive(
            message.content
        )
        val codec = ImageContentCodec(provider)
        return jsonArray(message.contentBlocks.map { block ->
            when (block) {
                is LLMContentBlock.Text -> if (provider.protocol == LLMProviderProtocol.Gemini) jsonObject(
                    "text" to block.text
                ) else jsonObject("type" to "text", "text" to block.text)

                is LLMContentBlock.Image -> codec.encode(block.reference, image(block.reference))
            }
        })
    }
}
