package me.kafuuneko.rpclient.feature.llmproviderlist.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

/** 模型配置列表渲染所需的最小摘要，不包含密钥和自定义请求头。 */
data class LLMProviderListItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 模型配置所属的供应商类型。 */
    val providerType: LLMProviderType,
    /** 模型配置实际采用的通信协议。 */
    val protocol: LLMProviderProtocol,
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
