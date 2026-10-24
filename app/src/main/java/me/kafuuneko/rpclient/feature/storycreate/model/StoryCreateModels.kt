package me.kafuuneko.rpclient.feature.storycreate.model

import me.kafuuneko.rpclient.libs.prompt.resolveCharacterUserMacros
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.model.LorebookWithEntries
import me.kafuuneko.rpclient.utils.filterLorebookGroups
import me.kafuuneko.rpclient.utils.matchesCharacterQuery

/** 创建 Story 时尚未持久化的角色与世界书选择。 */
data class StoryCreateForm(
    /** 供界面展示或持久化的标题。 */
    val title: String = "",
    /** 故事 Prompt 是否包含当前用户名称和用户设定。 */
    val includeUserPersona: Boolean = false,
    /** 每个角色当前采用的激活模式映射。 */
    val characterActivationModes: Map<Long, StoryCreateCharacterActivationMode> = emptyMap(),
    /** 当前已选中的世界书条目 ID 集合。 */
    val selectedLorebookEntryIds: Set<Long> = emptySet()
) {
    /** 当前已选中的角色 ID 集合。 */
    val selectedCharacterIds: Set<Long>
        get() = characterActivationModes.keys

    /** 查询指定角色的激活模式；若未显式指定则默认回退至 Auto。 */
    fun activationModeOf(characterId: Long): StoryCreateCharacterActivationMode {
        return characterActivationModes[characterId] ?: StoryCreateCharacterActivationMode.Auto
    }

    /** 切换指定角色的选中状态；首个选中的角色默认为主角（Primary）。 */
    fun toggleCharacterSelection(characterId: Long): StoryCreateForm {
        if (characterId in characterActivationModes) {
            return copy(characterActivationModes = characterActivationModes - characterId)
        }
        val initialMode = if (characterActivationModes.isEmpty()) {
            StoryCreateCharacterActivationMode.Primary
        } else {
            StoryCreateCharacterActivationMode.Auto
        }
        return copy(
            characterActivationModes = characterActivationModes + (characterId to initialMode)
        )
    }

    /** 设置指定角色的激活模式；若设为 Primary 则自动将既有主角降级为 Auto。 */
    fun setCharacterActivationMode(
        characterId: Long,
        activationMode: StoryCreateCharacterActivationMode
    ): StoryCreateForm {
        if (characterId !in characterActivationModes) return this
        val nextModes = if (activationMode == StoryCreateCharacterActivationMode.Primary) {
            characterActivationModes.mapValues { (id, currentMode) ->
                when {
                    id == characterId -> StoryCreateCharacterActivationMode.Primary
                    currentMode == StoryCreateCharacterActivationMode.Primary -> {
                        StoryCreateCharacterActivationMode.Auto
                    }
                    else -> currentMode
                }
            }
        } else {
            characterActivationModes + (characterId to activationMode)
        }
        return copy(characterActivationModes = nextModes)
    }
}

/** 新建 Story 页面中的角色激活方式，不暴露 Room 的持久化取值。 */
enum class StoryCreateCharacterActivationMode {
    /** 主角：常驻置顶注入，单篇故事仅允许一个主角。 */
    Primary,
    /** 常驻配角：常驻注入。 */
    Always,
    /** 自动匹配：根据正文提及关键词动态激活。 */
    Auto
}

/** 新建 Story 页面中的角色卡候选项。 */
data class StoryCreateCharacterItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 用于说明当前对象的描述文本。 */
    val description: String,
    /** 角色用于分类和搜索的标签列表。 */
    val tags: List<String>,
    /** 通过角色卡或业务关系绑定的世界书 ID。 */
    val linkedLorebookId: Long? = null,
    /** 已绑定世界书的显示名称。 */
    val linkedLorebookName: String? = null
)

