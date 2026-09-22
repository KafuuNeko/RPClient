package me.kafuuneko.rpclient.ui.message

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.MarkdownMessageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 验证真实选择菜单与 Android 文本处理、分享协议之间的衔接。 */
class MessageTextSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    /** 分享选中的 Markdown 渲染片段，且不通过剪贴板中转。 */
    @Test
    fun shareOnlySelectedTextWithoutChangingClipboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val outgoing = LinkedBlockingQueue<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                outgoing.offer(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            selectRenderedWord()
            var originalClipboard: CharSequence? = null
            compose.runOnIdle {
                originalClipboard = clipboard.primaryClip?.getItemAt(0)?.text
            }
            // 从系统浮动菜单进入，覆盖 Compose 截取文本和内部 Activity 的完整调用路径。
            clickMenuItem(context.getString(R.string.text_selection_share))
            val chooser = outgoing.poll(5, TimeUnit.SECONDS)
            assertNotNull("The selection menu should open the Android sharesheet", chooser)
            @Suppress("DEPRECATION")
            val sendIntent = chooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND, sendIntent.action)
            assertEquals("text/plain", sendIntent.type)
            assertEquals("selected", sendIntent.getStringExtra(Intent.EXTRA_TEXT))
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(originalClipboard, clipboard.primaryClip?.getItemAt(0)?.text)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** 第三方文本处理入口可被菜单发现，并收到实际选中的片段。 */
    @Test
    fun externalTextProcessorReceivesSelectedText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val outgoing = LinkedBlockingQueue<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.component?.packageName != instrumentation.context.packageName) return null
                outgoing.offer(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            // 测试 APK 中的处理入口属于另一个包，模拟设备上安装的翻译、朗读应用。
            selectRenderedWord()
            clickMenuItem("RPClient test text processor")
            val request = outgoing.poll(5, TimeUnit.SECONDS)
            assertNotNull("The menu should discover external text processors", request)
            assertEquals(Intent.ACTION_PROCESS_TEXT, request!!.action)
            assertEquals("selected", request.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString())
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun selectRenderedWord() {
        compose.setContent {
            AppTheme {
                Box(Modifier.padding(48.dp)) {
                    MarkdownMessageText("Before **selected** after.\n\nOther paragraph.", isUser = false)
                }
            }
        }
        // 依据排版结果长按目标单词，避免依赖设备分辨率或固定屏幕坐标。
        val layouts = mutableListOf<TextLayoutResult>()
        val text = compose.onNodeWithText("Before selected after.")
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        text.performTouchInput { longClick(layouts.single().getBoundingBox(9).center) }
        compose.waitForIdle()
    }

    private fun clickMenuItem(label: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val serviceInfo = automation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        // 系统浮动工具栏可能属于独立窗口，使用无障碍树而非应用内 View 查找。
        var target: AccessibilityNodeInfo? = null
        var expanded = false
        compose.waitUntil(timeoutMillis = 5_000) {
            val roots = automation.windows.mapNotNull { it.root }
            target = roots.firstNotNullOfOrNull { findMenuNode(it, label) }
            if (target == null && !expanded) {
                val more = roots.firstNotNullOfOrNull { findMenuNode(it, "More options") }
                if (more != null) {
                    more.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    expanded = true
                }
            }
            target != null
        }
        assertTrue(target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun findMenuNode(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
            return if (node.isClickable) node else node.parent
        }
        return (0 until node.childCount).firstNotNullOfOrNull { index ->
            node.getChild(index)?.let { findMenuNode(it, label) }
        }
    }
}
