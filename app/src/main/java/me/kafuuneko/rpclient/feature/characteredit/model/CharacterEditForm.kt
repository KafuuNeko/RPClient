package me.kafuuneko.rpclient.feature.characteredit.model

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.utils.trimmedNotBlank
import me.kafuuneko.rpclient.utils.toJsonString

/**
 * 角色编辑器表单模型。
 *
 * 数值型 depth prompt 字段保留为字符串以支持未完成输入；列表字段在转为 Room 实体时
 * 会去除空白项，角色卡未知扩展通过 [extensionsJson] 原样保留。
 */
data class CharacterEditForm(
    val id: Long = 0L,
    val name: String = "",
    val avatar: String = "",
    val originalAvatar: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val creatorNotes: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessages: List<String> = emptyList(),
    val examplesOfDialogue: String = "",
    val postHistoryInstructions: String = "",
    val systemPrompt: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val extensionsJson: String = "{}",
    val depthPromptPrompt: String = "",
    val depthPromptDepth: String = "4",
    val depthPromptRole: String = "0",
    val characterLorebookId: Long = 0L
) {
    /** ID 为 0 表示尚未写入数据库的新角色。 */
    val isNew: Boolean
        get() = id == 0L

    companion object {
        /** 从 Room 角色实体恢复可编辑表单。 */
        fun from(character: Character) = CharacterEditForm(
            id = character.id,
            name = character.name,
            avatar = character.avatar,
            originalAvatar = character.avatar,
            tags = character.getCharacterTagList(),
            description = character.description,
            creatorNotes = character.creatorNotes,
            personality = character.personality,
            scenario = character.scenario,
            firstMessages = character.getFirstMessageList(),
            examplesOfDialogue = character.examplesOfDialogue,
            postHistoryInstructions = character.postHistoryInstructions,
            systemPrompt = character.systemPrompt,
            creator = character.creator,
            characterVersion = character.characterVersion,
            alternateGreetings = character.getAlternateGreetingList(),
            extensionsJson = character.extensionsJson,
            depthPromptPrompt = character.depthPromptPrompt,
            depthPromptDepth = character.depthPromptDepth.toString(),
            depthPromptRole = character.depthPromptRole.toString(),
            characterLorebookId = character.characterLorebookId
        )
    }

    /** 清洗用户输入并转换为可持久化角色实体。 */
    fun toCharacter(): Character {
        return Character(
            id = id,
            name = name.trim(),
            avatar = avatar.trim(),
            characterTags = Gson().toJsonString(tags.trimmedNotBlank()),
            description = description.trim(),
            creatorNotes = creatorNotes.trim(),
            personality = personality.trim(),
            scenario = scenario.trim(),
            firstMessages = firstMessages
                .trimmedNotBlank()
                .joinToString("<START>"),
            examplesOfDialogue = examplesOfDialogue.trim(),
            postHistoryInstructions = postHistoryInstructions.trim(),
            systemPrompt = systemPrompt.trim(),
            creator = creator.trim(),
            characterVersion = characterVersion.trim(),
            alternateGreetings = Gson().toJsonString(alternateGreetings.trimmedNotBlank()),
            extensionsJson = extensionsJson.trim().ifBlank { "{}" },
            depthPromptPrompt = depthPromptPrompt.trim(),
            depthPromptDepth = depthPromptDepth.trim().toIntOrNull()?.coerceAtLeast(0) ?: 4,
            depthPromptRole = depthPromptRole.trim().toIntOrNull()?.coerceIn(0, 2) ?: 0,
            characterLorebookId = characterLorebookId
        )
    }
}

/** 比较清洗后的业务字段，忽略无意义的首尾空白差异。 */
fun CharacterEditForm.hasUnsavedChangesFrom(initialForm: CharacterEditForm): Boolean {
    return toComparableForm() != initialForm.toComparableForm()
}

/** 生成仅用于未保存变更比较的标准化表单。 */
fun CharacterEditForm.toComparableForm(): CharacterEditForm {
    return copy(
        name = name.trim(),
        avatar = avatar.trim(),
        originalAvatar = originalAvatar.trim(),
        tags = tags.trimmedNotBlank(),
        description = description.trim(),
        creatorNotes = creatorNotes.trim(),
        creator = creator.trim(),
        characterVersion = characterVersion.trim(),
        personality = personality.trim(),
        scenario = scenario.trim(),
        firstMessages = firstMessages.trimmedNotBlank(),
        examplesOfDialogue = examplesOfDialogue.trim(),
        postHistoryInstructions = postHistoryInstructions.trim(),
        systemPrompt = systemPrompt.trim(),
        alternateGreetings = alternateGreetings.trimmedNotBlank(),
        extensionsJson = extensionsJson.trim().ifBlank { "{}" },
        depthPromptPrompt = depthPromptPrompt.trim(),
        depthPromptDepth = depthPromptDepth.trim(),
        depthPromptRole = depthPromptRole.trim()
    )
}
