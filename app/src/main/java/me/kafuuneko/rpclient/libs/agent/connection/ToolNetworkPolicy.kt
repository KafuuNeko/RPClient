package me.kafuuneko.rpclient.libs.agent.connection

import java.io.IOException
import java.net.InetAddress
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 固定诊断码跨越网络边界，禁止把 URL、认证头或响应正文放入异常。
 */
class ToolHttpException(val code: String) : IOException("HTTP tool failed: $code")

/**
 * 工具专用地址策略，不继承模型 Provider 的内网访问权限。
 * - DNS 返回的每个地址都必须可用，禁止从混合结果中挑选公共地址绕过检查。
 * - IPv6 只接受全球单播，并排除隧道、文档和协议保留网段。
 */
object ToolNetworkPolicy {
    /**
     * 解析初始地址和每跳 Location，拒绝嵌入凭据和非 HTTP 协议。
     *
     * @param raw 待严格解析的 JSON 或地址文本。
     * @return 仅支持 HTTP(S) 且不含嵌入凭据的地址。
     */
    fun url(raw: String): HttpUrl {
        val result =
            raw.toHttpUrlOrNull() ?: throw ToolHttpException(ToolErrorCode.HTTP_INVALID_URL)
        if (result.username.isNotEmpty() || result.password.isNotEmpty()) {
            throw ToolHttpException(ToolErrorCode.HTTP_INVALID_URL)
        }
        return result
    }

    /**
     * OkHttp 对 IP 字面值会跳过 Dns，必须在每跳派发前单独校验。
     *
     * @param host 本跳目标主机，不包含认证信息。
     * @param allowPrivate 是否由用户明确允许访问私有地址。
     */
    fun checkLiteralHost(host: String, allowPrivate: Boolean) {
        if (allowPrivate || (!host.contains(':') && !host.matches(Regex("[0-9.]+")))) return
        val address = runCatching { InetAddress.getByName(host) }.getOrNull()
        if (address == null || !isPublic(address)) throw ToolHttpException(ToolErrorCode.HTTP_ADDRESS_BLOCKED)
    }

    /**
     * 判断解析后的真实地址是否可以在默认公网工具中使用。
     *
     * @param address 实际解析得到的 IP 地址。
     * @return 是否属于允许访问的公共单播地址。
     */
    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (bytes.size == 4) return isPublicV4(bytes)
        if (bytes.size != 16 || bytes[0] and 0xe0 != 0x20) return false
        // 排除 Teredo、基准测试、ORCHID、文档地址与 6to4，避免嵌入式内网地址。
        if (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] < 2) return false
        if (bytes.take(4) == listOf(0x20, 0x01, 0x0d, 0xb8)) return false
        return !(bytes[0] == 0x20 && bytes[1] == 0x02)
    }

    private fun isPublicV4(bytes: List<Int>): Boolean {
        val first = bytes[0]
        val second = bytes[1]
        if (first in setOf(0, 10, 127) || first >= 224) return false
        if (first == 100 && second in 64..127) return false
        if (first == 169 && second == 254) return false
        if (first == 172 && second in 16..31) return false
        if (first == 192 && (second == 168 || second == 0 || (second == 88 && bytes[2] == 99))) return false
        if (first == 198 && (second in 18..19 || (second == 51 && bytes[2] == 100))) return false
        return !(first == 203 && second == 0 && bytes[2] == 113)
    }
}

/**
 * 在 OkHttp 实际建立连接所用的 DNS 中校验，避免预检后再次解析产生重绑定。
 */
class ToolSafeDns(
    private val mAllowPrivate: Boolean,
    private val mDelegate: Dns = Dns.SYSTEM,
) : Dns {
    /**
     * 返回已经过策略检查的解析结果，不使用独立预检结果。
     *
     * @param hostname 本次连接需要解析的主机名。
     * @return 本次真实连接可以使用的地址列表。
     */
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = mDelegate.lookup(hostname)
        if (addresses.isEmpty() || (!mAllowPrivate && addresses.any { !ToolNetworkPolicy.isPublic(it) })) {
            throw ToolHttpException(ToolErrorCode.HTTP_ADDRESS_BLOCKED)
        }
        return addresses
    }
}
