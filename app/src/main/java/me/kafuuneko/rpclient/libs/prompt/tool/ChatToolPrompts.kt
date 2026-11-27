package me.kafuuneko.rpclient.libs.prompt.tool


/** 聊天检索工具的模型说明与参数契约，文本不参与宏展开。 */
object ChatToolPrompts {
    val SearchChat = ToolPromptSpec(
        description = "Search only committed text in the current conversation. Returned cursors must be passed unchanged.",
        parameters = objectSchema(
            "query" to string(1, 200),
            "cursor" to string(1, 2000),
            required = setOf("query"),
        ),
    )

    val ReadMessages = ToolPromptSpec(
        description = "Read a current-conversation message and an optional bounded neighbouring window. Text is paged; IDs from another conversation are rejected.",
        parameters = objectSchema(
            "message_id" to integer(1),
            "context" to integer(0, 5),
            "cursor" to integer(0),
            required = setOf("message_id"),
        ),
    )

}
