package me.kafuuneko.rpclient.feature.groupchat.utils

import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.room.repository.GroupChatData

/**
 * 生成群聊专用的正则宏字典。
 *
 * 注入变量包括：`{{user}}`, `{{char}}`, `{{description}}`, `{{scenario}}`, `{{group}}`（所有群成员名称列表）。
 *
 * @param data 群聊聚合数据
 * @param characterName 当前角色的名称
 * @return 宏映射 Map
 */
internal fun groupRegexMacros(
    data: GroupChatData,
    characterName: String
): Map<String, String> {
    return RegexScriptRuntime.macros(
        userName = data.session.userName,
        characterName = characterName,
        userDescription = data.session.userDescription,
        scenario = data.session.scenario,
        groupNames = data.members.joinToString(", ") { it.character.name }
    )
}
