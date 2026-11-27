package me.kafuuneko.rpclient.libs.prompt.tool


/** 计算与骰子工具的模型说明与参数契约，文本不参与宏展开。 */
object MathToolPrompts {
    val RollDice = ToolPromptSpec(
        description = "Roll NdM dice with optional kh/kl and a signed modifier, e.g. 4d6kh3+2. At most 100 dice and 10000 sides. The saved result is replayed on retry.",
        parameters = objectSchema(
            "notation" to string(1, 80),
            required = setOf("notation"),
        ),
    )

    val Calculate = ToolPromptSpec(
        description = "Evaluate bounded arithmetic: + - * / % ^, parentheses, pi, e, sqrt, abs, sin, cos, tan, ln, log10, floor, ceil, round. No code or variable access.",
        parameters = objectSchema(
            "expression" to string(1, 1024),
            required = setOf("expression"),
        ),
    )

}
