package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.chat.ChatCharacterMatcher
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/**
 * 故事归档的跨安装关联匹配规则，供菜单导入与完整备份恢复共用。
 * - 优先使用唯一内容指纹，再使用唯一名称，不任意挑选歧义候选。
 * - 候选指纹仅计算一次，返回输入对象，允许备份在尚未分配主键时登记关系。
 * @param characters 当前可关联的角色。
 * @param entries 当前可关联的世界书条目。
 * @param lorebooks 当前可关联的世界书。
 */
internal class StoryArchiveMatcher(
    characters: List<Character> = emptyList(),
    entries: List<LorebookEntry> = emptyList(),
    lorebooks: List<Lorebook> = emptyList()
) {
    private val mCharacters = characters
    private val mEntries = entries
    private val mCharactersByFingerprint = mCharacters.groupBy(ChatCharacterMatcher::fingerprintOf)
    private val mEntriesByFingerprint = mEntries.groupBy { entry -> storyTextHash(entry.content) }
    private val mBookNames = lorebooks.associate { book -> book.id to book.name }

    /**
     * 匹配角色提示，未匹配或名称仍有歧义时返回 null。
     * @param hint 故事文档内的角色提示。
     * @return 唯一匹配的原始对象。
     */
    fun matchCharacter(hint: StoryCharacterHint): Character? {
        val fingerprintMatch =
            mCharactersByFingerprint[hint.fingerprint.lowercase()].orEmpty().singleOrNull()
        return fingerprintMatch ?: mCharacters.singleOrNull { character ->
            character.name.trim().equals(hint.name.trim(), ignoreCase = true)
        }
    }

    /**
     * 匹配世界书条目提示；名称回退同时限定所属世界书。
     * @param hint 故事文档内的条目提示。
     * @return 唯一匹配的原始对象，无法确定时返回 null。
     */
    fun matchLorebookEntry(hint: StoryLorebookHint): LorebookEntry? {
        val fingerprintMatch =
            mEntriesByFingerprint[hint.fingerprint.lowercase()].orEmpty().singleOrNull()
        return fingerprintMatch ?: mEntries.singleOrNull { entry ->
            entry.name.trim().equals(hint.entryName.trim(), ignoreCase = true)
                && mBookNames[entry.lorebookId]?.trim()
                ?.equals(hint.lorebookName.trim(), ignoreCase = true) == true
        }
    }
}
