package me.kafuuneko.rpclient.libs.agent.runtime

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.groupchat.GroupChatMacros
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuilder
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptContext
import me.kafuuneko.rpclient.libs.groupchat.cardMembers
import me.kafuuneko.rpclient.libs.llm.LLMProviderSelectionResolver
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository

/**
 * 群聊适配器保留外层成员顺序，每个成员独立运行内部工具循环。
 * - 等待用户时把剩余成员 ID 随检查点保存，进程重启不重新选择发言者。
 * - 恢复后为后继成员重建公共历史，只读取前一成员已提交的正文和共享状态。
 */
class GroupChatAgentRuntime(
    private val mManager: AgentRunManager,
    private val mGroups: GroupChatRepository,
    private val mProviders: LLMProviderSelectionResolver,
    private val mLorebooks: LorebookRepository,
    private val mRegex: RegexScriptRepository,
    private val mImages: MessageImageRuntime,
    private val mBuilder: GroupChatPromptBuilder,
) {
    /** 外层队列已选定的当前成员，不在工具循环里重选发言者。 */
    suspend fun run(input: AgentTurnInput) {
        require(input.conversation is ConversationRef.Group) { "Expected group conversation" }
        mManager.run(input)
    }

    /** 恢复当前等待成员，再依原顺序推进冻结队列；新等待会自然再次挂起。 */
    suspend fun resume(groupId: Long, answer: String, confirmed: Boolean) {
        val plan = mManager.resume(ConversationRef.Group(groupId), answer, confirmed)
        val pending = plan.input.remainingSpeakers
        for ((index, characterId) in pending.withIndex()) {
            val input =
                next(plan.input, characterId).copy(remainingSpeakers = pending.drop(index + 1))
            mManager.run(input)
        }
    }

    private suspend fun next(previous: AgentTurnInput, characterId: Long): AgentTurnInput {
        val prompt = requireNotNull(
            mGroups.getGroupChatPromptData(
                sessionId = previous.conversation.id,
                maxHistoryMessages = AppModel.maxPromptHistoryMessages.coerceAtLeast(0),
                keepLatestMessage = true
            )
        )
        val data = prompt.data
        require(data.persona.id == previous.personaId && data.session.agentEnabled) { "Group identity or mode changed" }
        val speaker = data.members.firstOrNull { it.character.id == characterId }
            ?: throw AgentRuntimeException("group_member_removed")
        val provider = mProviders.requireCharacterProvider(speaker.character)
        val scripts = mRegex.activeScripts(data.members.map { it.character })
        val ids = mGroups.getSessionLorebookEntryIds(data.session).toSet()
        val books = data.session.cardMembers(data.members, speaker.character)
            .map { it.character.characterLorebookId }.toSet()
        val all = mLorebooks.getAllLorebooksWithEntries()
        val entries = all.flatMap { it.entries }.filter { it.id in ids || it.lorebookId in books }
        val lorebooks =
            all.map { it.lorebook }.filter { book -> entries.any { it.lorebookId == book.id } }
                .associateBy { it.id }
        val images = mImages.prepareCandidates(prompt.messageImages)
        // 下一成员只接收自己的权限快照和上一成员已经提交的公共发言。
        val built = mBuilder.buildWithMetadata(
            context = GroupChatPromptContext(
                data.session,
                data.members,
                speaker.character,
                data.messages,
                provider,
                prompt.totalMessageCount,
                summary = data.summary?.content.orEmpty(),
                candidateLorebookEntries = entries,
                candidateLorebooks = lorebooks,
                recursiveScanningLorebookIds = lorebooks.values.filter { it.recursiveScanning }
                    .map { it.id }.toSet(),
                regexScripts = scripts,
                messageImages = images.references,
                unavailableImages = images.unavailable,
                persona = data.persona
            ), preferences = previous.templates
        )
        return previous.copy(
            characterId = characterId,
            provider = provider,
            request = built.request,
            drafts = built.retainedDrafts,
            scripts = scripts,
            macros = GroupChatMacros(
                data.session,
                data.members,
                data.persona,
                speaker.character
            ).forCharacter(),
            worldInfoStateJson = built.worldInfoStateJson,
            replacingMessageId = null,
            speakerName = speaker.character.name,
            otherSpeakerNames = data.members.map { it.character.name }
                .filterNot { it == speaker.character.name })
    }
}
