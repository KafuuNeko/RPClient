package me.kafuuneko.rpclient.feature.promptpreset.model

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.prompt.PromptType

/** 预设字段按用途组织，分类顺序不参与实际 Prompt 拼接。 */
enum class PromptPresetCategory(@param:StringRes val titleRes: Int) {
    Basic(R.string.prompt_category_basic),
    Generation(R.string.prompt_category_generation),
    Memory(R.string.prompt_category_memory),
    Format(R.string.prompt_category_format),
}

/** 场景只用于筛选，共用字段仍编辑同一个预设值。 */
enum class PromptPresetScope(@param:StringRes val titleRes: Int) {
    All(R.string.filter_all),
    Chat(R.string.prompt_scope_chat),
    Group(R.string.prompt_scope_group),
    Story(R.string.prompt_scope_story),
}

/** 分类内部的轻量小标题，基础指令直接展示字段。 */
enum class PromptPresetSection(
    val category: PromptPresetCategory,
    @param:StringRes val titleRes: Int? = null
) {
    Basic(PromptPresetCategory.Basic),
    CharacterContinuation(
        PromptPresetCategory.Generation,
        R.string.prompt_section_character_continuation
    ),
    UserWriting(PromptPresetCategory.Generation, R.string.prompt_section_user_writing),
    StoryContinuation(PromptPresetCategory.Generation, R.string.prompt_section_story_continuation),
    EmptyInput(PromptPresetCategory.Generation, R.string.prompt_section_empty_input),
    SummaryGeneration(PromptPresetCategory.Memory, R.string.prompt_section_summary_generation),
    MemoryInjection(PromptPresetCategory.Memory, R.string.prompt_section_memory_injection),
    ContentFormat(PromptPresetCategory.Format, R.string.prompt_section_content_format),
    HistoryBoundary(PromptPresetCategory.Format, R.string.prompt_section_history_boundary),
}

/** 展示目录描述字段用途与场景，不修改领域类型、默认值或稳定存储键。 */
data class PromptPresetField(
    val type: PromptType,
    @param:StringRes val titleRes: Int,
    val section: PromptPresetSection,
    val scopes: List<PromptPresetScope>,
)

private val chatScopes = listOf(PromptPresetScope.Chat, PromptPresetScope.Group)
private val allScopes = chatScopes + PromptPresetScope.Story
private val singleChatScope = listOf(PromptPresetScope.Chat)
private val groupChatScope = listOf(PromptPresetScope.Group)
private val storyScope = listOf(PromptPresetScope.Story)

