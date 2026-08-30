package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizerRegistry
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageRecord
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageSource
import me.kafuuneko.rpclient.libs.room.repository.LLMTokenUsageRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 为协议客户端补充成功请求 Token 用量持久化的装饰器。
 *
 * - 服务端上报值优先，本地 Tokenizer 只补齐缺失的输入或输出侧。
 * - 流式响应只在上游正常结束后写入，异常与用户取消不会产生记录。
 * - 持久化异常会被隔离，不能把一次已成功的模型请求改写为失败。
 */
internal class LLMTokenUsageTrackingClient(
    private val mDelegate: LLMClient,
    private val mProvider: LLMProviderConfig,
    private val mRepository: LLMTokenUsageRepository,
    private val mTokenizerRegistry: PromptTokenizerRegistry
) : LLMClient {
    /** 执行非流式生成，并在模型服务成功返回后保存一条用量记录。 */
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        val startNanos = System.nanoTime()
        val response = mDelegate.generate(request)
        val outputText = if (request.includeReasoningInContent) {
            response.content
        } else {
            joinOutput(response.reasoningContent, response.content)
        }
        // 统计落库是附加能力，失败时保留原始成功响应
        trySaveRecord(
            request = request,
            effectiveModel = response.model,
            isStreaming = false,
            outputText = outputText,
            usage = response.usage,
            startNanos = startNanos
        )
        return response
    }

    /** 收集流式正文、推理与最终用量，并在响应流正常关闭后保存记录。 */
    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> = flow {
        val startNanos = System.nanoTime()
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var usage: LLMUsage? = null
        var effectiveModel = request.model ?: mProvider.model
        // 只有完整收集上游流才能到达落库节点，取消和异常会自然跳过
        mDelegate.streamGenerate(request).collect { event ->
            when (event) {
                is LLMStreamEvent.Delta -> content.append(event.content)
                is LLMStreamEvent.ReasoningDelta -> reasoning.append(event.content)
                is LLMStreamEvent.Finished -> {
                    usage = usage.mergeWith(event.usage)
                    effectiveModel = event.model?.takeIf { it.isNotBlank() } ?: effectiveModel
                }
                LLMStreamEvent.Connected -> Unit
            }
            emit(event)
        }
        // 推理已并入正文时不再重复拼接，避免本地估算重复计数
        val outputText = if (request.includeReasoningInContent) {
            content.toString()
        } else {
            joinOutput(reasoning.toString(), content.toString())
        }
        trySaveRecord(
            request = request,
            effectiveModel = effectiveModel,
            isStreaming = true,
            outputText = outputText,
            usage = usage,
            startNanos = startNanos
        )
    }

    /** 解析 Host、补齐缺失用量并尽力持久化脱敏记录。 */
    private suspend fun trySaveRecord(
        request: LLMGenerationRequest,
        effectiveModel: String,
        isStreaming: Boolean,
        outputText: String,
        usage: LLMUsage?,
        startNanos: Long
    ) {
        val tokenizer = mTokenizerRegistry.resolveForUsage(mProvider)
        val reportedInput = usage.reportedInputTokens()
        val reportedOutput = usage.reportedOutputTokens()
        val input = reportedInput?.toLong()
            ?: tokenizer.countMessages(request.messages).toLong()
        val output = reportedOutput?.toLong()
            ?: tokenizer.countText(outputText).toLong()
        val endpoint = mProvider.baseUrl.toHttpUrlOrNull()
        val hasEstimate = reportedInput == null || reportedOutput == null
        // 只记录 endpoint 的 Host 与端口，不保留路径、查询参数和可能的鉴权信息
        val record = LLMTokenUsageRecord(
            providerId = mProvider.providerId?.takeIf { it != 0L },
            providerName = mProvider.name,
            providerType = mProvider.providerType,
            protocol = mProvider.protocol,
            apiHost = endpoint?.host.orEmpty(),
            apiPort = endpoint?.port ?: UNKNOWN_PORT,
            requestedModel = request.model ?: mProvider.model,
            effectiveModel = effectiveModel.ifBlank { request.model ?: mProvider.model },
            isStreaming = isStreaming,
            inputTokens = input,
            outputTokens = output,
            inputTokenSource = reportedInput.toSource(tokenizer.strategy),
            outputTokenSource = reportedOutput.toSource(tokenizer.strategy),
            cachedInputTokens = usage?.cachedPromptTokens?.coerceAtLeast(0)?.toLong(),
            reasoningTokens = usage?.reasoningTokens?.coerceAtLeast(0)?.toLong(),
            tokenizerName = tokenizer.name.takeIf { hasEstimate },
            durationMs = ((System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND)
                .coerceAtLeast(0L)
        )
        runCatching { mRepository.saveRecord(record) }
    }

    private fun LLMUsage?.reportedInputTokens(): Int? {
        this ?: return null
        promptTokens?.let { return it.coerceAtLeast(0) }
        val total = totalTokens ?: return null
        val output = completionTokens ?: return null
        return (total - output).coerceAtLeast(0)
    }

    private fun LLMUsage?.reportedOutputTokens(): Int? {
        this ?: return null
        completionTokens?.let { return it.coerceAtLeast(0) }
        val total = totalTokens ?: return null
        val input = promptTokens ?: return null
        return (total - input).coerceAtLeast(0)
    }

    private fun Int?.toSource(strategy: PromptTokenizerStrategy): LLMTokenUsageSource {
        if (this != null) return LLMTokenUsageSource.ProviderReported
        return when (strategy) {
            PromptTokenizerStrategy.ModelAware -> LLMTokenUsageSource.ModelAwareEstimate
            PromptTokenizerStrategy.Estimated,
            PromptTokenizerStrategy.Conservative -> LLMTokenUsageSource.ProxyEstimate
        }
    }

    private fun LLMUsage?.mergeWith(newer: LLMUsage?): LLMUsage? {
        if (this == null) return newer
        if (newer == null) return this
        return LLMUsage(
            promptTokens = newer.promptTokens ?: promptTokens,
            completionTokens = newer.completionTokens ?: completionTokens,
            totalTokens = newer.totalTokens ?: totalTokens,
            cachedPromptTokens = newer.cachedPromptTokens ?: cachedPromptTokens,
            reasoningTokens = newer.reasoningTokens ?: reasoningTokens
        )
    }

    private fun joinOutput(reasoning: String, content: String): String {
        return listOf(reasoning, content).filter { it.isNotBlank() }.joinToString("\n")
    }

    private companion object {
        const val UNKNOWN_PORT = -1
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
