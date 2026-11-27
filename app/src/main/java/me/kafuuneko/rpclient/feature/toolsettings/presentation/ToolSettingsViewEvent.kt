package me.kafuuneko.rpclient.feature.toolsettings.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 系统选择器由 Activity 承载，导出快照保留在 ViewModel 私有字段。 */
sealed interface ToolSettingsViewEvent : IViewEvent {
    data object OpenImporter : ToolSettingsViewEvent
    data object OpenExporter : ToolSettingsViewEvent
}
