package me.kafuuneko.rpclient.feature.groupchat.model

/** 一次成员拖动的原始顺序与最终顺序快照。 */
internal data class PendingMemberOrder(
    /** 应用排序或筛选前的角色 ID 顺序。 */
    val originalCharacterIds: List<Long>,
    /** 应用业务规则后得到的角色 ID 顺序。 */
    val orderedCharacterIds: List<Long>
)
