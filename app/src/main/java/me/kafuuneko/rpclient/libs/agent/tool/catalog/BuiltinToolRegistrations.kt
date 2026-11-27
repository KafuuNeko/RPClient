package me.kafuuneko.rpclient.libs.agent.tool.catalog

import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.builtin.chat.ReadMessagesTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.chat.SearchChatTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.interaction.AskUserTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.lore.ReadLoreEntryTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.lore.SearchLoreTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.math.CalculateTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.math.RollDiceTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.memory.CorrectMemoryTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.memory.InvalidateMemoryTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.memory.PromoteMemoryScopeTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.memory.ReadMemoryTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.memory.SearchMemoryTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.memory.UpsertMemoryTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.scene.GetSceneStateTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.scene.UpdateSceneStateTool
import me.kafuuneko.rpclient.libs.prompt.tool.ChatToolPrompts
import me.kafuuneko.rpclient.libs.prompt.tool.InteractionToolPrompts
import me.kafuuneko.rpclient.libs.prompt.tool.LoreToolPrompts
import me.kafuuneko.rpclient.libs.prompt.tool.MathToolPrompts
import me.kafuuneko.rpclient.libs.prompt.tool.MemoryToolPrompts
import me.kafuuneko.rpclient.libs.prompt.tool.SceneToolPrompts

/** 内置工具唯一注册入口；新增项同时接入策略、配置、展示和运行工厂。 */
object BuiltinToolRegistrations {
    private val mPageDefaults = listOf(
        EditableDefaultSpec("limit", JsonPrimitive(20), R.string.tool_settings_default_limit),
        EditableDefaultSpec("offset", JsonPrimitive(0), R.string.tool_settings_default_offset),
    )
    val entries: List<ToolRegistration> = listOf(
        ToolRegistration(
            specification = ToolSpecification(
                "search_lore",
                LoreToolPrompts.SearchLore,
                ToolGroup.Lore,
                effect = ToolEffect.ReadOnly,
                editableDefaults = mPageDefaults,
            ),
            order = 0, labelRes = R.string.agent_tool_search_lore,
            factory = { config, services -> SearchLoreTool(config, services.data) },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "read_lore_entry",
                LoreToolPrompts.ReadLoreEntry,
                ToolGroup.Lore,
                effect = ToolEffect.ReadOnly,
            ),
            order = 10, labelRes = R.string.agent_tool_read_lore_entry,
            factory = { config, services -> ReadLoreEntryTool(config, services.data) },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "search_chat",
                ChatToolPrompts.SearchChat,
                ToolGroup.Chat,
                effect = ToolEffect.ReadOnly,
            ),
            order = 20, labelRes = R.string.agent_tool_search_chat,
            factory = { config, services -> SearchChatTool(config, services.data) },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "read_messages",
                ChatToolPrompts.ReadMessages,
                ToolGroup.Chat,
                effect = ToolEffect.ReadOnly,
            ),
            order = 30, labelRes = R.string.agent_tool_read_messages,
            factory = { config, services -> ReadMessagesTool(config, services.data) },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "search_memory",
                MemoryToolPrompts.SearchMemory,
                ToolGroup.Memory,
                effect = ToolEffect.ReadOnly,
                editableDefaults = mPageDefaults,
            ),
            order = 40, labelRes = R.string.agent_tool_search_memory,
            factory = { config, services -> SearchMemoryTool(config, services.memory) },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "read_memory",
                MemoryToolPrompts.ReadMemory,
                ToolGroup.Memory,
                effect = ToolEffect.ReadOnly,
            ),
            order = 50, labelRes = R.string.agent_tool_read_memory,
            factory = { config, services -> ReadMemoryTool(config, services.memory) },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "upsert_memory",
                MemoryToolPrompts.UpsertMemory,
                ToolGroup.Memory,
                effect = ToolEffect.LocalWrite,
                writesMemory = true,
            ),
            order = 60, labelRes = R.string.agent_tool_upsert_memory,
            factory = { config, services -> UpsertMemoryTool(config, services.memory) },
            proposalHandler = { it.memory },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "correct_memory",
                MemoryToolPrompts.CorrectMemory,
                ToolGroup.Memory,
                effect = ToolEffect.LocalWrite,
                writesMemory = true,
            ),
            order = 70, labelRes = R.string.agent_tool_correct_memory,
            factory = { config, services -> CorrectMemoryTool(config, services.memory) },
            proposalHandler = { it.memory },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "invalidate_memory",
                MemoryToolPrompts.InvalidateMemory,
                ToolGroup.Memory,
                effect = ToolEffect.LocalWrite,
                writesMemory = true,
            ),
            order = 80, labelRes = R.string.agent_tool_invalidate_memory,
            factory = { config, services -> InvalidateMemoryTool(config, services.memory) },
            proposalHandler = { it.memory },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "promote_memory_scope",
                MemoryToolPrompts.PromoteMemoryScope,
                ToolGroup.Memory,
                effect = ToolEffect.LocalWrite,
                writesMemory = true,
            ),
            order = 90, labelRes = R.string.agent_tool_promote_memory_scope,
            factory = { config, services -> PromoteMemoryScopeTool(config, services.memory) },
            proposalHandler = { it.memory },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "roll_dice",
                MathToolPrompts.RollDice,
                ToolGroup.Utility,
                effect = ToolEffect.NonDeterministicRead,
            ),
            order = 100, labelRes = R.string.agent_tool_roll_dice,
            factory = { config, services -> RollDiceTool(config) },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "calculate",
                MathToolPrompts.Calculate,
                ToolGroup.Utility,
                effect = ToolEffect.ReadOnly,
            ),
            order = 110, labelRes = R.string.agent_tool_calculate,
            factory = { config, services -> CalculateTool(config) },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "get_scene_state",
                SceneToolPrompts.GetSceneState,
                ToolGroup.Scene,
                effect = ToolEffect.ReadOnly,
            ),
            order = 120, labelRes = R.string.agent_tool_get_scene_state,
            factory = { config, services -> GetSceneStateTool(config, services.scene) },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "update_scene_state",
                SceneToolPrompts.UpdateSceneState,
                ToolGroup.Scene,
                effect = ToolEffect.LocalWrite,
            ),
            order = 130, labelRes = R.string.agent_tool_update_scene_state,
            factory = { config, services -> UpdateSceneStateTool(config, services.scene) },
            proposalHandler = { it.scene },
        ),
        ToolRegistration(
            specification = ToolSpecification(
                "ask_user",
                InteractionToolPrompts.AskUser,
                ToolGroup.Interaction,
                effect = ToolEffect.NonDeterministicRead,
            ),
            order = 140, labelRes = R.string.agent_tool_ask_user,
            factory = { config, services -> AskUserTool(config) },
        ),
    )
    val catalog = ToolCatalog(entries)
}
