package me.kafuuneko.rpclient.feature.jsonviewer

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.feature.jsonviewer.ui.JsonViewerLayout
import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogItem
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiState
import me.kafuuneko.rpclient.feature.requestlog.ui.RequestLogLayout
import me.kafuuneko.rpclient.libs.appearance.BackgroundBlur
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.PageBackdrop
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.graphics.Color as AndroidColor

/** 在真实 Activity 中滚动长文本和切换超限源码，验证惰性布局及原生窗口稳定性。 */
@RunWith(AndroidJUnit4::class)
class JsonViewerRenderingTest {
    private val mVerticalList = hasScrollAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @get:Rule
    val compose = createEmptyComposeRule()

    /** 超过旧摘要长度的文本尾部能够滚动到达，源码切换后仍能布局。 */
    @Test
    fun longText_scrollsToEndAndSwitchesToHighlightedSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = JSONObject().put("content", "段落😀\n".repeat(5000) + "TAIL").toString()
        ActivityScenario.launch<JsonViewerActivity>(
            JsonViewerActivity.newIntent(
                context,
                "JSON probe",
                source
            )
        ).use {
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(mVerticalList).fetchSemanticsNodes().isNotEmpty()
            }
            // 定位文本尾部会跨过多个惰性块，不能只验证首屏预览。
            compose.onNode(mVerticalList).performScrollToNode(hasText("TAIL", substring = true))
            compose.onNodeWithText("TAIL", substring = true).assertExists()
            screenshot("issue23-json-tree")
            compose.onNodeWithText(context.getString(R.string.json_tab_source)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText("\"content\"", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            screenshot("issue23-json-source")
        }
    }

