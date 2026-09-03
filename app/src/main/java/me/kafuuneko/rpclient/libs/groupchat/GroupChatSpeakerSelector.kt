package me.kafuuneko.rpclient.libs.groupchat

import com.google.gson.JsonParser
import kotlin.random.Random
import me.kafuuneko.rpclient.libs.prompt.matchesPlainTextKey
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData

/**
 * 发言者选择算法使用的历史投影。
 *
 * @property spokenCharacterIdsSinceLastUserMessage 上一条用户消息之后发言过的角色 ID。
 * @property lastCharacterSpeakerId 最近一条角色消息的发言者 ID。
 */
data class GroupChatSpeakerHistory(
    val spokenCharacterIdsSinceLastUserMessage: Set<Long>,
    val lastCharacterSpeakerId: Long?
)

/**
 * 根据群聊激活策略选择本轮一个或多个发言成员。
 *
 * 支持四种激活策略：
 * - Manual：手动指定单成员发言；
 * - List：全员依次按列表顺序发言；
 * - Pooled：轮流池模式（优先选择自上一轮用户发言后尚未发言的成员）；
 * - Natural：自然交互模式（结合点名、连续发言限制与角色活跃度掷骰决定激活列表）。
 */
class GroupChatSpeakerSelector {
    /**
     * 根据会话策略、成员状态和用户输入选择本轮发言者。
     */
    fun select(
        session: GroupChatSession,
        members: List<GroupChatMemberData>,
        messages: List<GroupChatMessage>,
        activationText: String,
        isUserInput: Boolean,
        manualCharacterId: Long?,
        random: Random = Random.Default
    ): List<GroupChatMemberData> {
        return select(
            session = session,
            members = members,
            history = messages.toSpeakerHistory(),
            activationText = activationText,
            isUserInput = isUserInput,
            manualCharacterId = manualCharacterId,
            random = random
        )
    }

    /**
     * 根据预先聚合的最小历史投影选择本轮发言者。
     *
     * 此入口避免大型群聊为了调度发言者而读取完整消息实体列表。
     */
    fun select(
        session: GroupChatSession,
        members: List<GroupChatMemberData>,
        history: GroupChatSpeakerHistory,
        activationText: String,
        isUserInput: Boolean,
        manualCharacterId: Long?,
        random: Random = Random.Default
    ): List<GroupChatMemberData> {
        // 过滤已被静音的成员
        val available = members.filterNot { it.relation.muted }
        if (available.isEmpty()) return emptyList()

        // 根据会话配置的激活策略分别派发
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
                listOf(selectPooled(available, history, isUserInput, random))
            }
            GroupChatSession.ActivationStrategy.Natural -> {
                selectNatural(
                    session = session,
                    members = available,
                    history = history,
                    activationText = activationText,
                    isUserInput = isUserInput,
                    random = random
                )
            }
        }
    }

    /**
     * 轮流池模式：优先从本轮尚未发言的成员池中随机选择一名成员。
     *
     * 规则：
     * - 若为用户新输入，重置发言记录；
     * - 查找自上一轮用户消息以来尚未发言的成员；
     * - 若全员均已发言，则排除上一条消息的发言者后从剩余成员中随机选取。
     */
    private fun selectPooled(
        members: List<GroupChatMemberData>,
        history: GroupChatSpeakerHistory,
        isUserInput: Boolean,
        random: Random
    ): GroupChatMemberData {
        // 统计自上一条用户消息以来已发言的角色 ID 集合
        val spokenSinceUser = if (isUserInput) {
            emptySet()
        } else {
            history.spokenCharacterIdsSinceLastUserMessage
        }
        // 筛选未发言候选者；若都已发言则排除上一条消息发言者（避免单人连续发言）
        val candidates = members.filterNot { it.character.id in spokenSinceUser }
            .ifEmpty {
                val lastSpeakerId = history.lastCharacterSpeakerId
                members.filterNot {
                    members.size > 1 && it.character.id == lastSpeakerId
                }.ifEmpty { members }
            }
        return candidates.random(random)
    }

    /**
     * 自然交互模式：综合点名、连续发言限制和角色活跃度进行自然选择。
     *
     * 规则：
     * - 检查是否允许自回复（连续发言）；
     * - 优先提取被文本点名（@或全词命中）的角色；
     * - 对未点名角色按活跃度（Talkativeness）掷骰判定是否加入发言队列；
     * - 若无任何成员被激活，则保底随机挑选一名成员。
     */
    private fun selectNatural(
        session: GroupChatSession,
        members: List<GroupChatMemberData>,
        history: GroupChatSpeakerHistory,
        activationText: String,
        isUserInput: Boolean,
        random: Random
    ): List<GroupChatMemberData> {
        // 判定自回复限制
        val lastSpeakerId = history.lastCharacterSpeakerId
        val candidates = if (session.allowSelfResponses || isUserInput) {
            members
        } else {
            members.filterNot { it.character.id == lastSpeakerId }.ifEmpty { members }
        }

        // 查找被当前激活文本点名的角色
        val mentioned = candidates.filter { member ->
            member.character.name
                .split(Regex("""[\s_-]+"""))
                .filter { it.isNotBlank() }
                .any { activationText.containsWholeToken(it) }
        }

        // 对剩余角色按活跃度概率抽取激活
        val activated = (mentioned + candidates.shuffled(random).filter { member ->
            random.nextDouble() <= member.talkativeness()
        }).distinctBy { it.character.id }

        // 若均未激活，从活跃度大于 0 的池中保底抽取一名
        val randomPool = candidates.filter { it.talkativeness() > 0.0 }.ifEmpty { candidates }
        return activated.ifEmpty { listOf(randomPool.random(random)) }
    }

    /** 从完整消息列表提取与数据库投影相同的发言历史信息。 */
    private fun List<GroupChatMessage>.toSpeakerHistory(): GroupChatSpeakerHistory {
        return GroupChatSpeakerHistory(
            spokenCharacterIdsSinceLastUserMessage = asReversed()
                .takeWhile { it.source != GroupChatMessage.Source.User }
                .mapNotNull { it.speakerCharacterId }
                .toSet(),
            lastCharacterSpeakerId = lastOrNull {
                it.source == GroupChatMessage.Source.Character
            }?.speakerCharacterId
        )
    }

    /** 从角色扩展字段读取活跃度，并限制在 [0.0, 1.0] 有效概率区间。 */
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
        return matchesPlainTextKey(token, ignoreCase = true, matchWholeWords = true)
    }

    private companion object {
        /** 角色卡未提供活跃度时使用的默认发言概率 (50%)。 */
        const val DEFAULT_TALKATIVENESS = 0.5
    }
}
