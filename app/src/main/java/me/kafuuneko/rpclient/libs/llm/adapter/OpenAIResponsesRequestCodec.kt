package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.resolveFor

/**
 * Responses 原生请求编码器。
 * - 保留最终 Prompt 的角色与顺序，以完整 input 管理本地会话。
 * - 图片只登记资源占位符，实际载荷由共享资源编码器写出。
 * - 高级 JSON 合并后校验推理与采样参数，避免模型不接受的组合。
 */
internal object OpenAIResponsesRequestCodec {
    /** 构造流式或非流式请求；imageUrl 必须返回本次登记的图片占位符。 */
    fun encode(
        provider: LLMProviderConfig,
        request: LLMGenerationRequest,
        model: String,
        stream: Boolean,
        imageUrl: (LLMImageReference) -> String
    ): String {
        val options = request.options.resolveFor(provider)
        require(options.stop.isEmpty()) { "Responses does not support stop sequences." }
        // 使用手动上下文模式，禁止服务端自行裁剪而使检查器与真实输入不同。
        val payload = JsonObject().apply {
            addProperty("model", model)
            add("input", JsonArray().also { input ->
                request.messages.forEach { input.add(message(it, imageUrl)) }
            })
            addProperty("max_output_tokens", options.maxTokens)
            addProperty("stream", stream)
            addProperty("store", false)
            addProperty("truncation", "disabled")
            options.temperature?.let { addProperty("temperature", it) }
            options.topP?.let { addProperty("top_p", it) }
            if (request.captureReasoning && supportsSummary(model)) {
                add("reasoning", JsonObject().apply { addProperty("summary", "auto") })
            }
        }
        val merged = JsonParser.parseString(mergeRequestBodyExtensionsJson(
            baseJson = payload.toString(),
            patchJson = provider.requestBodyPatchJson,
            protectedPaths = protectedRequestBodyPaths(provider.protocol),
            routingSessionId = request.routingSessionId
        )).asJsonObject
        // 已知推理模型仅在显式关闭推理时发送采样参数；未知模型遵从用户设置。
        val effort = merged.objectOrNull("reasoning")?.cleanString("effort").orEmpty()
        if (effort != "none" && (effort.isNotEmpty() || isReasoningModel(model))) {
            merged.remove("temperature")
            merged.remove("top_p")
            merged.remove("top_logprobs")
            merged.arrayOrNull("include")?.let { include ->
                merged.add("include", JsonArray().apply {
                    include.filterNot { it.isJsonPrimitive && it.asString == "message.output_text.logprobs" }
                        .forEach { add(it) }
                })
            }
        }
        return merged.toString()
    }

    /** EasyInputMessage 允许历史 assistant 使用 input_text，避免伪造输出 Item 的标识。 */
    private fun message(message: LLMMessage, imageUrl: (LLMImageReference) -> String): JsonObject {
        require(message.role != LLMMessageRole.System || message.images.isEmpty()) {
            "System instructions cannot contain user images."
        }
        // 保留内容块中的空白和图文交错，不能对正文使用 trim 或合并角色。
        val content = JsonArray()
        message.contentBlocks.forEach { block ->
            content.add(JsonObject().apply {
                when (block) {
                    is LLMContentBlock.Text -> {
                        addProperty("type", "input_text")
                        addProperty("text", block.text)
                    }
                    is LLMContentBlock.Image -> {
                        addProperty("type", "input_image")
                        addProperty("image_url", imageUrl(block.reference))
                        addProperty("detail", "auto")
                    }
                }
            })
        }
        return JsonObject().apply {
            addProperty("type", "message")
            addProperty("role", when (message.role) {
                LLMMessageRole.System -> "system"
                LLMMessageRole.User -> "user"
                LLMMessageRole.Assistant -> "assistant"
            })
            add("content", content)
            // 本地聊天记录保存最终回复；中间工具进度不作为独立历史消息回放。
            if (message.role == LLMMessageRole.Assistant) addProperty("phase", "final_answer")
        }
    }

    private fun isReasoningModel(model: String): Boolean =
        Regex("^(gpt-[56](?:[.-]|$)|o[134](?:-|$))").containsMatchIn(model.lowercase())

    private fun supportsSummary(model: String): Boolean =
        Regex("^(gpt-[56](?:[.-]|$)|o[34](?:-|$))").containsMatchIn(model.lowercase())
}
