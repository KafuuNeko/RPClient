package me.kafuuneko.rpclient.feature.characteredit.model

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.utils.trimmedNotBlank
import me.kafuuneko.rpclient.utils.toJsonString

/**
 * 角色编辑器表单模型。
 *
 * 数值型 depth prompt 字段保留为字符串以支持未完成输入；列表字段在转为 Room 实体时
 * 会去除空白项，角色卡未知扩展通过 [extensionsJson] 原样保留。
 */
data class CharacterEditForm(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long = 0L,
    /** 供界面展示和业务识别的名称。 */
    val name: String = "",
    /** 用于展示角色或成员的头像引用。 */
    val avatar: String = "",
    /** 开始编辑前保存的头像引用。 */
    val originalAvatar: String = "",
    /** 角色用于分类和搜索的标签列表。 */
    val tags: List<String> = emptyList(),
    /** 用于说明当前对象的描述文本。 */
    val description: String = "",
    /** 作者提供的角色使用说明和备注。 */
    val creatorNotes: String = "",
    /** 角色的性格与行为设定。 */
    val personality: String = "",
    /** 角色对话发生的场景设定。 */
    val scenario: String = "",
    /** 角色可用的主开场白列表。 */
    val firstMessages: List<String> = emptyList(),
    /** 用于约束角色语气和格式的示例对话。 */
    val examplesOfDialogue: String = "",
    /** 追加在聊天历史之后的角色级提示词。 */
    val postHistoryInstructions: String = "",
    /** 角色级系统提示词覆盖内容。 */
    val systemPrompt: String = "",
    /** 角色卡作者信息，仅用于元数据展示和导出。 */
    val creator: String = "",
    /** 角色卡作者声明的内容版本。 */
    val characterVersion: String = "",
    /** 角色卡提供的备用开场白列表。 */
    val alternateGreetings: List<String> = emptyList(),
    /** 用于兼容第三方格式的扩展字段 JSON。 */
    val extensionsJson: String = "{}",
    /** 需要插入聊天历史内部的角色附加提示词正文。 */
    val depthPromptPrompt: String = "",
    /** 角色附加提示词相对聊天末尾的插入深度。 */
    val depthPromptDepth: String = "4",
    /** 角色附加提示词使用的消息角色。 */
    val depthPromptRole: String = "0",
    /** 角色卡直接绑定的世界书 ID；未绑定时为 0。 */
    val characterLorebookId: Long = 0L,
    /** 角色默认关联的模型配置 ID；未关联时为空。 */
    val llmProviderId: Long = 0L
) {
    /** ID 为 0 表示尚未写入数据库的新角色。 */
    val isNew: Boolean
        get() = id == 0L

    companion object {
        /** 从 Room 角色实体及独立的模型关联恢复可编辑表单。 */
        fun from(
            character: Character,
            llmProviderId: Long = 0L
        ) = CharacterEditForm(
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
            characterLorebookId = character.characterLorebookId,
            llmProviderId = llmProviderId
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
