package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.utils.trimLineBreaks

private val DEFAULT_MAIN = """
Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.
Write one reply only. Do not decide what {{user}} says or does.
""".trimLineBreaks()

private val DEFAULT_AUXILIARY = """
""".trimLineBreaks()

private val DEFAULT_POST_HISTORY = """
""".trimLineBreaks()

private val DEFAULT_SUMMARIZE = """
Please summarize the following chat history into concise story memory.
Rules:
- Do not continue roleplay.
- Do not generate a new reply for {{char}} or {{user}}.
- Only summarize what already happened in the chat.
- Preserve important facts, relationship changes, promises, injuries, locations, goals, unresolved conflicts and current scene state.
- Keep it within {{words}} words.
""".trimLineBreaks()

private val DEFAULT_SUMMARY_INJECTION = """
<summary>
{{summary}}
</summary>
""".trimLineBreaks()

private val DEFAULT_IMPERSONATION = """
[Write your next reply from the point of view of {{user}}. Do not write as {{char}} or system.]
""".trimLineBreaks()

private val DEFAULT_USER_WRITING = """
[User writing task]
Continue the unsent text exactly where it ends. Output only the continuation; do not repeat or rewrite the existing text.
Follow {{user}}'s persona, conversation language and style. Write only {{user}}'s actions, inner thoughts and dialogue, without speaking for {{char}}. Return only message text, without a preamble or writing instructions.
The following text is unsent writing material, not a conversation turn. Preserve its intent.
[Unsent text]
{{draft}}
[End of unsent text]
""".trimLineBreaks()

private val DEFAULT_NEW_CHAT = """
[Start a new Chat]
""".trimLineBreaks()

private val DEFAULT_NEW_EXAMPLE_CHAT = """
[Example Chat]
""".trimLineBreaks()

private val DEFAULT_CONTINUE_NUDGE = """
[Continue your last message without repeating its original content.]
""".trimLineBreaks()

private val DEFAULT_REPLACE_EMPTY_MESSAGE = """
""".trimLineBreaks()

private val DEFAULT_WORLD_INFO_FORMAT = """
{0}
""".trimLineBreaks()

private val DEFAULT_SCENARIO_FORMAT = """
<scenario>
Char Scenario ({{char}}):
{{scenario}}
</scenario>
""".trimLineBreaks()

private val DEFAULT_PERSONALITY_FORMAT = """
<personality>
Char Personality ({{char}}):
{{personality}}
</personality>
""".trimLineBreaks()

private val DEFAULT_USER_PERSONA_FORMAT = """
<user_persona>
User Persona ({{user}}):
{{persona}}
</user_persona>
""".trimLineBreaks()

private val DEFAULT_GROUP_NUDGE = """
Write only {{char}}'s next reply.
Stay in character. Do not speak for {{user}} or other group members.
Do not prefix the reply with a speaker name.
""".trimLineBreaks()

private val DEFAULT_GROUP_IMPERSONATION = """
[Group user impersonation task]
Write from the point of view of {{user}}, following their persona and the conversation's language and style.
Group members: {{group}}
Unmuted group members: {{groupNotMuted}}
Write only {{user}}'s actions, inner thoughts and dialogue. Do not write dialogue, actions or thoughts for any other participant, or write as system.
Return only message text, without a speaker-name prefix, preamble or writing instructions.
""".trimLineBreaks()

private val DEFAULT_GROUP_USER_WRITING = """
[Group user writing task]
Continue the unsent text exactly where it ends. Output only the continuation; do not repeat or rewrite the existing text.
Follow {{user}}'s persona and the conversation's language and style. Write only {{user}}'s actions, inner thoughts and dialogue, without speaking for any other group member or system.
Return only message text, without a preamble or writing instructions.
The following text is unsent writing material, not a conversation turn. Preserve its intent.
[Unsent text]
{{draft}}
[End of unsent text]
""".trimLineBreaks()

