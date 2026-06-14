package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent

/**
 * 模型明确结束请求但没有返回任何可显示内容。
 *
 * 空内容配合 stop 并不代表一次有效生成，常见原因包括提示目标冲突、
 * 供应商不接受当前消息顺序，或模型在网关内部立即停止。
 */
class LLMEmptyResponseException(
    providerName: String,
    model: String,
    finishReason: String? = null,
) : IllegalStateException(
    buildMessage(providerName, model, finishReason)
) {
    private companion object {
        fun buildMessage(
            providerName: String,
            model: String,
            finishReason: String?,
        ): String {
            val details = listOfNotNull(
                "provider: $providerName",
                "model: $model",
                finishReason
                    ?.takeIf(String::isNotBlank)
                    ?.let { "finish reason: $it" },
            ).joinToString(prefix = " (", postfix = ")")
            return "The model returned an empty response$details. " +
                    "Check the prompt objective, message role order, or try a different model route."
        }
    }
}

/** 校验非流式结果，避免上层把空 stop 当作成功并静默结束。 */
internal fun LLMGenerationResponse.requireNonEmptyContent(
    providerName: String,
    requestedModel: String
): LLMGenerationResponse {
    if (content.isBlank()) {
        throw LLMEmptyResponseException(
            providerName = providerName,
            model = model.ifBlank { requestedModel },
            finishReason = finishReason
        )
    }
    return this
}

/**
 * 校验流式结果是否至少产生过一个非空文本增量。
 *
 * 完成事件仍会原样转发给 UI；只有流正常结束且始终没有有效文本时才抛出异常，
 * 网络错误和供应商显式错误继续保留原异常。
 */
internal fun Flow<LLMStreamEvent>.requireNonEmptyContent(
    providerName: String,
    requestedModel: String
): Flow<LLMStreamEvent> = flow {
    var hasContent = false
    var finishReason: String? = null
    var actualModel: String? = null

    collect { event ->
        when (event) {
            is LLMStreamEvent.Delta -> {
                if (event.content.isNotBlank()) hasContent = true
            }
            is LLMStreamEvent.Finished -> {
                event.finishReason?.let { finishReason = it }
                event.model?.let { actualModel = it }
            }
        }
        emit(event)
    }

    if (!hasContent) {
        throw LLMEmptyResponseException(
            providerName = providerName,
            model = actualModel?.takeIf { it.isNotBlank() } ?: requestedModel,
            finishReason = finishReason
        )
    }
}
