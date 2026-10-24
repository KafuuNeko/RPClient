package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/**
 * 群聊世界书上下文数据模型。
 *
 * @property entries 候选世界书条目列表
 * @property lorebooks 关联的世界书实体映射
 * @property recursiveLorebookIds 开启了递归扫描的世界书 ID 集合
 */
internal data class GroupLorebookContext(
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: List<LorebookEntry>,
    /** 当前页面或流程可使用的世界书列表。 */
    val lorebooks: Map<Long, Lorebook>,
    /** 本轮实际参与递归扫描的世界书 ID 集合。 */
    val recursiveLorebookIds: Set<Long>
)
