package me.kafuuneko.rpclient.feature.characterlist.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** 角色列表渲染所需的最小快照。 */
data class CharacterListItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 用于说明当前对象的描述文本。 */
    val description: String,
    /** 角色用于分类和搜索的标签列表。 */
    val tags: List<String>,
    /** 头像无图片时展示的文字缩写。 */
    val avatarText: String,
    /** 头像占位内容使用的稳定背景色。 */
    val avatarColor: Color,
    /** 已解码、可直接用于界面展示的头像图像。 */
    val avatarImage: ImageBitmap? = null
)