    /** 巨型 Base64 字段在树视图中受保护，源码超预算时显示提示而非创建完整布局。 */
    @Test
    fun hugeField_showsProtectionAndRejectsSourcePreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = "data:image/png;base64," + "A".repeat(1_100_000)
        val source = JSONObject().put("image", content).toString()
        ActivityScenario.launch<JsonViewerActivity>(
            JsonViewerActivity.newIntent(
                context,
                "Large JSON probe",
                source
            )
        ).use {
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(mVerticalList).fetchSemanticsNodes().isNotEmpty()
            }
            // 仅需少量预览块便可到达明确的缩略提示。
            val warning = context.getString(R.string.json_value_truncated, content.length)
            compose.onNode(mVerticalList).performScrollToNode(hasText(warning))
            compose.onNodeWithText(warning).assertExists()
            compose.onNodeWithText(context.getString(R.string.json_tab_source)).performClick()
            val tooLarge = context.getString(R.string.json_preview_too_large)
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText(tooLarge)).fetchSemanticsNodes().isNotEmpty()
            }
            screenshot("issue23-json-too-large")
        }
    }

    /** 流式日志和空字符串在壁纸下保持完整字段布局，顶栏与正文分别遵循模糊和透明度。 */
    @Test
    fun streamFields_renderWithIndependentSurfaceAppearance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event =
            "data: " + """{"id":"sample","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"role":"assistant","content":"这是用于检查布局的合成响应。"},"finish_reason":null}]}"""
        val source = JSONArray().apply {
            repeat(40) {
                put(event)
                put("")
            }
        }.toString()
        val blurred = createBackdrop()
        var backdrop by mutableStateOf(blurred)
        var darkTheme by mutableStateOf(true)
        ActivityScenario.launch<JsonViewerActivity>(
            JsonViewerActivity.newIntent(context, "响应 JSON", source)
        ).use { scenario ->
            // 仅替换测试宿主的外观，不写入用户背景配置，继续使用真实 ViewModel 和导航。
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity)[JsonViewerViewModel::class.java]
                activity.setContent {
                    AppTheme(darkTheme = darkTheme, dynamicColor = false) {
                        PageBackdrop(state = backdrop) {
                            val uiState by viewModel.uiStateFlow.collectAsState()
                            JsonViewerLayout(uiState = uiState, emit = { viewModel.emit(this) })
                        }
                    }
                }
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText("\"\"")).fetchSemanticsNodes().isNotEmpty()
            }
            screenshot("issue23-json-wallpaper-tree")
            // 关闭两个栏位的模糊，便于与开启状态比对；不改变底图和不透明度。
            compose.runOnIdle {
                backdrop = blurred.copy(
                    settings = blurred.settings?.copy(contentBlur = 0, topBarBlur = 0),
                    surfaceImages = emptyMap(),
                )
            }
            screenshot("issue23-json-wallpaper-sharp")
            compose.runOnIdle {
                backdrop = blurred
                darkTheme = false
            }
            screenshot("issue23-json-wallpaper-light")
            compose.runOnIdle { darkTheme = true }
            compose.onNodeWithText(context.getString(R.string.json_tab_source)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText("\"data: ", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            screenshot("issue23-json-wallpaper-source")
            showRequestLogActions(scenario = scenario, backdrop = blurred)
        }
    }

    /** 从可视化字段、高亮源码和滚动后才组合的文本块实际复制选中的单词。 */
    @Test
    fun jsonText_longPressCopiesVisualAndLazySourceBlocks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val padding = JSONArray().apply { repeat(260) { put("padding") } }
        val source = JSONObject().put("start", "visiblecopy").put("padding", padding)
            .put("end", "latercopy").toString()
        ActivityScenario.launch<JsonViewerActivity>(
            JsonViewerActivity.newIntent(context, "Selection probe", source)
        ).use {
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText("visiblecopy")).fetchSemanticsNodes().isNotEmpty()
            }
            copyWord(word = "visiblecopy")
            compose.onNodeWithText(context.getString(R.string.json_tab_source)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText("\"start\"", substring = true)).fetchSemanticsNodes()
                    .isNotEmpty()
            }
            // 复制首屏后再滚动至未组合的末块，覆盖大文档的选择能力。
            copyWord(word = "visiblecopy", screenshotName = "issue23-json-source-selection")
            compose.onNode(mVerticalList)
                .performScrollToNode(hasText("latercopy", substring = true))
            copyWord(word = "latercopy")
        }
    }

    /** 非法历史日志进入原文降级后，正文仍可通过系统菜单复制。 */
    @Test
    fun rawSource_longPressCopiesSelectedText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<JsonViewerActivity>(
            JsonViewerActivity.newIntent(context, "Raw selection probe", "Before rawcopy after.")
        ).use {
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText(context.getString(R.string.json_tab_source)))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(context.getString(R.string.json_tab_source)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodes(hasText("Before rawcopy after.")).fetchSemanticsNodes()
                    .isNotEmpty()
            }
            // 降级提示不参与选择，仅复制原文中的目标词。
            copyWord(word = "rawcopy")
        }
    }

    /** 通过排版坐标长按目标词，并核对系统复制后的实际剪贴板内容。 */
    private fun copyWord(word: String, screenshotName: String? = null) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val layouts = mutableListOf<TextLayoutResult>()
        val text = compose.onNodeWithText(word, substring = true)
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val offset = layout.layoutInput.text.text.indexOf(word) + word.length / 2
        text.performTouchInput { longClick(layout.getBoundingBox(offset).center) }
        if (screenshotName != null) screenshot(screenshotName)
        // 浮动工具栏属于原生窗口，通过无障碍操作验证真实复制路径。
        clickCopyMenu()
        compose.waitUntil(timeoutMillis = 5_000) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() == word
        }
        compose.runOnIdle {
            assertEquals(
                word,
                clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            )
        }
    }

    /** 查询系统浮动菜单，避免依赖具体设备分辨率或复制按钮位置。 */
    private fun clickCopyMenu() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val label = instrumentation.targetContext.getString(android.R.string.copy)
        val serviceInfo = automation.serviceInfo
        serviceInfo.flags =
            serviceInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        var target: AccessibilityNodeInfo? = null
        // 部分系统会将菜单放在独立窗口，遍历当前交互窗口查找可点击父节点。
        compose.waitUntil(timeoutMillis = 5_000) {
            target = automation.windows.asSequence().mapNotNull { it.root }
                .flatMap { it.findAccessibilityNodeInfosByText(label).asSequence() }
                .firstOrNull { it.text?.toString() == label }
            target != null
        }
        val node = requireNotNull(target)
        val clickable = if (node.isClickable) node else requireNotNull(node.parent)
        assertTrue(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    /** 用合成日志核查导出图标与无描边操作行，不启动系统导出或写入日志。 */
    private fun showRequestLogActions(
        scenario: ActivityScenario<JsonViewerActivity>,
        backdrop: PageBackgroundUiState
    ) {
        val sample = RequestLogItem(
            id = 1L,
            title = "Sample / model",
            subtitle = "10-24 12:00:00 · stream",
            requestPreview = """{"model":"sample","stream":true}""",
            responsePreview = """{"content":"Synthetic response"}""",
        )
        // 只替换测试宿主的渲染内容，保留与 JSON 页面相同的主题和外观参数。
        scenario.onActivity { activity ->
            activity.setContent {
                AppTheme(darkTheme = true, dynamicColor = false) {
                    PageBackdrop(state = backdrop) {
                        RequestLogLayout(uiState = RequestLogUiState.Normal(logs = listOf(sample)))
                    }
                }
            }
        }
        compose.onNodeWithText(sample.title).performClick()
        screenshot("issue23-requestlog-actions")
    }

    /** 使用带细线的合成渐变图检查坐标对齐，派生图使用应用现有模糊算法。 */
    private fun createBackdrop(): PageBackgroundUiState = runBlocking {
        val width = 360
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF355B78.toInt(), 0xFF806B91.toInt(), 0xFFC7A58A.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPaint(paint)
        // 清晰的细线在卡片外保留，卡片内应按照栏位强度平滑，而非模糊文字。
        paint.shader = null
        paint.color = AndroidColor.argb(110, 240, 230, 255)
        paint.strokeWidth = 3f
        for (x in -height..width step 32) canvas.drawLine(
            x.toFloat(),
            0f,
            (x + height).toFloat(),
            height.toFloat(),
            paint
        )
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val content = Bitmap.createBitmap(
            BackgroundBlur.apply(pixels, width, height, radius = 12),
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        val topBar = Bitmap.createBitmap(
            BackgroundBlur.apply(pixels, width, height, radius = 6),
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        PageBackgroundUiState(
            settings = PageBackground(
                page = BackgroundPage.General,
                blur = 0,
                scrim = 0.2f,
                opacity = 0.45f,
                topBarOpacity = 0.25f,
                contentBlur = 80,
                topBarBlur = 40,
            ),
            image = bitmap.asImageBitmap(),
            surfaceImages = mapOf(
                PageSurfaceKind.Content to content.asImageBitmap(),
                PageSurfaceKind.TopBar to topBar.asImageBitmap()
            ),
        )
    }

    /** 保存合成数据截图供本次界面核查，不包含用户请求内容。 */
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 语义树可能先于原生窗口提交，等待绘制和点击涟漪收尾后再取屏幕像素。
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream()
            .use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        bitmap.recycle()
    }
}
