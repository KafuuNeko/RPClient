package me.kafuuneko.rpclient.libs.agent.runtime

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMToolCall
import me.kafuuneko.rpclient.libs.llm.model.LLMToolExchange
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/**
 * 本机恢复快照显式编码器，字段名不依赖反射或 R8 保留规则。
 * - 不序列化 Provider 认证信息、闭包和数据库实体。
 * - 已最终化的前缀、正则与交换顺序在恢复时保持原值。
 */
class AgentCheckpointCodec(private val mRegexCodec: RegexScriptCodec) {
    /** 编码完整运行快照；工具结果独立检查点保留幂等回放。 */
    fun encode(checkpoint: AgentRuntimeCheckpoint): JsonObject {
        val plan = checkpoint.plan
        val input = plan.input
        // 模型配置只保存 ID 与不可逆指纹，恢复时必须读取原配置并核验。
        return jsonObject(
            "version" to 1,
            "conversation" to input.conversation.key,
            "characterId" to input.characterId,
            "personaId" to input.personaId,
            "providerId" to input.provider.id,
            "providerDigest" to plan.providerDigest,
            "request" to request(input.request),
            "templates" to jsonObject(
                "id" to input.templates.presetId,
                "revision" to input.templates.revision,
                "summaryPosition" to input.templates.summaryInjectionPosition,
                "summaryDepth" to input.templates.summaryInjectionDepth,
                "summaryRole" to input.templates.summaryInjectionRole,
                "worldPercent" to input.templates.worldInfoBudgetPercent,
                "worldCap" to input.templates.worldInfoBudgetCap,
                "think" to input.templates.includeThinkInContext,
                "values" to JsonObject().apply {
                    input.templates.values.forEach { (key, value) ->
                        addProperty(
                            key.key,
                            value
                        )
                    }
                }),
            "tools" to JsonObject().apply {
                plan.toolDigests.forEach { (key, value) ->
                    addProperty(
                        key,
                        value
                    )
                }
            },
            "scripts" to jsonArray(input.scripts.map { script ->
                jsonObject(
                    "scope" to script.scope.name,
                    "ownerId" to script.ownerId,
                    "ownerName" to script.ownerName,
                    "order" to script.order,
                    "json" to mRegexCodec.toJson(listOf(script.script))
                )
            }),
            "macros" to JsonObject().apply {
                input.macros.forEach { (key, value) ->
                    addProperty(
                        key,
                        value
                    )
                }
            },
            "world" to input.worldInfoStateJson,
            "replacingId" to input.replacingMessageId,
            "speakerName" to input.speakerName,
            "otherSpeakers" to strings(input.otherSpeakerNames),
            "trim" to input.trimOtherSpeakers,
            "batchId" to input.batchId,
            "remainingSpeakers" to JsonArray().apply { input.remainingSpeakers.forEach(::add) },
            "streaming" to input.streaming,
            "finalize" to plan.finalizeInstruction,
            "errorGuidance" to plan.errorInstruction,
            "compaction" to plan.compactionInstruction,
            "covered" to plan.compactionCoveredMessageId,
            "prefixCount" to plan.prefixMessageCount,
            "exchanges" to jsonArray(checkpoint.exchanges.map {
                jsonObject(
                    "turn" to turn(it.turn),
                    "results" to jsonArray(it.results),
                    "instruction" to it.instructionBefore
                )
            }),
            "pending" to checkpoint.pending?.let(::turn),
            "pendingStep" to checkpoint.pendingStepId,
            "pendingInstruction" to checkpoint.pendingInstruction
        )
    }

