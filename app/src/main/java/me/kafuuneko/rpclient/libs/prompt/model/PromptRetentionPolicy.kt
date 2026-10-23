package me.kafuuneko.rpclient.libs.prompt.model

/** 单聊和群聊共享的 Prompt 保留优先级策略与常量定义。 */
internal object PromptRetentionPolicy {
    /** 普通示例对话（Example Dialogue）的保留优先级（低于聊天历史）。 */
    const val EXAMPLE = 10

    /** 聊天历史消息（Chat History）的标准保留优先级。 */
    const val HISTORY = 100

    /** 固定置顶示例对话（Pinned Example）的保留优先级（高于聊天历史）。 */
    const val PINNED_EXAMPLE = 200

    /**
     * 根据示例对话保留策略获取对应的数值优先级。
     *
     * @param behavior 示例对话行为策略枚举
     * @return 对应的数值优先级
     */
    fun examplePriority(behavior: ExampleDialogueBehavior): Int {
        return when (behavior) {
            ExampleDialogueBehavior.Normal -> EXAMPLE
            ExampleDialogueBehavior.Pinned -> PINNED_EXAMPLE
            ExampleDialogueBehavior.Disabled -> error("Disabled examples have no retention priority")
        }
    }
}
