package me.kafuuneko.rpclient.feature.promptpreset.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** Prompt 预设页需要宿主处理的一次性系统动作。 */
sealed class PromptPresetViewEvent : IViewEvent {
    data class CopyText(val text: String) : PromptPresetViewEvent()

    /** 缺少素材占位符时，由宿主使用当前语言格式化提示。 */
    data class ShowInvalidSummaryFormat(val requiredMacro: String) : PromptPresetViewEvent()
}
