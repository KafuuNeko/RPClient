package me.kafuuneko.rpclient.feature.main.model

/** 对话导入确认框中用于区分本地角色卡的轻量展示模型。 */
data class MainImportCharacterItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 用于解释当前对象的详细信息列表。 */
    val details: String
)
