package me.kafuuneko.rpclient.model

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

/** 生成失败在对话弹窗中推荐的下一步处理方向。 */
internal enum class GenerationFailureAction {
    /** 瞬时或运行期波动，适合在弹窗中提供一次原地重试。 */
    Retry,

    /** 模型选型、凭据、路由或模型级参数不匹配，需前往模型配置修改。 */
    ConfigureModel,

    /** 附件或历史内容约束，关闭弹窗后由用户直接调整消息或附件。 */
    DismissOnly,
}

/** 一次生成失败的脱敏提示、推荐处理动作及可选模型配置引导。 */
internal data class GenerationFailurePresentation(
    val message: String,
    val modelSettingsGuide: ModelSettingsGuideContent?,
    val action: GenerationFailureAction = GenerationFailureAction.DismissOnly,
)

/** 将生成异常转换为不包含模型服务响应、请求内容或凭据的本地化展示信息。 */
internal fun Throwable.toGenerationFailurePresentation(
    context: Context,
    @StringRes fallbackMessageResId: Int,
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
        providerName = providerName,
    )
}

/** 创建当前没有可用模型配置时的标准引导内容。 */
internal fun noProviderModelSettingsGuide(context: Context): ModelSettingsGuideContent {
    return ModelSettingsGuideContent(
        title = context.getString(R.string.no_model_provider_title),
        message = context.getString(R.string.no_model_provider_desc),
    )
}

/** 根据失败根因划分弹窗动作，将模型配置问题与瞬时重试及内容约束分离。 */
internal fun GenerationFailure.toFailureAction(): GenerationFailureAction = when (this) {
    GenerationFailure.NoProvider,
    GenerationFailure.CharacterProviderUnavailable,
    GenerationFailure.SummaryProviderUnavailable,
    GenerationFailure.Unauthorized,
    GenerationFailure.Forbidden,
    is GenerationFailure.PromptBudget -> GenerationFailureAction.ConfigureModel

    is GenerationFailure.HttpFailure -> if (statusCode == 408 || statusCode in 500..599) {
        GenerationFailureAction.Retry
    } else {
        GenerationFailureAction.ConfigureModel
    }

    is GenerationFailure.Image -> when (kind) {
        ImageRequestFailure.Unsupported,
        ImageRequestFailure.UnsupportedDetail -> GenerationFailureAction.ConfigureModel

        ImageRequestFailure.UnsupportedFormat,
        ImageRequestFailure.Missing,
        ImageRequestFailure.InvalidImage,
        ImageRequestFailure.OriginalUnsupported,
        ImageRequestFailure.OriginalTooLarge,
        ImageRequestFailure.TooLarge,
        ImageRequestFailure.TooMany -> GenerationFailureAction.DismissOnly
    }

    GenerationFailure.RateLimited,
    GenerationFailure.RequestFailure,
    GenerationFailure.Network,
    is GenerationFailure.EmptyResponse,
    GenerationFailure.Unknown -> GenerationFailureAction.Retry
}

/** 将安全失败分类转换为页面可以直接渲染的本地化展示信息。 */
private fun GenerationFailure.toGenerationFailurePresentation(
    context: Context,
    @StringRes fallbackMessageResId: Int,
    providerName: String?,
): GenerationFailurePresentation {
    // 页面正文始终使用固定本地化文案，不读取供应商原始响应。
    val message = toGenerationFailureMessage(
        context = context,
        fallbackMessageResId = fallbackMessageResId,
        providerName = providerName?.takeIf { it.isNotBlank() },
    )
    val action = toFailureAction()
    // 只有模型配置与能力类错误生成配置引导，瞬时故障与附件问题不误导至模型设置页。
    val guide = when {
        this == GenerationFailure.NoProvider -> noProviderModelSettingsGuide(context)

        this is GenerationFailure.PromptBudget || this is GenerationFailure.Image -> {
            if (action == GenerationFailureAction.ConfigureModel) {
                ModelSettingsGuideContent(
                    title = context.getString(R.string.model_request_failed_title),
                    message = message,
                )
            } else {
                null
            }
        }

        action == GenerationFailureAction.ConfigureModel -> ModelSettingsGuideContent(
            title = context.getString(R.string.model_request_failed_title),
            message = context.getString(R.string.model_request_failed_desc, message),
        )

        else -> null
    }
    return GenerationFailurePresentation(
        message = message,
        modelSettingsGuide = guide,
        action = action,
    )
}

/** 将安全失败分类转换为不包含原始响应的本地化错误消息。 */
private fun GenerationFailure.toGenerationFailureMessage(
    context: Context,
    @StringRes fallbackMessageResId: Int,
    providerName: String?,
): String = when (this) {
    is GenerationFailure.Image -> context.getString(
        when (kind) {
            ImageRequestFailure.Unsupported -> R.string.image_error_unsupported
            ImageRequestFailure.UnsupportedDetail -> R.string.image_error_detail
            ImageRequestFailure.UnsupportedFormat -> R.string.image_error_provider_format
            ImageRequestFailure.Missing -> R.string.image_error_missing
            ImageRequestFailure.InvalidImage -> R.string.image_error_invalid
            ImageRequestFailure.OriginalUnsupported -> R.string.image_error_original_format
            ImageRequestFailure.OriginalTooLarge -> R.string.image_error_original_large
            ImageRequestFailure.TooLarge -> R.string.image_error_large
            ImageRequestFailure.TooMany -> R.string.image_error_count
        },
    )

    GenerationFailure.NoProvider -> context.getString(R.string.generation_error_no_provider)
    GenerationFailure.CharacterProviderUnavailable -> providerName?.let { text ->
        context.getString(R.string.generation_error_character_provider_unavailable_named, text)
    } ?: context.getString(R.string.generation_error_character_provider_unavailable)

    GenerationFailure.SummaryProviderUnavailable -> providerName?.let { text ->
        context.getString(R.string.generation_error_summary_provider_unavailable_named, text)
    } ?: context.getString(R.string.generation_error_summary_provider_unavailable)

    is GenerationFailure.PromptBudget -> context.getString(
        R.string.generation_error_prompt_budget,
        requiredTokens,
        promptBudget,
    )

    GenerationFailure.Unauthorized -> providerName?.let { text ->
        context.getString(R.string.generation_error_unauthorized_named, text)
    } ?: context.getString(R.string.generation_error_unauthorized)

    GenerationFailure.Forbidden -> providerName?.let { text ->
        context.getString(R.string.generation_error_forbidden_named, text)
    } ?: context.getString(R.string.generation_error_forbidden)

    GenerationFailure.RateLimited -> providerName?.let { text ->
        context.getString(R.string.generation_error_rate_limited_named, text)
    } ?: context.getString(R.string.generation_error_rate_limited)

    GenerationFailure.RequestFailure -> providerName?.let { text ->
        context.getString(R.string.generation_error_request_failed_named, text)
    } ?: context.getString(fallbackMessageResId)

    is GenerationFailure.HttpFailure -> providerName?.let { text ->
        context.getString(R.string.generation_error_http_named, text, statusCode)
    } ?: context.getString(R.string.generation_error_http, statusCode)

    GenerationFailure.Network -> context.getString(R.string.generation_error_network)
    is GenerationFailure.EmptyResponse -> context.getString(R.string.generation_error_empty_response)
    GenerationFailure.Unknown -> context.getString(fallbackMessageResId)
}
