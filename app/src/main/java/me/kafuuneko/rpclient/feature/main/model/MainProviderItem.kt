package me.kafuuneko.rpclient.feature.main.model

/** 设置页渲染所需的模型配置摘要，不包含任何鉴权信息。 */
data class MainProviderItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 模型服务 API 的基础地址。 */
    val baseUrl: String,
    /** 当前配置或请求使用的模型名称。 */
    val model: String,
    /** 当前记录或配置是否启用。 */
    val isEnabled: Boolean
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()
}
