package me.kafuuneko.rpclient.libs.groupchat

import kotlin.random.Random

/** 创建群聊时可供开场白规划器使用的角色及候选文本。 */
data class GroupChatGreetingCandidate(
    val characterId: Long,
    val characterName: String,
    val greetings: List<String>
)

/** 群聊创建流程选择的开场白策略。 */
sealed interface GroupChatGreetingSelection {
    data object RandomPerCharacter : GroupChatGreetingSelection

    data class Manual(
        val characterId: Long,
        val greetingIndex: Int
    ) : GroupChatGreetingSelection

    data class Custom(
        val characterId: Long,
        val content: String
    ) : GroupChatGreetingSelection

    data object None : GroupChatGreetingSelection
}

/** 已解析、可直接写入群聊历史的角色开场消息。 */
data class GroupChatOpeningMessage(
    val characterId: Long,
    val characterName: String,
    val content: String
)

/**
 * 将创建页选择转换为有序的群聊开场消息。
 *
 * 随机模式保持成员顺序并为每名有候选文本的角色独立抽取；手动和自定义模式只生成
 * 一条消息。所有模式在规划阶段统一展开角色与用户宏。
 */
class GroupChatGreetingPlanner {
    fun plan(
        candidates: List<GroupChatGreetingCandidate>,
        selection: GroupChatGreetingSelection,
        userName: String,
        random: Random = Random.Default
    ): List<GroupChatOpeningMessage> {
        return when (selection) {
            GroupChatGreetingSelection.RandomPerCharacter -> candidates.mapNotNull { candidate ->
                candidate.greetings
                    .filter { it.isNotBlank() }
                    .randomOrNull(random)
                    ?.toOpeningMessage(candidate, userName)
            }
            is GroupChatGreetingSelection.Manual -> {
                val candidate = candidates.firstOrNull {
                    it.characterId == selection.characterId
                } ?: return emptyList()
                listOfNotNull(
                    candidate.greetings
                        .getOrNull(selection.greetingIndex)
                        ?.takeIf { it.isNotBlank() }
                        ?.toOpeningMessage(candidate, userName)
                )
            }
            is GroupChatGreetingSelection.Custom -> {
                val candidate = candidates.firstOrNull {
                    it.characterId == selection.characterId
                } ?: return emptyList()
                listOfNotNull(
                    selection.content
                        .takeIf { it.isNotBlank() }
                        ?.toOpeningMessage(candidate, userName)
                )
            }
            GroupChatGreetingSelection.None -> emptyList()
        }
    }

    private fun String.toOpeningMessage(
        candidate: GroupChatGreetingCandidate,
        userName: String
    ): GroupChatOpeningMessage {
        return GroupChatOpeningMessage(
            characterId = candidate.characterId,
            characterName = candidate.characterName,
            content = replace("{{char}}", candidate.characterName, ignoreCase = true)
                .replace("{{user}}", userName, ignoreCase = true)
                .trim()
        )
    }
}
