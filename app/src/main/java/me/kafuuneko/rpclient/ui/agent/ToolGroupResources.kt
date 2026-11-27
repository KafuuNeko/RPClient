package me.kafuuneko.rpclient.ui.agent

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolGroup

/** @return 共享工具分组的本地化标题，不影响工具执行契约。 */
@StringRes
fun ToolGroup.titleRes(): Int = when (this) {
    ToolGroup.Lore -> R.string.agent_tool_group_lore
    ToolGroup.Chat -> R.string.agent_tool_group_chat
    ToolGroup.Memory -> R.string.agent_tool_group_memory
    ToolGroup.Scene -> R.string.agent_tool_group_scene
    ToolGroup.Utility -> R.string.agent_tool_group_utility
    ToolGroup.Interaction -> R.string.agent_tool_group_interaction
    ToolGroup.Network -> R.string.tool_settings_tools
}
