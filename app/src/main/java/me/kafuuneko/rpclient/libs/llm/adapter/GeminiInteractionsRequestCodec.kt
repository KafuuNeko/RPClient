package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.resolveFor
import me.kafuuneko.rpclient.libs.prompt.request.DEFAULT_STRICT_PROMPT_PLACEHOLDER

/**
 * Gemini Interactions v1 普通生成请求编码器。
 * - 连续前置系统消息映射到系统指令，历史保持步骤与内容块的原序。
 * - 仅使用本地历史，图片通过请求级占位符进入共用的受限文件写出流程。
 */
internal object GeminiInteractionsRequestCodec {
    /**
     * 生成已应用请求扩展的 JSON，本方法不读取图片文件。
     * @param provider 已解析本次实际模型的配置。
     * @param request 调用方选定的历史与生成参数。
     * @param stream 是否要求服务端返回 SSE。
     * @param registerImage 为本次发送登记图片占位符。
     * @return 交给网络层的无状态请求。
     */
    fun encode(
        provider: LLMProviderConfig,
        request: LLMGenerationRequest,
        stream: Boolean,
        registerImage: (LLMImageReference) -> String,
    ): String {
        require(request.messages.none { it.role == LLMMessageRole.System && it.images.isNotEmpty() }) {
            "System instructions cannot contain images."
        }
        val options = request.options.resolveFor(provider)
        val images = ImageContentCodec(provider)
        // 只把前置系统消息提升为指令，中途的资料保持在历史中的原位置。
        val history = request.messages.dropWhile { it.role == LLMMessageRole.System }.ifEmpty {
            listOf(LLMMessage(LLMMessageRole.User, DEFAULT_STRICT_PROMPT_PLACEHOLDER))
        }
        val input = JsonArray().apply { history.forEach { add(step(it, images, registerImage)) } }
        val payload = JsonObject().apply {
            addProperty("model", provider.model)
            addProperty("stream", stream)
            addProperty("store", false)
            request.messages.leadingSystemPrompt().takeIf { it.isNotEmpty() }
                ?.let { addProperty("system_instruction", it) }
            add("input", input)
            add("generation_config", JsonObject().apply {
                addProperty("max_output_tokens", options.maxTokens)
                if (options.stop.isNotEmpty()) add(
                    "stop_sequences",
                    JsonArray().apply { options.stop.forEach(::add) })
                if (request.captureReasoning) addProperty("thinking_summaries", "auto")
            })
        }
        // 路由变量沿用既有配置；系统指令、输入、输出预算和服务器存储归属受保护。
        return mergeRequestBodyExtensionsJson(
            baseJson = payload.toString(), patchJson = provider.requestBodyPatchJson,
            protectedPaths = protectedRequestBodyPaths(provider.protocol),
            routingSessionId = request.routingSessionId, protocol = provider.protocol,
        )
    }

    /**
     * 将通用消息写为原生角色步骤，文本空白与图文块顺序保持不变。
     * @param message 已最终化的一条历史消息。
     * @param images 当前模型配置的图片编码器。
     * @param registerImage 登记本次传输使用的图片占位符。
     * @return 含有序内容块的用户输入或模型输出步骤。
     */
    private fun step(
        message: LLMMessage,
        images: ImageContentCodec,
        registerImage: (LLMImageReference) -> String
    ) =
        JsonObject().apply {
            // Interactions 用步骤类型表达角色，中途系统资料沿用用户步骤的位置。
            addProperty(
                "type",
                if (message.role == LLMMessageRole.Assistant) "model_output" else "user_input"
            )
            // 图文块按原序写出，图片占位符交给共用网络层解析。
            add("content", JsonArray().apply {
                message.contentBlocks.forEach { block ->
                    when (block) {
                        is LLMContentBlock.Text -> add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", block.text)
                        })

                        is LLMContentBlock.Image -> add(
                            images.encode(
                                block.reference,
                                registerImage(block.reference)
                            )
                        )
                    }
                }
            })
        }
}