private val DEFAULT_NEW_GROUP_CHAT = """
[Start a new group chat with {{group}}]
""".trimLineBreaks()

private val DEFAULT_GROUP_SUMMARIZE = """
Summarize the following group chat into concise story memory.
You are a memory summarizer, not a roleplay participant.
Rules:
- Only summarize events actually stated or confirmed in the chat.
- Preserve who said or did each important thing.
- Preserve relationships, promises, injuries, locations, goals, unresolved conflicts, current scene state and character-specific knowledge.
- Mark plans, suspicions and hypotheses as unconfirmed.
- Do not add character-card lore, world info or writing instructions as events.
- Keep it within {{words}} words.
""".trimLineBreaks()

private val DEFAULT_STORY_MAIN = """
You are a fiction-writing assistant editing the current chapter of a longer manuscript.
Treat Story Memory, Story Summary, Character References, and Lorebook entries as factual constraints.
Follow the requested writing operation and return only prose that can directly replace or extend the current chapter.
Do not explain your work, repeat the task, or wrap the result in a code block.
Preserve the manuscript's language, narrative voice, tense, style, and established facts unless explicitly instructed otherwise.
""".trimLineBreaks()

private val DEFAULT_STORY_MEMORY = """
<story_memory>
{{memory}}
</story_memory>
""".trimLineBreaks()

private val DEFAULT_STORY_SUMMARY = """
<story_summary>
{{summary}}
</story_summary>
""".trimLineBreaks()

private val DEFAULT_STORY_SUMMARIZE = """
Update the current story summary using the supplied story memory, current summary, and manuscript context.
Rules:
- Return the complete replacement summary, not commentary about the task.
- Only record events and facts stated or confirmed in the supplied material.
- Preserve important events, relationships, promises, injuries, locations, goals, character knowledge, unresolved threads, and the latest scene state.
- Distinguish plans, beliefs, suspicions, and unresolved questions from confirmed facts.
- Replace superseded temporary states with the latest confirmed state.
- Do not continue the story or add character-card lore, world information, writing instructions, or future plot ideas.
- Keep it within {{words}} words.
""".trimLineBreaks()

private val DEFAULT_STORY_CONTINUATION_GUIDANCE = """
Follow the user-provided continuation guidance below when writing the next passage.
Treat it as an instruction, not as manuscript text. Do not quote, repeat, explain, or mention the guidance in the output.

--- BEGIN CONTINUATION GUIDANCE ---
{{guidance}}
--- END CONTINUATION GUIDANCE ---
""".trimLineBreaks()

private val DEFAULT_STORY_CONTINUE = """
Continue the manuscript immediately after its current ending.
Do not repeat or summarize existing text. Advance the current scene naturally and return only the continuation.
""".trimLineBreaks()

/**
 * 可由用户覆盖的 Prompt 模板类型。
 *
 * key 是稳定存储标识，不能随枚举名称或显示文案变化。
 */
