package me.kafuuneko.rpclient.libs.story

import com.google.gson.annotations.SerializedName

/** RPClient 故事归档 V2 的顶层传输模型。 */
data class StoryArchive(
    /** 导入或导出内容采用的文件格式。 */
    @field:SerializedName("format")
    val format: String = FORMAT,
    /** 兼容数据声明的格式版本。 */
    @field:SerializedName("version")
    val version: Int = VERSION,
    /** 当前页面展示或编辑的故事数据。 */
    @field:SerializedName("story")
    val story: ArchivedStory,
    /** Prompt 中可用于识别相关角色的提示列表。 */
    @field:SerializedName("characterHints")
    val characterHints: List<StoryCharacterHint> = emptyList(),
    /** Prompt 中可用于识别相关世界书的提示列表。 */
    @field:SerializedName("lorebookHints")
    val lorebookHints: List<StoryLorebookHint> = emptyList()
) {
    companion object {
        /** 故事归档标识字符串。 */
        const val FORMAT = "rpclient_story"
        /** 故事归档协议版本号。 */
        const val VERSION = 2
    }
}

/** 故事归档中可独立恢复的层级正文与 Story 级上下文设置。 */
data class ArchivedStory(
    /** 供界面展示或持久化的标题。 */
    @field:SerializedName("title")
    val title: String,
    /** 长期参与故事 Prompt 构建的记忆内容。 */
    @field:SerializedName("memory")
    val memory: String = "",
    /** 按指定位置注入故事 Prompt 的作者注释。 */
    @field:SerializedName("authorNote")
    val authorNote: String = "",
    /** 当前会话或故事使用的摘要内容。 */
    @field:SerializedName("summary")
    val summary: String = "",
    /** 故事 Prompt 是否包含当前用户名称和用户设定。 */
    @field:SerializedName("includeUserPersona")
    val includeUserPersona: Boolean = false,
    /** 尚未归入任何卷的章节列表。 */
    @field:SerializedName("ungroupedChapters")
    val ungroupedChapters: List<ArchivedChapter> = emptyList(),
    /** 当前故事包含的卷结构列表。 */
    @field:SerializedName("volumes")
    val volumes: List<ArchivedVolume> = emptyList()
) {
    val chapterCount: Int
        get() = ungroupedChapters.size + volumes.sumOf { it.chapters.size }

    val totalCharacterCount: Int
        get() = ungroupedChapters.sumOf { it.content.length } +
            volumes.sumOf { volume -> volume.chapters.sumOf { it.content.length } }
}

/** 归档中的一个分卷；数组顺序就是分卷和卷内章节顺序。 */
data class ArchivedVolume(
    /** 供界面展示或持久化的标题。 */
    @field:SerializedName("title")
    val title: String,
    /** 当前故事或卷包含的章节列表。 */
    @field:SerializedName("chapters")
    val chapters: List<ArchivedChapter> = emptyList()
)

/** 归档中的一个章节。 */
data class ArchivedChapter(
    /** 供界面展示或持久化的标题。 */
    @field:SerializedName("title")
    val title: String,
    /** 当前对象承载的正文内容。 */
    @field:SerializedName("content")
    val content: String,
    /** 用户为下一次故事续写提供的临时指导。 */
    @field:SerializedName("continuationGuidance")
    val continuationGuidance: String = ""
)

/** 归档中的角色匹配提示，不包含完整角色卡内容。 */
data class StoryCharacterHint(
    /** 供界面展示和业务识别的名称。 */
    @field:SerializedName("name")
    val name: String,
    /** 用于匹配同一份业务内容的稳定指纹。 */
    @field:SerializedName("fingerprint")
    val fingerprint: String,
    /** 当前角色或条目的激活模式。 */
    @field:SerializedName("activationMode")
    val activationMode: String
)

/** 归档中的世界书条目匹配提示，不包含条目正文。 */
data class StoryLorebookHint(
    /** 关联世界书的显示名称。 */
    @field:SerializedName("lorebookName")
    val lorebookName: String,
    /** 当前世界书条目的显示名称。 */
    @field:SerializedName("entryName")
    val entryName: String,
    /** 用于匹配同一份业务内容的稳定指纹。 */
    @field:SerializedName("fingerprint")
    val fingerprint: String
)

/** 故事导入来源类型。 */
enum class StoryImportType {
    Text,
    Archive
}

/**
 * 已完成解析但尚未写入 Room 的结构化导入草稿。
 *
 * 纯文本导入同样转换为单章节结构，避免在导入确认阶段保留第二份连续正文事实来源。
 */
data class StoryImportDraft(
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 长期参与故事 Prompt 构建的记忆内容。 */
    val memory: String = "",
    /** 按指定位置注入故事 Prompt 的作者注释。 */
    val authorNote: String = "",
    /** 当前会话或故事使用的摘要内容。 */
    val summary: String = "",
    /** 故事 Prompt 是否包含当前用户名称和用户设定。 */
    val includeUserPersona: Boolean = false,
    /** 尚未归入任何卷的章节列表。 */
    val ungroupedChapters: List<ArchivedChapter> = emptyList(),
    /** 当前故事包含的卷结构列表。 */
    val volumes: List<ArchivedVolume> = emptyList(),
    /** Prompt 中可用于识别相关角色的提示列表。 */
    val characterHints: List<StoryCharacterHint> = emptyList(),
    /** Prompt 中可用于识别相关世界书的提示列表。 */
    val lorebookHints: List<StoryLorebookHint> = emptyList(),
    /** 当前对象所属的业务类型。 */
    val type: StoryImportType
) {
    val chapterCount: Int
        get() = ungroupedChapters.size + volumes.sumOf { it.chapters.size }

    val totalCharacterCount: Int
        get() = ungroupedChapters.sumOf { it.content.length } +
            volumes.sumOf { volume -> volume.chapters.sumOf { it.content.length } }
}
