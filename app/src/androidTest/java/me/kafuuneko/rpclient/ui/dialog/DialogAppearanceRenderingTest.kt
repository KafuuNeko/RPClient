package me.kafuuneko.rpclient.ui.dialog

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.feature.dialogappearance.ui.DialogPreviewControls
import me.kafuuneko.rpclient.libs.appearance.DialogAppearance
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.LocalDialogAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import android.graphics.Color as AndroidColor

/** 真实窗口验证遮罩、模糊开关以及窗口销毁后的恢复，兼顾系统不支持模糊的降级。 */
class DialogAppearanceRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    /** 对话框内容保持独立，关闭模糊与关闭弹窗都不能留下窗口效果。 */
    @Test
    fun nativeWindowAppearanceUpdatesAndCleansUp() {
        var settings by mutableStateOf(
            value = DialogAppearance(
                transparency = 50,
                blur = 70,
                controlTransparency = 50,
                dim = 25,
                behindBlur = 40,
            ),
        )
        var dark by mutableStateOf(false)
        var show by mutableStateOf(true)
        var dialogWindow: Window? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val available =
            Build.VERSION.SDK_INT >= 31 && context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        compose.setContent {
            AppTheme(darkTheme = dark, dynamicColor = false) {
                Canvas(
                    modifier = Modifier.fillMaxSize(),
                    onDraw = {
                        val cell = 24.dp.toPx()
                        for (x in 0..(size.width / cell).toInt()) for (y in 0..(size.height / cell).toInt()) {
                            drawRect(
                                color = if ((x + y) % 2 == 0) Color(0xFF4D789C) else Color(
                                    0xFFEACDB1
                                ),
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell, cell),
                            )
                        }
                    },
                )
                if (show) CompositionLocalProvider(LocalDialogAppearance provides settings) {
                    AppDialogScaffold(
                        onDismissRequest = {},
                        title = "Dialog preview",
                        onConfirm = {}) {
                        val window = (LocalView.current.parent as DialogWindowProvider).window
                        SideEffect { dialogWindow = window }
                        DialogPreviewControls()
                    }
                }
            }
        }
        compose.waitForIdle()
        assertNotNull(dialogWindow)
        // 系统支持时启用原生后景；不支持时使用更深的遮罩，用户目标参数不被覆盖。
        compose.runOnIdle {
            assertEquals(
                if (available) 0.25f else 0.5f,
                dialogWindow!!.attributes.dimAmount,
                0.001f
            )
            if (Build.VERSION.SDK_INT >= 31) assertEquals(
                available,
                dialogWindow!!.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0,
            )
        }
        screenshot("dialog-light")
        compose.runOnIdle { dark = true }
        screenshot("dialog-dark")
        compose.runOnIdle {
            settings = settings.copy(
                transparency = 100,
                controlTransparency = 90,
                behindBlur = 0,
                blur = 0,
                dim = 0,
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) { dialogWindow?.attributes?.dimAmount == 0f }
        compose.runOnIdle {
            assertEquals(0f, dialogWindow!!.attributes.dimAmount, 0.001f)
            if (Build.VERSION.SDK_INT >= 31) assertEquals(
                0,
                dialogWindow!!.attributes.blurBehindRadius
            )
        }
        val transparentContrast = screenshot("dialog-transparent", dialogWindow)
        compose.runOnIdle { settings = settings.copy(blur = 100) }
        val blurredContrast = screenshot("dialog-panel-blur", dialogWindow)
        if (available) {
            // 不能仅断言窗口属性：不透明主题曾让参数存在但局部模糊完全不绘制。
            assertTrue("Checkerboard must be visible without blur", transparentContrast > 100)
            assertTrue("Panel blur must remove checkerboard edges", blurredContrast < 30)
        }
        compose.runOnIdle { show = false }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(false, dialogWindow!!.decorView.isAttachedToWindow) }
    }

    private fun screenshot(name: String, window: Window? = null): Int {
        compose.waitForIdle()
        // 窗口模糊由系统合成器异步提交，等待其完成，避免截到上一帧主题。
        SystemClock.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        val contrast = window?.let { window ->
            val location = IntArray(2)
            var width = 0
            var height = 0
            compose.runOnIdle {
                window.decorView.getLocationOnScreen(location)
                width = window.decorView.width
                height = window.decorView.height
            }
            val inset =
                (12 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
            val y = (location[1] + height - inset).coerceIn(
                minimumValue = 0,
                maximumValue = bitmap.height - 1
            )
            val reds = (location[0] + width / 4 until location[0] + width * 3 / 4).map { x ->
                AndroidColor.red(
                    bitmap.getPixel(
                        x.coerceIn(
                            minimumValue = 0,
                            maximumValue = bitmap.width - 1
                        ), y
                    )
                )
            }
            reds.max() - reds.min()
        } ?: 0
        bitmap.recycle()
        return contrast
    }
}
