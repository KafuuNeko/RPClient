package me.kafuuneko.rpclient.feature.characteredit.model

/** 角色编辑页可绑定的模型配置摘要，不包含鉴权信息。 */
data class CharacterProviderItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前配置或请求使用的模型名称。 */
    val model: String,
    /** 当前记录或配置是否启用。 */
    val isEnabled: Boolean
)
