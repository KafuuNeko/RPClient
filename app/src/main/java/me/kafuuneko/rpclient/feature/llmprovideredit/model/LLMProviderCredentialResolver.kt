package me.kafuuneko.rpclient.feature.llmprovideredit.model

/** 让连接测试和最终保存共享完全相同的 Keep/Replace/Clear API Key 解析规则。 */
internal object LLMProviderCredentialResolver {
    fun resolveApiKey(
        form: LLMProviderEditForm,
        initialApiKey: String,
        apiKeyReplacement: String?,
    ): String? {
        return when (form.apiKeyEditMode) {
            CredentialEditMode.KeepExisting -> initialApiKey
            CredentialEditMode.Replace -> apiKeyReplacement
            CredentialEditMode.Clear -> ""
        }
    }
}

