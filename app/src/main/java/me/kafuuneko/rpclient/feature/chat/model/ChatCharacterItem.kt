package me.kafuuneko.rpclient.feature.chat.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** 单聊页面使用的角色卡展示快照，包含 Prompt 相关字段和头像展示信息。 */
data class ChatCharacterItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 用于说明当前对象的描述文本。 */
    val description: String,
    /** 角色的性格与行为设定。 */
    val personality: String,
    /** 角色对话发生的场景设定。 */
    val scenario: String,
    /** 用于约束角色语气和格式的示例对话。 */
    val examplesOfDialogue: String,
    /** 追加在聊天历史之后的角色级提示词。 */
    val postHistoryInstructions: String,
    /** 作者提供的角色使用说明和备注。 */
    val creatorNotes: String,
    /** 头像无图片时展示的文字缩写。 */
    val avatarText: String,
    /** 角色在聊天界面中使用的稳定强调色。 */
    val accentColor: Color,
    /** 已解码、可直接用于界面展示的头像图像。 */
    val avatarImage: ImageBitmap? = null
)
