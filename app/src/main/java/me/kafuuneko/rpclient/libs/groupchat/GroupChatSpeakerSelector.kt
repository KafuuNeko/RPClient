package me.kafuuneko.rpclient.libs.groupchat

import com.google.gson.JsonParser
import kotlin.random.Random
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData

/** 根据群聊激活策略选择本轮一个或多个发言成员。 */
class GroupChatSpeakerSelector {
    /** 根据会话策略、成员状态和用户输入选择本轮发言者。 */
    fun select(
        session: GroupChatSession,
        members: List<GroupChatMemberData>,
        messages: List<GroupChatMessage>,
        activationText: String,
        isUserInput: Boolean,
        manualCharacterId: Long?,
        random: Random = Random.Default
    ): List<GroupChatMemberData> {
        val available = members.filterNot { it.relation.muted }
        if (available.isEmpty()) return emptyList()
        return when (session.activationStrategy) {
            GroupChatSession.ActivationStrategy.Manual -> {
                val selected = available.firstOrNull {
                    it.character.id == manualCharacterId
                }
                listOfNotNull(selected ?: available.randomOrNull(random).takeIf {
                    !isUserInput
                })
            }
            GroupChatSession.ActivationStrategy.List -> available
            GroupChatSession.ActivationStrategy.Pooled -> {
                listOf(selectPooled(available, messages, isUserInput, random))
            }
            GroupChatSession.ActivationStrategy.Natural -> {
                selectNatural(
                    session = session,
                    members = available,
                    messages = messages,
                    activationText = activationText,
                    isUserInput = isUserInput,
                    random = random
                )
            }
        }
    }

    /** 优先从本轮尚未发言的成员池中随机选择一名成员。 */
    private fun selectPooled(
        members: List<GroupChatMemberData>,
        messages: List<GroupChatMessage>,
        isUserInput: Boolean,
        random: Random
    ): GroupChatMemberData {
        val spokenSinceUser = if (isUserInput) {
            emptySet()
        } else {
            messages.asReversed()
                .takeWhile { it.source != GroupChatMessage.Source.User }
                .mapNotNull { it.speakerCharacterId }
                .toSet()
        }
        val candidates = members.filterNot { it.character.id in spokenSinceUser }
            .ifEmpty {
                val lastSpeakerId = messages.lastOrNull {
                    it.source == GroupChatMessage.Source.Character
                }?.speakerCharacterId
                members.filterNot {
                    members.size > 1 && it.character.id == lastSpeakerId
                }.ifEmpty { members }
            }
        return candidates.random(random)
    }

    /** 综合点名、连续发言限制和角色活跃度进行自然选择。 */
    private fun selectNatural(
        session: GroupChatSession,
        members: List<GroupChatMemberData>,
        messages: List<GroupChatMessage>,
        activationText: String,
        isUserInput: Boolean,
        random: Random
    ): List<GroupChatMemberData> {
        val lastSpeakerId = messages.lastOrNull {
            it.source == GroupChatMessage.Source.Character
        }?.speakerCharacterId
        val candidates = if (session.allowSelfResponses || isUserInput) {
            members
        } else {
            members.filterNot { it.character.id == lastSpeakerId }.ifEmpty { members }
        }
        val mentioned = candidates.filter { member ->
            member.character.name
                .split(Regex("""[\s_-]+"""))
                .filter { it.isNotBlank() }
                .any { activationText.containsWholeToken(it) }
        }
        val activated = (mentioned + candidates.shuffled(random).filter { member ->
            random.nextDouble() <= member.talkativeness()
        }).distinctBy { it.character.id }
        val randomPool = candidates.filter { it.talkativeness() > 0.0 }.ifEmpty { candidates }
        return activated.ifEmpty { listOf(randomPool.random(random)) }
    }

    /** 从角色扩展字段读取活跃度，并限制在有效概率区间。 */
    private fun GroupChatMemberData.talkativeness(): Double {
        return runCatching {
            val root = JsonParser.parseString(character.extensionsJson).asJsonObject
            when {
                root.has("talkativeness") -> root.get("talkativeness").asDouble
                root.has("group_chat_talkativeness") -> root.get("group_chat_talkativeness").asDouble
                else -> DEFAULT_TALKATIVENESS
            }
        }.getOrDefault(DEFAULT_TALKATIVENESS).coerceIn(0.0, 1.0)
    }

    /** 按完整词边界判断用户是否点名，避免命中名称子串。 */
    private fun String.containsWholeToken(token: String): Boolean {
        val escaped = Regex.escape(token)
        return Regex(
            pattern = """(?<![\p{L}\p{N}_])$escaped(?![\p{L}\p{N}_])""",
            option = RegexOption.IGNORE_CASE
        ).containsMatchIn(this)
    }

    private companion object {
        // 角色卡未提供活跃度时使用的默认发言概率。
        const val DEFAULT_TALKATIVENESS = 0.5
    }
}
