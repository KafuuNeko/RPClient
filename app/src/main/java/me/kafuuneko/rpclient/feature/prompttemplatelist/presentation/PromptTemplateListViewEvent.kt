package me.kafuuneko.rpclient.feature.prompttemplatelist.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 系统文档选择器由 Activity 承载，业务快照仍归 ViewModel 管理。 */
sealed class PromptTemplateListViewEvent : IViewEvent {
    data object OpenImporter : PromptTemplateListViewEvent()
    data object OpenExporter : PromptTemplateListViewEvent()
}
