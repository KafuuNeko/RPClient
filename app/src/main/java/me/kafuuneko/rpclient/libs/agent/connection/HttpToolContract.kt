package me.kafuuneko.rpclient.libs.agent.connection

/** HTTP 工具持久化类别，未知类别必须在解码时拒绝。 */
object HttpToolKind {
    const val Http = "Http"
    const val ReadUrl = "ReadUrl"
}

/** 客户端支持的方法与保守副作用分类，不能根据服务端自报降低写入确认。 */
object ToolHttpMethod {
    const val Get = "GET"
    const val Head = "HEAD"
    const val Post = "POST"
    const val Put = "PUT"
    const val Patch = "PATCH"
    const val Delete = "DELETE"
    val all = listOf(Get, Post, Put, Patch, Delete, Head)
    val writes = setOf(Post, Put, Patch, Delete)
    val requiresBody = setOf(Post, Put, Patch)
    val reads = setOf(Get, Head)
}

/** 认证方式使用稳定字段值，凭据正文只通过本机加密仓库读取。 */
object ToolAuthMode {
    const val None = "None"
    const val Bearer = "Bearer"
    const val ApiKey = "ApiKey"
    const val Header = "Header"
    const val Query = "Query"
    val all = listOf(None, Bearer, ApiKey, Header, Query)
}

/** 参数映射只允许固定 Query 或 JSON Body，禁止模型绑定目标主机与认证头。 */
object ToolBindingTarget {
    const val Query = "query"
    const val Body = "body"
}
