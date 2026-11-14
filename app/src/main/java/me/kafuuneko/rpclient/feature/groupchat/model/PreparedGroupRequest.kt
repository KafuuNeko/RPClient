package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuildResult
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.GroupChatData

/** 群聊本次生成的只读快照，输出阶段复用构建请求时的人设、成员和正则。 */
internal data class PreparedGroupRequest(
    val data: GroupChatData,
    val provider: LLMProvider,
    val scripts: List<ScopedRegexScript>,
    val result: GroupChatPromptBuildResult,
)
