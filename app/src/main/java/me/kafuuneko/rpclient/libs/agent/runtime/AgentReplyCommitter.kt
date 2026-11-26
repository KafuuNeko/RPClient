package me.kafuuneko.rpclient.libs.agent.runtime

import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.groupchat.GroupChatOutputSanitizer
import me.kafuuneko.rpclient.libs.regex.RegexMessageProcessor
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository

/**
 * 最终正文及提案的原子收尾，不依赖页面存活。
 * - 清洗、Source Regex 和检查点 IO 在事务外完成。
 * - 短事务复核执行时钟、替换目标、证据、剧情乐观锁及世界书快照。
 */
class AgentReplyCommitter(
    private val mRuns: AgentRunRepository,
    private val mData: AgentLocalDataRepository,
    private val mChats: ChatRepository,
    private val mGroups: GroupChatRepository,
    private val mDatabase: AppDatabase,
    private val mRegex: RegexMessageProcessor,
    private val mSanitizer: GroupChatOutputSanitizer,
) {
    /** 使用与普通聊天一致的正文清洗和 Source Regex 顺序。 */
    fun clean(plan: AgentTurnPlan, content: String): String {
        val input = plan.input
        val sanitized = if (input.conversation is ConversationRef.Group) mSanitizer.sanitize(
            content,
            input.speakerName,
            input.otherSpeakerNames,
            input.trimOtherSpeakers
        ) else content
        return mRegex.applyAiResponse(sanitized, input.scripts, input.macros)
    }

    /** 发布前读取提案载荷，然后在同一授权事务提交最终正文及核验成功的变更。 */
    suspend fun commit(
        token: AgentExecutionToken,
        plan: AgentTurnPlan,
        content: String
    ): AgentExecutionToken {
        val accepted = clean(plan, content)
        if (accepted.isBlank()) throw AgentRuntimeException("empty_final_content")
        val proposals = mData.proposals(token.runId)
        val input = plan.input
        // 未被清洗后正文支持的提案不进入事务，不能借工具返回伪造已发生剧情。
        val supported = proposals.mapNotNull { proposal ->
            val matched = AgentLocalDataRepository.matchEvidenceQuote(
                content = accepted,
                quote = proposal["evidence"].asString,
            ) ?: return@mapNotNull null
            proposal.deepCopy().apply { addProperty("evidence", matched) }
        }
        return mRuns.commitAuthorized(token) {
            val id = persistBody(input, accepted)
            val context = ToolExecutionContext(
                token,
                0,
                "final/${token.runId}",
                input.characterId,
                input.personaId
            )
            supported.forEach { proposal -> mData.commitProposal(context, proposal, id) }
            mRuns.attachMessage(token, id)
        }
    }

    /** 取消时只提交已知最终阶段的正文；群聊原位重生成必须等待完整结果。 */
    internal suspend fun persistPartial(plan: AgentTurnPlan, accepted: String): Long? {
        if (accepted.isBlank() || (plan.input.conversation is ConversationRef.Group && plan.input.replacingMessageId != null)) return null
        return persistBody(plan.input, accepted)
    }

    private suspend fun persistBody(input: AgentTurnInput, accepted: String): Long {
        // 只复用原消息规则，不处理提案、网络或检查点文件。
        return when (val conversation = input.conversation) {
            is ConversationRef.Single -> requireNotNull(
                mChats.commitGenerationResult(
                    conversation.id,
                    input.replacingMessageId,
                    ChatMessage.Source.Char,
                    accepted,
                    false,
                    input.worldInfoStateJson
                )
            )

            is ConversationRef.Group -> {
                val replacing = input.replacingMessageId
                if (replacing != null) {
                    val original =
                        requireNotNull(mDatabase.getGroupChatMessageDao().getMessageById(replacing))
                    check(
                        mGroups.replaceLatestReply(
                            original,
                            accepted
                        )
                    ) { "Group reply changed before commit" }
                    mGroups.updateWorldInfoState(conversation.id, input.worldInfoStateJson)
                    replacing
                } else mGroups.createMessage(
                    conversation.id,
                    GroupChatMessage.Source.Character,
                    accepted,
                    input.characterId,
                    input.speakerName,
                    input.batchId,
                    worldInfoStateJson = input.worldInfoStateJson
                )
            }
        }
    }

}
