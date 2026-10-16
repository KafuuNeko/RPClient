package me.kafuuneko.rpclient.libs.utils

/**
 * 格式化仅供展示的主机与端口，不用于构造请求 URL。
 *
 * 保留已有规则：空主机显示占位符，HTTP/HTTPS 默认端口均省略，其他端口为 IPv6 主机补方括号。
 *
 * @param host 已提取的主机名或 IP 地址
 * @param port 端口号；非正数表示未指定
 * @return 不含协议与路径的端点展示文本
 */
internal fun formatEndpoint(host: String, port: Int): String {
    if (host.isBlank()) return "—"
    if (port <= 0 || port == HTTP_PORT || port == HTTPS_PORT) return host
    val displayHost = if (':' in host) "[$host]" else host
    return "$displayHost:$port"
}

private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443
