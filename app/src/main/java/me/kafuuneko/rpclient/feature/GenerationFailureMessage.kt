package me.kafuuneko.rpclient.feature

import android.content.Context
import androidx.annotation.StringRes
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.llm.GenerationFailure
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.LLMProviderRequestException
import me.kafuuneko.rpclient.libs.llm.UnavailableLLMProviderSelectionException
import me.kafuuneko.rpclient.libs.llm.classifyGenerationFailure

/** 模型配置引导对话框所需的脱敏展示内容。 */
internal data class ModelSettingsGuideContent(
    val title: String,
    val message: String
)

/** 一次生成失败的脱敏提示及可选模型配置引导。 */
internal data class GenerationFailurePresentation(
    val message: String,
    val modelSettingsGuide: ModelSettingsGuideContent?
)

/** 将生成异常转换为不包含模型服务响应、请求内容或凭据的本地化展示信息。 */
internal fun Throwable.toGenerationFailurePresentation(
    context: Context,
    @StringRes fallbackMessageResId: Int
): GenerationFailurePresentation? {
    val failure = classifyGenerationFailure(this) ?: return null
    val providerName = when (this) {
        is LLMProviderRequestException -> providerName
        is UnavailableLLMProviderSelectionException -> providerName
        else -> null
    }
    return failure.toGenerationFailurePresentation(
        context = context,
        fallbackMessageResId = fallbackMessageResId,
        providerName = providerName
    )
}

/** 创建当前没有可用模型配置时的标准引导内容。 */
internal fun noProviderModelSettingsGuide(context: Context): ModelSettingsGuideContent {
    return ModelSettingsGuideContent(
        title = context.getString(R.string.no_model_provider_title),
        message = context.getString(R.string.no_model_provider_desc)
    )
}

/** 将安全失败分类转换为页面可以直接渲染的本地化展示信息。 */
private fun GenerationFailure.toGenerationFailurePresentation(
    context: Context,
    @StringRes fallbackMessageResId: Int,
    providerName: String?
): GenerationFailurePresentation {
    // 页面正文始终使用固定本地化文案，不读取供应商原始响应
    val message = toGenerationFailureMessage(
        context = context,
        fallbackMessageResId = fallbackMessageResId,
        providerName = providerName?.takeIf { it.isNotBlank() }
    )
    // 只有模型配置或 HTTP 访问类错误提供配置入口，避免误导网络和空响应问题
    val guide = when (this) {
        GenerationFailure.NoProvider -> noProviderModelSettingsGuide(context)
        GenerationFailure.CharacterProviderUnavailable,
        GenerationFailure.SummaryProviderUnavailable,
        GenerationFailure.Unauthorized,
        GenerationFailure.Forbidden,
        GenerationFailure.RateLimited,
        GenerationFailure.RequestFailure,
        is GenerationFailure.HttpFailure -> ModelSettingsGuideContent(
            title = context.getString(R.string.model_request_failed_title),
            message = context.getString(R.string.model_request_failed_desc, message)
        )
        is GenerationFailure.Image,
        is GenerationFailure.PromptBudget,
        GenerationFailure.Network,
        GenerationFailure.EmptyResponse,
        GenerationFailure.Unknown -> null
    }
    return GenerationFailurePresentation(message, guide)
}

/** 将安全失败分类转换为不包含原始响应的本地化错误消息。 */
private fun GenerationFailure.toGenerationFailureMessage(
    context: Context,
    @StringRes fallbackMessageResId: Int,
    providerName: String?
): String = when (this) {
    is GenerationFailure.Image -> context.getString(when (kind) {
        ImageRequestFailure.Unsupported -> R.string.image_error_unsupported
        ImageRequestFailure.Missing -> R.string.image_error_missing
        ImageRequestFailure.InvalidImage -> R.string.image_error_invalid
        ImageRequestFailure.TooLarge -> R.string.image_error_large
        ImageRequestFailure.TooMany -> R.string.image_error_count
    })
    GenerationFailure.NoProvider -> context.getString(R.string.generation_error_no_provider)
    GenerationFailure.CharacterProviderUnavailable -> providerName?.let {
        context.getString(R.string.generation_error_character_provider_unavailable_named, it)
    } ?: context.getString(R.string.generation_error_character_provider_unavailable)
    GenerationFailure.SummaryProviderUnavailable -> providerName?.let {
        context.getString(R.string.generation_error_summary_provider_unavailable_named, it)
    } ?: context.getString(R.string.generation_error_summary_provider_unavailable)
    is GenerationFailure.PromptBudget -> context.getString(
        R.string.generation_error_prompt_budget,
        requiredTokens,
        promptBudget
    )
    GenerationFailure.Unauthorized -> providerName?.let {
        context.getString(R.string.generation_error_unauthorized_named, it)
    } ?: context.getString(R.string.generation_error_unauthorized)
    GenerationFailure.Forbidden -> providerName?.let {
        context.getString(R.string.generation_error_forbidden_named, it)
    } ?: context.getString(R.string.generation_error_forbidden)
    GenerationFailure.RateLimited -> providerName?.let {
        context.getString(R.string.generation_error_rate_limited_named, it)
    } ?: context.getString(R.string.generation_error_rate_limited)
    GenerationFailure.RequestFailure -> providerName?.let {
        context.getString(R.string.generation_error_request_failed_named, it)
    } ?: context.getString(fallbackMessageResId)
    is GenerationFailure.HttpFailure -> providerName?.let {
        context.getString(R.string.generation_error_http_named, it, statusCode)
    } ?: context.getString(R.string.generation_error_http, statusCode)
    GenerationFailure.Network -> context.getString(R.string.generation_error_network)
    GenerationFailure.EmptyResponse -> context.getString(R.string.generation_error_empty_response)
    GenerationFailure.Unknown -> context.getString(fallbackMessageResId)
}
