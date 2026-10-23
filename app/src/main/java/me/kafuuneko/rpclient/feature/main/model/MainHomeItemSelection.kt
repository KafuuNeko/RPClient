package me.kafuuneko.rpclient.feature.main.model

/** 主页多选模式中用于区分不同内容类型的稳定键。 */
data class MainHomeItemSelection(
    val type: MainHomeItemType,
    val itemId: String
)

/** 主页可选择的内容类型。 */
enum class MainHomeItemType {
    Chat,

    GroupChat,

    Story
}
