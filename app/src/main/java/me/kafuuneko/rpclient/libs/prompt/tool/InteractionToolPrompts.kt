package me.kafuuneko.rpclient.libs.prompt.tool

import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema

/** 用户提问工具的模型说明与参数契约，文本不参与宏展开。 */
object InteractionToolPrompts {
    val AskUser = ToolPromptSpec(
        description = "Ask the user a question and suspend this run for up to 24 hours. An answer is input only and never grants write confirmation.",
        parameters = objectSchema(
            "question" to string(1, 2000),
            "options" to ToolSchema(
                ToolSchema.Type.Array,
                items = string(1, 200),
                maxItems = 8,
            ),
            required = setOf("question"),
        ),
    )

}