/** 新建 Story 页面中可独立开启或关闭的世界书条目。 */
data class StoryCreateLorebookEntryItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 关联世界书的显示名称。 */
    val lorebookName: String,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 用于触发世界书条目的主关键词列表。 */
    val keywords: List<String>,
    /** 是否忽略关键词并始终激活当前世界书条目。 */
    val constant: Boolean,
    /** 当前对象在同类数据中的排序值。 */
    val order: Int,
    /** 当前内容相对聊天末尾的插入或扫描深度。 */
    val depth: Int
)

/** 新建 Story 页面中的世界书分组。 */
data class StoryCreateLorebookGroupItem(
    /** 关联世界书的唯一 ID。 */
    val lorebookId: Long,
    /** 关联世界书的显示名称。 */
    val lorebookName: String,
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: List<StoryCreateLorebookEntryItem>
) {
    val entryCount: Int
        get() = entries.size
}

/** 根据检索文本对世界书分组及条目进行匹配过滤。 */
internal fun List<StoryCreateLorebookGroupItem>.filterForQuery(
    query: String
): List<StoryCreateLorebookGroupItem> = filterLorebookGroups(
    query = query,
    groupName = { it.lorebookName },
    entries = { it.entries },
    entrySearchFields = { entry ->
        sequenceOf(entry.lorebookName, entry.name, entry.content) +
            entry.keywords.asSequence()
    },
    copyWithEntries = { group, entries -> group.copy(entries = entries) }
)

/** 根据检索文本匹配角色名称、简介或标签。 */
internal fun List<StoryCreateCharacterItem>.filterCharactersForQuery(
    query: String
): List<StoryCreateCharacterItem> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return this
    return filter { character ->
        matchesCharacterQuery(character.name, character.description, character.tags, normalizedQuery)
    }
}

/** 将创建页角色激活模式转换为持久化值。 */
internal fun StoryCreateCharacterActivationMode.toStorageValue(): Int {
    return when (this) {
        StoryCreateCharacterActivationMode.Primary -> StoryCharacter.ACTIVATION_PRIMARY
        StoryCreateCharacterActivationMode.Always -> StoryCharacter.ACTIVATION_ALWAYS
        StoryCreateCharacterActivationMode.Auto -> StoryCharacter.ACTIVATION_AUTO
    }
}

/** 将持久化角色转换为当前页面的候选展示项。 */
internal fun Character.toStoryCreateCharacterItem(
    userName: String,
    lorebookNames: Map<Long, String>
): StoryCreateCharacterItem {
    // 保留角色绑定世界书的原始查询规则；显示名称与有效 ID 分别解析。
    return StoryCreateCharacterItem(
        id = id,
        name = name,
        description = resolveCharacterUserMacros(
            template = description,
            characterName = name,
            userName = userName
        ),
        tags = getCharacterTagList(),
        linkedLorebookId = characterLorebookId.takeIf { it > 0L },
        linkedLorebookName = lorebookNames[characterLorebookId]
    )
}

/** 按条目的 order 构建创建页世界书分组，并排除空分组。 */
internal fun List<LorebookWithEntries>.toStoryCreateLorebookGroups(): List<StoryCreateLorebookGroupItem> {
    // 保持各创建页原有字段投影，避免搜索能力或默认勾选状态变化。
    return map { lorebookWithEntries ->
        val lorebook = lorebookWithEntries.lorebook
        StoryCreateLorebookGroupItem(
            lorebookId = lorebook.id,
            lorebookName = lorebook.name,
            entries = lorebookWithEntries.entries
                .sortedBy { it.order }
                .map { entry ->
                    StoryCreateLorebookEntryItem(
                        id = entry.id,
                        lorebookName = lorebook.name,
                        name = entry.name,
                        content = entry.content,
                        keywords = entry.getKeywordList(),
                        constant = entry.constant,
                        order = entry.order,
                        depth = entry.depth
                    )
                }
        )
    }.filter { it.entries.isNotEmpty() }
}