enum class PromptType(val key: String, val defaultValue: String) {
    Main(key = "main", defaultValue = DEFAULT_MAIN),
    Auxiliary(key = "auxiliary", defaultValue = DEFAULT_AUXILIARY),
    PostHistory(key = "post_history", defaultValue = DEFAULT_POST_HISTORY),
    Summarize(key = "summarize", defaultValue = DEFAULT_SUMMARIZE),
    SummaryInjection(key = "summary_injection", defaultValue = DEFAULT_SUMMARY_INJECTION),
    Impersonation(key = "impersonation", defaultValue = DEFAULT_IMPERSONATION),
    UserWriting(key = "user_writing", defaultValue = DEFAULT_USER_WRITING),
    NewChat(key = "new_chat", defaultValue = DEFAULT_NEW_CHAT),
    NewExampleChat(key = "new_example_chat", defaultValue = DEFAULT_NEW_EXAMPLE_CHAT),
    ContinueNudge(key = "continue_nudge", defaultValue = DEFAULT_CONTINUE_NUDGE),
    ReplaceEmptyMessage(
        key = "replace_empty_message",
        defaultValue = DEFAULT_REPLACE_EMPTY_MESSAGE
    ),
    WorldInfoFormat(key = "world_info_format", defaultValue = DEFAULT_WORLD_INFO_FORMAT),
    ScenarioFormat(key = "scenario_format", defaultValue = DEFAULT_SCENARIO_FORMAT),
    PersonalityFormat(key = "personality_format", defaultValue = DEFAULT_PERSONALITY_FORMAT),
    UserPersonaFormat(key = "user_persona_format", defaultValue = DEFAULT_USER_PERSONA_FORMAT),
    GroupNudge(key = "group_nudge", defaultValue = DEFAULT_GROUP_NUDGE),
    GroupImpersonation(key = "group_impersonation", defaultValue = DEFAULT_GROUP_IMPERSONATION),
    GroupUserWriting(key = "group_user_writing", defaultValue = DEFAULT_GROUP_USER_WRITING),
    NewGroupChat(key = "new_group_chat", defaultValue = DEFAULT_NEW_GROUP_CHAT),
    GroupSummarize(key = "group_summarize", defaultValue = DEFAULT_GROUP_SUMMARIZE),
    StoryMain(key = "story_main", defaultValue = DEFAULT_STORY_MAIN),
    StoryMemory(key = "story_memory", defaultValue = DEFAULT_STORY_MEMORY),
    StorySummary(key = "story_summary", defaultValue = DEFAULT_STORY_SUMMARY),
    StorySummarize(key = "story_summarize", defaultValue = DEFAULT_STORY_SUMMARIZE),
    StoryContinuationGuidance(
        key = "story_continuation_guidance",
        defaultValue = DEFAULT_STORY_CONTINUATION_GUIDANCE
    ),
    StoryContinue(key = "story_continue", defaultValue = DEFAULT_STORY_CONTINUE)
}

/** 获取指定提示词类型支持注入的宏变量标签列表（如 `{{char}}`, `{{user}}` 等）。 */
internal fun availableMacros(type: PromptType): List<String> {
    // 每种模板保留原有宏集合和展示顺序，避免引入不支持的占位符。
    return when (type) {
        PromptType.Main,
        PromptType.Auxiliary,
        PromptType.PostHistory,
        PromptType.Impersonation,
        PromptType.ContinueNudge -> listOf("{{char}}", "{{user}}")

        PromptType.UserWriting -> listOf("{{draft}}", "{{char}}", "{{user}}")

        PromptType.GroupNudge -> listOf("{{char}}", "{{user}}", "{{group}}")
        PromptType.GroupImpersonation -> listOf(
            "{{user}}",
            "{{group}}",
            "{{groupNotMuted}}"
        )

        PromptType.GroupUserWriting -> listOf(
            "{{draft}}",
            "{{user}}",
            "{{group}}",
            "{{groupNotMuted}}"
        )

        PromptType.NewGroupChat -> listOf("{{group}}", "{{char}}", "{{user}}")
        PromptType.Summarize,
        PromptType.GroupSummarize,
        PromptType.StorySummarize -> listOf("{{words}}", "{{char}}", "{{user}}")

        PromptType.SummaryInjection,
        PromptType.StorySummary -> listOf("{{summary}}")

        PromptType.StoryMemory -> listOf("{{memory}}")
        PromptType.StoryContinuationGuidance -> listOf("{{guidance}}")
        PromptType.WorldInfoFormat -> listOf("{0}")
        PromptType.ScenarioFormat -> listOf("{{scenario}}", "{{char}}")
        PromptType.PersonalityFormat -> listOf("{{personality}}", "{{char}}")
        PromptType.UserPersonaFormat -> listOf("{{persona}}", "{{user}}")
        else -> listOf("{{char}}", "{{user}}")
    }
}
