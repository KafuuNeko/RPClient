package me.kafuuneko.rpclient.libs.llm.model

/** Provider 的工具能力选择；Auto 不发起额外付费探测，未知能力由运行入口提示。 */
enum class ToolCallingSetting { Auto, Enabled, Disabled }