    /** 解码只使用本机受指纹保护的快照，不从请求日志重建。 */
    fun decode(value: JsonObject, provider: LLMProvider): AgentRuntimeCheckpoint {
        // 模板、已处理前缀和顺序交换从同一个原子快照恢复，不重新执行宏或世界书。
        require(value["version"].asInt == 1 && value["providerId"].asLong == provider.id) { "Invalid checkpoint version or provider" }
        val key = value["conversation"].asString
        val id = key.substringAfter('/').toLong()
        val conversation = when (key.substringBefore('/')) {
            "single" -> ConversationRef.Single(id)
            "group" -> ConversationRef.Group(id)
            else -> error(
                "Invalid checkpoint conversation"
            )
        }
        val templates = value.getAsJsonObject("templates")
        val input = AgentTurnInput(
            conversation,
            value["characterId"].asLong,
            value["personaId"].asLong,
            provider,
            request(value.getAsJsonObject("request")),
            emptyList(),
            PromptTemplateSnapshot.resolve(
                templates["id"].asLong,
                templates["revision"].asLong,
                templates.getAsJsonObject("values").entrySet()
                    .associate { it.key to it.value.asString }).copy(
                summaryInjectionPosition = templates["summaryPosition"].asInt,
                summaryInjectionDepth = templates["summaryDepth"].asInt,
                summaryInjectionRole = templates["summaryRole"].asInt,
                worldInfoBudgetPercent = templates["worldPercent"].asInt,
                worldInfoBudgetCap = templates["worldCap"].asInt,
                includeThinkInContext = templates["think"].asBoolean
            ),
            value.getAsJsonArray("scripts").map { element ->
                val script = element.asJsonObject
                ScopedRegexScript(
                    mRegexCodec.parseList(script["json"].asString).single(),
                    RegexScriptScope.valueOf(script["scope"].asString),
                    script["ownerId"].asString,
                    script["ownerName"].asString,
                    script["order"].asInt
                )
            },
            value.getAsJsonObject("macros").entrySet().associate { it.key to it.value.asString },
            value["world"].asString,
            value["replacingId"]?.asLong,
            value["speakerName"].asString,
            value.getAsJsonArray("otherSpeakers").map { it.asString },
            value["trim"].asBoolean,
            value["batchId"]?.asString,
            value.getAsJsonArray("remainingSpeakers").map { it.asLong },
            value["streaming"].asBoolean
        )
        val plan = AgentTurnPlan(
            input,
            value["providerDigest"].asString,
            value.getAsJsonObject("tools").entrySet().associate { it.key to it.value.asString },
            value["finalize"].asString,
            value["errorGuidance"].asString,
            value["compaction"].asString,
            value["covered"].asLong,
            value["prefixCount"].asInt
        )
        return AgentRuntimeCheckpoint(
            plan,
            value.getAsJsonArray("exchanges").map {
                val item = it.asJsonObject
                LLMToolExchange(
                    turn(item.getAsJsonObject("turn")),
                    item.getAsJsonArray("results").map { result -> result.asJsonObject },
                    item["instruction"].asString
                )
            },
            value.getAsJsonObject("pending")?.let(::turn),
            value["pendingStep"].asLong,
            value["pendingInstruction"].asString
        )
    }

    private fun strings(items: List<String>) = JsonArray().apply { items.forEach(::add) }
    private fun turn(turn: LLMToolTurn) = jsonObject(
        "content" to turn.content,
        "reasoning" to turn.reasoning,
        "finish" to turn.finishReason,
        "model" to turn.model,
        "continuation" to turn.continuation,
        "calls" to jsonArray(turn.calls.map {
            jsonObject(
                "name" to it.name,
                "arguments" to it.arguments,
                "id" to it.remoteId
            )
        })
    )

    private fun turn(value: JsonObject) = LLMToolTurn(
        value["content"].asString,
        value.getAsJsonArray("calls").map {
            val call = it.asJsonObject
            LLMToolCall(
                call["name"].asString,
                call["arguments"].asString,
                call["id"]?.asString
            )
        },
        value.getAsJsonArray("continuation").deepCopy(),
        value["finish"].asString,
        value["model"].asString,
        reasoning = value["reasoning"].asString
    )

    private fun request(request: LLMGenerationRequest): JsonObject =
        jsonObject(
            "model" to request.model,
            "routing" to request.routingSessionId,
            "maxTokens" to request.options.maxTokens,
            "temperature" to request.options.temperature,
            "topP" to request.options.topP,
            "stop" to strings(request.options.stop),
            "messages" to jsonArray(request.messages.map { message ->
                jsonObject(
                    "role" to message.role.name,
                    "content" to message.content,
                    "blocks" to jsonArray(message.blocks.map { block ->
                        when (block) {
                            is LLMContentBlock.Text -> jsonObject(
                                "type" to "text",
                                "text" to block.text
                            )

                            is LLMContentBlock.Image -> jsonObject(
                                "type" to "image",
                                "id" to block.reference.uuid,
                                "mime" to block.reference.mimeType,
                                "width" to block.reference.width,
                                "height" to block.reference.height,
                                "bytes" to block.reference.byteCount
                            )
                        }
                    })
                )
            })
        )

    private fun request(value: JsonObject) = LLMGenerationRequest(
        value.getAsJsonArray("messages").map { element ->
            val message = element.asJsonObject
            LLMMessage(
                LLMMessageRole.valueOf(message["role"].asString),
                message["content"].asString,
                message.getAsJsonArray("blocks").map { element ->
                    val block = element.asJsonObject
                    if (block["type"].asString == "text") LLMContentBlock.Text(block["text"].asString) else LLMContentBlock.Image(
                        LLMImageReference(
                            block["id"].asString,
                            block["mime"].asString,
                            block["width"].asInt,
                            block["height"].asInt,
                            block["bytes"].asLong
                        )
                    )
                })
        },
        value["model"]?.asString,
        LLMGenerationOptions(
            maxTokens = value["maxTokens"]?.asInt,
            temperature = value["temperature"]?.asFloat,
            topP = value["topP"]?.asFloat,
            stop = value.getAsJsonArray("stop").map { it.asString }),
        captureReasoning = true,
        routingSessionId = value["routing"]?.asString,
        isPromptFinalized = true
    )
}
