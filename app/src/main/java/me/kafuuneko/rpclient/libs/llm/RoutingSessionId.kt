package me.kafuuneko.rpclient.libs.llm

import me.kafuuneko.rpclient.libs.AppModel
import java.security.MessageDigest
import java.util.UUID

/** 为网关生成匿名、跨重启稳定且按业务会话隔离的路由会话 ID。 */
object RoutingSessionId {
    /** 返回同一安装、同一业务会话内稳定且不暴露原始数据库 ID 的路由键。 */
    fun forConversation(conversationKey: String): String {
        val installationId = synchronized(AppModel) {
            AppModel.llmRoutingInstallationId.ifBlank {
                UUID.randomUUID().toString().also { text -> AppModel.llmRoutingInstallationId = text }
            }
        }
        return hashRoutingSessionId(installationId, conversationKey)
    }
}

internal fun hashRoutingSessionId(installationId: String, conversationKey: String): String {
    val source = "rpclient-routing-v1|$installationId|$conversationKey"
    return MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
