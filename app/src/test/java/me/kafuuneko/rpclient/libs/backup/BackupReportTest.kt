package me.kafuuneko.rpclient.libs.backup

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证普通错误详情不会透传异常中的凭据、正文、路径或堆栈。 */
class BackupReportTest {
    /** 原因与附加异常中的原始载荷都不能进入可复制的安全摘要。 */
    @Test
    fun errorDetailsExcludeMessagesFromEveryFailure() {
        val report = BackupReport().apply { info("Exporting characters") }
        val error =
            IllegalArgumentException("Invalid JSON: $PRIVATE_PAYLOAD", IOException(PRIVATE_PAYLOAD))
        error.addSuppressed(SecurityException(PRIVATE_PAYLOAD))
        val detail = report.textWithError(error)
        assertTrue(detail.contains("Exporting characters"))
        assertTrue(detail.contains("IOException"))
        assertTrue(detail.contains("SecurityException"))
        assertFalse(detail.contains("AUDIT_PRIVATE"))
        assertFalse(detail.contains("/data/user/"))
        assertFalse(detail.contains("BackupReportTest.kt"))
    }

    /** 固定的诊断分类可以展示，但不能拼入源异常的自由文本。 */
    @Test
    fun statusIncludesSafeReasonWithoutOriginalPayload() {
        val status = BackupReport().statusMessage(
            error = IllegalStateException(PRIVATE_PAYLOAD, SecurityException(PRIVATE_PAYLOAD)),
            operation = "Backup",
        )
        assertTrue(status.contains("File access was denied."))
        assertFalse(status.contains("AUDIT_PRIVATE"))
    }

    /** 异常关系形成环或很深时，详情生成仍应结束并限制输出大小。 */
    @Test
    fun cyclicAndDeepFailuresRemainBounded() {
        val first = IllegalStateException(PRIVATE_PAYLOAD)
        val second = IOException(PRIVATE_PAYLOAD)
        first.initCause(second)
        second.initCause(first)
        // 同时覆盖原因和附加异常的循环，不能递归打印整条异常图。
        second.addSuppressed(first)
        val report = BackupReport()
        val cyclicDetail = report.textWithError(first)
        assertTrue(cyclicDetail.length < 1024)
        assertFalse(cyclicDetail.contains("AUDIT_PRIVATE"))
        // 长链中的每条原始消息仍属于敏感输入，截断不能放松内容过滤。
        val deepFailure = (1..256).fold<Int, Throwable>(first) { cause, _ ->
            IllegalStateException(PRIVATE_PAYLOAD, cause)
        }
        val deepDetail = report.textWithError(deepFailure)
        assertTrue(deepDetail.length < 1024)
        assertFalse(deepDetail.contains("AUDIT_PRIVATE"))
    }

    private companion object {
        const val PRIVATE_PAYLOAD =
            "{\"apiKey\":\"AUDIT_PRIVATE_KEY\",\"text\":\"AUDIT_PRIVATE_TEXT\",\"path\":\"/data/user/private\"}"
    }
}