/** 每个字段只归属一个用途分区；适用场景按当前构建器的读取关系声明。 */
val promptPresetFields = listOf(
    PromptPresetField(
        type = PromptType.Main,
        titleRes = R.string.prompt_main_title,
        section = PromptPresetSection.Basic,
        scopes = chatScopes,
    ),
    PromptPresetField(
        type = PromptType.Auxiliary,
        titleRes = R.string.prompt_auxiliary_title,
        section = PromptPresetSection.Basic,
        scopes = chatScopes,
    ),
    PromptPresetField(
        type = PromptType.PostHistory,
        titleRes = R.string.prompt_post_history_title,
        section = PromptPresetSection.Basic,
        scopes = chatScopes,
    ),
    PromptPresetField(
        type = PromptType.GroupNudge,
        titleRes = R.string.prompt_catalog_group_nudge_title,
        section = PromptPresetSection.Basic,
        scopes = groupChatScope,
    ),
    PromptPresetField(
        type = PromptType.StoryMain,
        titleRes = R.string.prompt_story_main_title,
        section = PromptPresetSection.Basic,
        scopes = storyScope,
    ),
    PromptPresetField(
        type = PromptType.ContinueNudge,
        titleRes = R.string.prompt_catalog_continue_title,
        section = PromptPresetSection.CharacterContinuation,
        scopes = chatScopes,
    ),
    PromptPresetField(
        type = PromptType.Impersonation,
        titleRes = R.string.prompt_catalog_impersonation_title,
        section = PromptPresetSection.UserWriting,
        scopes = singleChatScope,
    ),
    PromptPresetField(
        type = PromptType.UserWriting,
        titleRes = R.string.prompt_catalog_user_writing_title,
        section = PromptPresetSection.UserWriting,
        scopes = singleChatScope,
    ),
    PromptPresetField(
        type = PromptType.GroupImpersonation,
        titleRes = R.string.prompt_catalog_group_impersonation_title,
        section = PromptPresetSection.UserWriting,
        scopes = groupChatScope,
    ),
    PromptPresetField(
        type = PromptType.GroupUserWriting,
        titleRes = R.string.prompt_catalog_group_user_writing_title,
        section = PromptPresetSection.UserWriting,
        scopes = groupChatScope,
    ),
    PromptPresetField(
        type = PromptType.StoryContinue,
        titleRes = R.string.prompt_story_continue_title,
        section = PromptPresetSection.StoryContinuation,
        scopes = storyScope,
    ),
    PromptPresetField(
        type = PromptType.StoryContinuationGuidance,
        titleRes = R.string.prompt_catalog_story_guidance_title,
        section = PromptPresetSection.StoryContinuation,
        scopes = storyScope,
    ),
    PromptPresetField(
        type = PromptType.ReplaceEmptyMessage,
        titleRes = R.string.prompt_catalog_empty_input_title,
        section = PromptPresetSection.EmptyInput,
        scopes = singleChatScope,
    ),
    PromptPresetField(
        type = PromptType.Summarize,
        titleRes = R.string.prompt_catalog_summarize_title,
        section = PromptPresetSection.SummaryGeneration,
        scopes = singleChatScope,
    ),
    PromptPresetField(
        type = PromptType.GroupSummarize,
        titleRes = R.string.prompt_catalog_group_summarize_title,
        section = PromptPresetSection.SummaryGeneration,
        scopes = groupChatScope,
    ),
    PromptPresetField(
        type = PromptType.StorySummarize,
        titleRes = R.string.prompt_catalog_story_summarize_title,
        section = PromptPresetSection.SummaryGeneration,
        scopes = storyScope,
    ),
    PromptPresetField(
        type = PromptType.SummaryInjection,
        titleRes = R.string.prompt_catalog_summary_injection_title,
        section = PromptPresetSection.MemoryInjection,
        scopes = chatScopes,
    ),
    PromptPresetField(
        type = PromptType.StoryMemory,
        titleRes = R.string.prompt_catalog_story_memory_title,
        section = PromptPresetSection.MemoryInjection,
        scopes = storyScope,
    ),
    PromptPresetField(
        type = PromptType.StorySummary,
        titleRes = R.string.prompt_catalog_story_summary_title,
        section = PromptPresetSection.MemoryInjection,
        scopes = storyScope,
    ),
    PromptPresetField(
        type = PromptType.WorldInfoFormat,
        titleRes = R.string.prompt_world_info_format_title,
        section = PromptPresetSection.ContentFormat,
        scopes = allScopes,
    ),
    PromptPresetField(
        type = PromptType.ScenarioFormat,
        titleRes = R.string.prompt_scenario_format_title,
        section = PromptPresetSection.ContentFormat,
        scopes = chatScopes,
    ),
    PromptPresetField(
        type = PromptType.PersonalityFormat,
        titleRes = R.string.prompt_personality_format_title,
        section = PromptPresetSection.ContentFormat,
        scopes = chatScopes,
    ),
    PromptPresetField(
        type = PromptType.UserPersonaFormat,
        titleRes = R.string.prompt_user_persona_format_title,
        section = PromptPresetSection.ContentFormat,
        scopes = allScopes,
    ),
    PromptPresetField(
        type = PromptType.NewChat,
        titleRes = R.string.prompt_catalog_new_chat_title,
        section = PromptPresetSection.HistoryBoundary,
        scopes = singleChatScope,
    ),
    PromptPresetField(
        type = PromptType.NewGroupChat,
        titleRes = R.string.prompt_catalog_new_group_title,
        section = PromptPresetSection.HistoryBoundary,
        scopes = groupChatScope,
    ),
    PromptPresetField(
        type = PromptType.NewExampleChat,
        titleRes = R.string.prompt_catalog_new_example_title,
        section = PromptPresetSection.HistoryBoundary,
        scopes = chatScopes,
    ),
)

private val fieldsByType = promptPresetFields.associateBy { it.type }

/**
 * 根据领域类型获取同一份展示定义，保证列表与编辑器的名称一致。
 * @param type 已支持的提示词类型。
 * @return 对应字段的用途、作用范围和文案定义。
 */
fun promptPresetField(type: PromptType): PromptPresetField = fieldsByType.getValue(type)
