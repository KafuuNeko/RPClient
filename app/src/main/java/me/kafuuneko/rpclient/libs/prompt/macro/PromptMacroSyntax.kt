package me.kafuuneko.rpclient.libs.prompt.macro

import me.kafuuneko.rpclient.libs.macro.MacroExpander

/** Prompt 兼容语法只在模板内生效，插入的字面正文不会经过这些别名。 */
internal val promptMacroExpander = MacroExpander(
    mapOf(
        "<USER>" to "user",
        "<CHAR>" to "char",
        "<BOT>" to "char",
        "<GROUP>" to "group",
        "<CHARIFNOTGROUP>" to "charifnotgroup",
        "{0}" to "0",
    )
)
