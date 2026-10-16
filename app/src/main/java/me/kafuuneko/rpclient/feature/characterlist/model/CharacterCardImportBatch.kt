package me.kafuuneko.rpclient.feature.characterlist.model

import me.kafuuneko.rpclient.libs.character.CharacterCardImportDraft

/** 一次角色卡导入选择对应的有效草稿与解析失败统计。 */
internal data class CharacterCardImportBatch(
    /** 批量导入解析成功、等待确认的角色草稿列表。 */
    val drafts: List<CharacterCardImportDraft>,
    /** 批量导入中无法解析的文件数量。 */
    val parseFailureCount: Int,
    /** 当前查询或统计包含的总数量。 */
    val totalCount: Int
)
