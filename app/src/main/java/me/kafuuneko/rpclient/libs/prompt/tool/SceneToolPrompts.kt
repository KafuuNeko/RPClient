package me.kafuuneko.rpclient.libs.prompt.tool

import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema

/** 剧情工具的模型说明与参数契约，文本不参与宏展开。 */
object SceneToolPrompts {
    val GetSceneState = ToolPromptSpec(
        description = "Read current scene state plus this run's proposed changes, with an optimistic revision number.",
        parameters = objectSchema(),
    )

    val UpdateSceneState = ToolPromptSpec(
        description = "Apply bounded path changes to location, time, attributes, inventory, goals or custom groups. value_json is a JSON literal; omit it to delete. expectedRevision prevents lost updates. Quote committed user evidence (omit sourceMessageId to auto-match a recent user message) or propose against this final reply with sourceMessageId=0.",
        parameters = objectSchema(
            "expectedRevision" to integer(0).copy(
                description = "Current scene revision returned by get_scene_state.",
            ),
            "sourceMessageId" to integer(0).copy(
                description = "Optional committed user message ID. Omit to auto-match a recent user message containing evidence, or pass 0 for a proposed change verified against your final reply.",
            ),
            "evidence" to string(1, 1000).copy(
                description = "Exact verbatim substring from a committed user message or your upcoming final reply.",
            ),
            "changes" to ToolSchema(
                ToolSchema.Type.Array,
                minItems = 1,
                maxItems = 30,
                items = objectSchema(
                    "path" to ToolSchema(
                        ToolSchema.Type.Array,
                        minItems = 1,
                        maxItems = 5,
                        items = string(1, 100),
                    ),
                    "value_json" to string(1, 4000),
                    required = setOf("path"),
                ),
            ),
            required = setOf("expectedRevision", "evidence", "changes"),
        ),
    )

}
