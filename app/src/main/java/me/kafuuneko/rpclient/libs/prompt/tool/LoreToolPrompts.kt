package me.kafuuneko.rpclient.libs.prompt.tool


/** 世界书工具的模型说明与参数契约，文本不参与宏展开。 */
object LoreToolPrompts {
    val SearchLore = ToolPromptSpec(
        description = "Search enabled lore entries for this conversation. Returns IDs and short excerpts; use the cursor for the next page.",
        parameters = searchSchema(),
    )

    val ReadLoreEntry = ToolPromptSpec(
        description = "Read an enabled lore entry by ID. Text pages preserve the original content; cursor is a character offset.",
        parameters = objectSchema(
            "id" to integer(1),
            "cursor" to integer(0),
            required = setOf("id"),
        ),
    )

}
