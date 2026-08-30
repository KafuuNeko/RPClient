package me.kafuuneko.rpclient.feature.storycreate.model

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
