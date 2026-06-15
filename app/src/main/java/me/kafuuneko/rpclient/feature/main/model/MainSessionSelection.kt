package me.kafuuneko.rpclient.feature.main.model

/** 主页多选模式中用于区分单聊与群聊会话的稳定键。 */
data class MainSessionSelection(
    val type: MainSessionType,
    val sessionId: String
)

/** 主页可选择的会话类型。 */
enum class MainSessionType {
    Chat,
    GroupChat
}
