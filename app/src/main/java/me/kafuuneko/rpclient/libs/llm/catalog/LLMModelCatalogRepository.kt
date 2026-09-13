package me.kafuuneko.rpclient.libs.llm.catalog

import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig

/**
 * 使用未保存的模型配置草稿查询远端模型目录。
 *
 * 查询结果只交给当前页面渲染，不写入 Room 或生成请求日志。
 */
class LLMModelCatalogRepository(
    private val mClientFactory: LLMModelCatalogClientFactory,
    private val mImageCapabilities: ImageInputCapabilityResolver
) {
    suspend fun listModels(provider: LLMProviderConfig): List<LLMAvailableModel> {
        return mClientFactory.create(provider).listModels().also { mImageCapabilities.record(provider, it) }
    }
}
