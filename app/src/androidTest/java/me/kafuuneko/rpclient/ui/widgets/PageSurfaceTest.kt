package me.kafuuneko.rpclient.ui.widgets

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 验证局部壁纸绘制的坐标、裁切和开关，防止背景变成气泡内重新居中的图片。 */
class PageSurfaceTest {
    @get:Rule
    val mCompose = createComposeRule()

    /** 壁纸与局部背景相同时，圆角内外都不应出现阴影残边或主题底色渗出的细线。 */
    @Test
    fun roundedTranslucentSurfaceHasNoResidualOutline() {
        val mode = mutableIntStateOf(0)
        val white = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.WHITE) }.asImageBitmap()
        mCompose.setContent {
            AppTheme(darkTheme = mode.intValue % 2 == 1, dynamicColor = false) {
                Box(Modifier.size(240.dp, 160.dp).testTag("corners")) {
                    PageBackdrop(PageBackgroundUiState(
                        PageBackground(BackgroundPage.Chat, scrim = 0f, bubbleOpacity = 0.2f, bubbleBlur = 50),
                        white, mapOf(PageSurfaceKind.Bubble to white)
                    )) {
                        PageSurface(PageSurfaceKind.Bubble,
                            modifier = Modifier.padding(30.dp).size(160.dp, 80.dp),
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp),
                            color = Color.White.copy(alpha = 0.2f),
                            shadowElevation = if (mode.intValue < 2) 0.dp else 8.dp
                        ) {}
                    }
                }
            }
        }
        // 覆盖浅深主题及原有投影开关，直接逐像素检查圆角所在的整个画布。
        for (value in 0..3) {
            mCompose.runOnIdle { mode.intValue = value }
            val image = mCompose.onNodeWithTag("corners").captureToImage().toPixelMap()
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val color = image[x, y]
                assertTrue("Residual outline in mode $value at $x,$y: $color",
                    color.red > 0.99f && color.green > 0.99f && color.blue > 0.99f)
            }
        }
    }

    /** 半透明容器带阴影时，背景派生图仍应遮住容器自身投影，不能出现内部暗线。 */
    @Test
    fun elevatedSurfaceDoesNotCastShadowOverItsOwnBackdrop() {
        val white = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.WHITE) }.asImageBitmap()
        mCompose.setContent {
            AppTheme(darkTheme = false) {
                PageBackdrop(PageBackgroundUiState(
                    PageBackground(BackgroundPage.Chat, scrim = 0f, bubbleOpacity = 0.2f, bubbleBlur = 50),
                    white, mapOf(PageSurfaceKind.Bubble to white)
                )) {
                    PageSurface(PageSurfaceKind.Bubble,
                        modifier = Modifier.padding(32.dp).size(180.dp, 80.dp).testTag("elevated"),
                        shape = RoundedCornerShape(16.dp), color = Color.Transparent,
                        shadowElevation = 8.dp
                    ) {}
                }
            }
        }
        // 白色壁纸的中心列应该保持纯白，阴影只能出现在圆角容器外部。
        val image = mCompose.onNodeWithTag("elevated").captureToImage().toPixelMap()
        for (y in 4 until image.height - 4) {
            val color = image[image.width / 2, y]
            assertTrue("Unexpected shadow at row $y: $color", color.red > 0.99f && color.green > 0.99f && color.blue > 0.99f)
        }
    }

    /** 非居中裁切、页面偏移与气泡移动后仍对齐，禁用模糊时恢复底图且前景不受影响。 */
    @Test
    fun backdropStaysAlignedAndClippedWhenSurfaceMoves() {
        val opacity = mutableFloatStateOf(0f)
        val shift = mutableIntStateOf(0)
        val blur = mutableIntStateOf(70)
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }.asImageBitmap()
        val pixels = IntArray(10000) { index -> android.graphics.Color.rgb(index % 100 * 2, index / 100 * 2, 0) }
        val layer = Bitmap.createBitmap(pixels, 100, 100, Bitmap.Config.ARGB_8888).asImageBitmap()
        mCompose.setContent {
            AppTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().padding(start = 17.dp, top = 23.dp, end = 11.dp, bottom = 19.dp)) {
                    Box(Modifier.fillMaxSize().testTag("canvas")) {
                        PageBackdrop(PageBackgroundUiState(
                            PageBackground(BackgroundPage.Chat, scrim = 0f, focalX = 0.2f,
                                bubbleOpacity = opacity.floatValue, bubbleBlur = blur.intValue),
                            source, mapOf(PageSurfaceKind.Bubble to layer)
                        )) {
                            PageSurface(PageSurfaceKind.Bubble,
                                Modifier.offset(31.dp, (57 + shift.intValue).dp).size(100.dp, 90.dp).testTag("surface"),
                                shape = RoundedCornerShape(16.dp), color = Color.Transparent
                            ) {
                                Box(Modifier.fillMaxSize().padding(30.dp)) {
                                    Box(Modifier.size(10.dp).background(Color.Red))
                                }
                            }
                        }
                    }
                }
            }
        }
        // 同一壁纸坐标在容器移动后应继续取到对应像素，圆角外仍显示原始底图。
        for (offset in listOf(0, 80)) {
            mCompose.runOnIdle { shift.intValue = offset }
            mCompose.waitForIdle()
            val frame = mCompose.onNodeWithTag("canvas").fetchSemanticsNode().boundsInRoot
            val bounds = mCompose.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
            val image = mCompose.onNodeWithTag("canvas").captureToImage().toPixelMap()
            val x = (bounds.left - frame.left + bounds.width * 0.7f).toInt()
            val y = (bounds.top - frame.top + bounds.height * 0.7f).toInt()
            val scale = maxOf(frame.width, frame.height) / 100f
            val expectedX = (x - (frame.width - 100 * scale) * 0.2f) / scale
            val expectedY = (y - (frame.height - 100 * scale) * 0.5f) / scale
            val color = image[x, y]
            assertTrue(abs(color.red - expectedX * 2 / 255f) < 0.025f)
            assertTrue(abs(color.green - expectedY * 2 / 255f) < 0.025f)
            val corner = image[(bounds.left - frame.left + 1).toInt(), (bounds.top - frame.top + 1).toInt()]
            assertTrue(corner.blue > 0.95f && corner.red < 0.05f)
            // 纯红前景保留原像素，附加背景层不得覆盖或模糊内容。
            assertTrue((0 until image.width).any { px -> (0 until image.height).any { py ->
                val foreground = image[px, py]
                foreground.red > 0.99f && foreground.green < 0.01f && foreground.blue < 0.01f
            } })
        }
        // 透明度为零和模糊度为零分别关闭背景层，包括原本带半透明底色的子表面。
        for (disableByOpacity in listOf(true, false)) {
            mCompose.runOnIdle {
                opacity.floatValue = if (disableByOpacity) 1f else 0f
                blur.intValue = if (disableByOpacity) 70 else 0
            }
            mCompose.waitForIdle()
            val image = mCompose.onNodeWithTag("surface").captureToImage().toPixelMap()
            assertTrue(image[image.width * 3 / 4, image.height * 3 / 4].blue > 0.95f)
        }
    }
}
