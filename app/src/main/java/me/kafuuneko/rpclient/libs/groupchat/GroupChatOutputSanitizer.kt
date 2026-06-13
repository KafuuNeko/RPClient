package me.kafuuneko.rpclient.libs.groupchat

/** 清理群聊模型输出中的角色名前缀和越权代写内容。 */
class GroupChatOutputSanitizer {
    /** 移除当前角色名前缀，并按配置截断模型代写其他成员的内容。 */
    fun sanitize(
        content: String,
        currentSpeakerName: String,
        otherSpeakerNames: List<String>,
        trimOtherSpeakers: Boolean
    ): String {
        var result = content.trim()
        result = result.removeSpeakerPrefix(currentSpeakerName)
        if (!trimOtherSpeakers) return result
        val boundary = otherSpeakerNames
            .flatMap { name ->
                listOf("\n$name:", "\n$name：")
            }
            .mapNotNull { marker ->
                result.indexOf(marker, ignoreCase = true)
                    .takeIf { it >= 0 }
            }
            .minOrNull()
        return boundary?.let { result.take(it).trimEnd() } ?: result
    }

    /** 清除模型可能主动添加的半角或全角角色名前缀。 */
    private fun String.removeSpeakerPrefix(name: String): String {
        val trimmed = trimStart()
        val prefixes = listOf("$name:", "$name：")
        val prefix = prefixes.firstOrNull {
            trimmed.startsWith(it, ignoreCase = true)
        } ?: return this
        return trimmed.substring(prefix.length).trimStart()
    }
}
