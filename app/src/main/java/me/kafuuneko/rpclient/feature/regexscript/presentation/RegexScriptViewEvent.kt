package me.kafuuneko.rpclient.feature.regexscript.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class RegexScriptViewEvent : IViewEvent {
    data object OpenImporter : RegexScriptViewEvent()
    data class OpenExporter(val fileName: String) : RegexScriptViewEvent()
}
