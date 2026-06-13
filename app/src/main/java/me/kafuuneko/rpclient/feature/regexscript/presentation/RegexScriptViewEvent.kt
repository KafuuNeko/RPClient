package me.kafuuneko.rpclient.feature.regexscript.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 需要 Activity 调用系统文件选择器处理的一次性事件。 */
sealed class RegexScriptViewEvent : IViewEvent {
    data object OpenImporter : RegexScriptViewEvent()
    data class OpenExporter(val fileName: String) : RegexScriptViewEvent()
}
