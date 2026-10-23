package me.kafuuneko.rpclient.ui.widgets

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

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
                Box(
                    modifier = Modifier
                    .size(width = 240.dp, height = 160.dp)
                    .testTag("corners"),
                ) {
                    PageBackdrop(
                        state = PageBackgroundUiState(
                            settings = PageBackground(
                                page = if (mode.intValue < 4) BackgroundPage.Chat else BackgroundPage.General,
                                scrim = 0f,
                                bubbleOpacity = 0.2f,
                                bubbleBlur = 50,
                                opacity = 0.2f,
                                contentBlur = 50,
                            ),
                            image = white,
                            surfaceImages = mapOf(
                                PageSurfaceKind.Bubble to white,
                                PageSurfaceKind.Content to white,
                            ),
                        ),
                    ) {
                        val modifier = Modifier
                            .padding(30.dp)
                            .size(width = 160.dp, height = 80.dp)
                        val shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomEnd = 4.dp,
                            bottomStart = 18.dp,
                        )
                        when (mode.intValue / 4) {
                            0 -> PageSurface(
                                kind = PageSurfaceKind.Bubble,
                                modifier = modifier,
                                shape = shape,
                                color = Color.White.copy(alpha = 0.2f),
                                shadowElevation = if (mode.intValue < 2) 0.dp else 8.dp,
                            ) {}
                            1 -> AppSurface(
                                modifier = modifier,
                                shape = shape,
                                color = Color.White,
                                shadowElevation = 8.dp,
                                border = BorderStroke(width = 1.dp, color = Color.Black),
                            ) {}
                            else -> AppCard(
                                modifier = modifier,
                                shape = shape,
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                border = BorderStroke(width = 1.dp, color = Color.Black),
                            ) {}
                        }
                    }
                }
            }
        }
        // 覆盖浅深主题及原有投影开关，直接逐像素检查圆角所在的整个画布。
        for (value in 0..11) {
            mCompose.runOnIdle { mode.intValue = value }
            val image = mCompose
                .onNodeWithTag("corners")
                .captureToImage()
                .toPixelMap()
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val color = image[x, y]
                assertTrue(
                    "Residual outline in mode $value at $x,$y: $color",
                    color.red > 0.99f && color.green > 0.99f && color.blue > 0.99f,
                )
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
                PageBackdrop(
                    state = PageBackgroundUiState(
                        settings = PageBackground(
                            page = BackgroundPage.Chat,
                            scrim = 0f,
                            bubbleOpacity = 0.2f,
                            bubbleBlur = 50,
                        ),
                        image = white,
                        surfaceImages = mapOf(PageSurfaceKind.Bubble to white),
                    ),
                ) {
                    PageSurface(
                        kind = PageSurfaceKind.Bubble,
                        modifier = Modifier
                            .padding(32.dp)
                            .size(width = 180.dp, height = 80.dp)
                            .testTag("elevated"),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        shadowElevation = 8.dp,
                    ) {}
                }
            }
        }
        // 白色壁纸的中心列应该保持纯白，阴影只能出现在圆角容器外部。
        val image = mCompose
            .onNodeWithTag("elevated")
            .captureToImage()
            .toPixelMap()
        for (y in 4 until image.height - 4) {
            val color = image[image.width / 2, y]
            assertTrue(
                "Unexpected shadow at row $y: $color",
                color.red > 0.99f && color.green > 0.99f && color.blue > 0.99f,
            )
        }
    }

    /** 非居中裁切、页面偏移与气泡移动后仍对齐，禁用模糊时恢复底图且前景不受影响。 */
    @Test
    fun backdropStaysAlignedAndClippedWhenSurfaceMoves() {
        val opacity = mutableFloatStateOf(0f)
        val shift = mutableIntStateOf(0)
        val blur = mutableIntStateOf(70)
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }.asImageBitmap()
        val pixels = IntArray(size = 10000) { index -> android.graphics.Color.rgb(index % 100 * 2, index / 100 * 2, 0) }
        val layer = Bitmap.createBitmap(pixels, 100, 100, Bitmap.Config.ARGB_8888).asImageBitmap()
        mCompose.setContent {
            AppTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 17.dp, top = 23.dp, end = 11.dp, bottom = 19.dp),
                ) {
                    Box(
                        modifier = Modifier
                        .fillMaxSize()
                        .testTag("canvas"),
                    ) {
                        PageBackdrop(
                            state = PageBackgroundUiState(
                                settings = PageBackground(
                                    page = BackgroundPage.Chat,
                                    scrim = 0f,
                                    focalX = 0.2f,
                                    bubbleOpacity = opacity.floatValue,
                                    bubbleBlur = blur.intValue,
                                ),
                                image = source,
                                surfaceImages = mapOf(PageSurfaceKind.Bubble to layer),
                            ),
                        ) {
                            PageSurface(
                                kind = PageSurfaceKind.Bubble,
                                modifier = Modifier
                                    .offset(x = 31.dp, y = (57 + shift.intValue).dp)
                                    .size(width = 100.dp, height = 90.dp)
                                    .testTag("surface"),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Transparent,
                            ) {
                                Box(
                                    modifier = Modifier
                                    .fillMaxSize()
                                    .padding(30.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                        .size(10.dp)
                                        .background(Color.Red),
                                    )
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
            val image = mCompose
                .onNodeWithTag("canvas")
                .captureToImage()
                .toPixelMap()
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
            assertTrue(
                (0 until image.width).any { px -> (0 until image.height).any { py ->
                val foreground = image[px, py]
                foreground.red > 0.99f && foreground.green < 0.01f && foreground.blue < 0.01f
            } },
            )
        }
        // 透明度为零和模糊度为零分别关闭背景层，包括原本带半透明底色的子表面。
        for (disableByOpacity in listOf(true, false)) {
            mCompose.runOnIdle {
                opacity.floatValue = if (disableByOpacity) 1f else 0f
                blur.intValue = if (disableByOpacity) 70 else 0
            }
            mCompose.waitForIdle()
            val image = mCompose
                .onNodeWithTag("surface")
                .captureToImage()
                .toPixelMap()
            assertTrue(image[image.width * 3 / 4, image.height * 3 / 4].blue > 0.95f)
        }
    }
    /** 通用页面及会话子页分别取样所属区域，内嵌底板继承透明度且前景保持实色。 */
    @Test
    fun generalRegionsUseIndependentLayersWithoutFadingChildren() {
        val pageIndex = mutableIntStateOf(0)
        fun image(color: Int) = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(color) }.asImageBitmap()
        val source = image(android.graphics.Color.BLUE)
        val layers = mapOf(
            PageSurfaceKind.TopBar to image(android.graphics.Color.RED),
            PageSurfaceKind.Content to image(android.graphics.Color.GREEN),
            PageSurfaceKind.Bubble to image(android.graphics.Color.MAGENTA),
            PageSurfaceKind.BottomBar to image(android.graphics.Color.YELLOW),
        )
        mCompose.setContent {
            AppTheme(dynamicColor = false) {
                Box(Modifier.size(width = 240.dp, height = 300.dp)) {
                    PageBackdrop(
                        state = PageBackgroundUiState(
                            settings = PageBackground(
                                page = BackgroundPage.entries[pageIndex.intValue],
                                scrim = 0f,
                                topBarOpacity = 0f,
                                opacity = 0.5f,
                                bottomBarOpacity = 0.75f,
                                topBarBlur = 50,
                                contentBlur = 50,
                                bottomBarBlur = 50,
                                bubbleOpacity = 0.25f,
                                bubbleBlur = 50,
                            ),
                            image = source,
                            surfaceImages = layers,
                        ),
                        useContainerAppearance = true,
                    ) {
                        AppPageScaffold(
                            contentWindowInsets = WindowInsets(left = 0.dp),
                            topBar = {
                            AppSurface(
                                modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("general-top"),
                                color = Color.White,
                            ) {}
                        },
                            bottomBar = {
                            AppSurface(
                                modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("general-bottom"),
                                color = Color.White,
                            ) {}
                        },
                        ) { padding ->
                            AppCard(
                                modifier = Modifier
                                .padding(padding)
                                .size(width = 160.dp, height = 100.dp)
                                .testTag("general-card"),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                            ) {
                                AppSurface(
                                    modifier = Modifier
                                    .size(20.dp)
                                    .testTag("general-label"),
                                    color = Color.Red,
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier
                                            .offset(x = 12.dp, y = 12.dp)
                                            .size(3.dp)
                                            .background(Color.Red)
                                            .testTag("foreground"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 使用精确背景色检测区域串线、重复透明度和顶层实色遮挡。
        for (page in BackgroundPage.entries) {
            mCompose.runOnIdle { pageIndex.intValue = page.ordinal }
            val content = if (page == BackgroundPage.Chat || page == BackgroundPage.Group)
                Color(red = 1f, green = 0.25f, blue = 1f) else Color(red = 0.5f, green = 1f, blue = 0.5f)
            val expected = mapOf(
                "general-top" to Color.Red,
                "general-bottom" to Color(
                    red = 1f,
                    green = 1f,
                    blue = 0.75f,
                ),
                "general-card" to content,
                "general-label" to if (page == BackgroundPage.Chat || page == BackgroundPage.Group)
                    Color(red = 1f, green = 0.1875f, blue = 0.75f) else Color(red = 0.75f, green = 0.5f, blue = 0.25f),
                "foreground" to Color.Red,
            )
            expected.forEach { (tag, color) ->
                val pixels = mCompose
                    .onNodeWithTag(testTag = tag, useUnmergedTree = true)
                    .captureToImage()
                    .toPixelMap()
                val actual = pixels[pixels.width / 2, pixels.height / 2]
                assertTrue(
                    "Unexpected region $page/$tag: $actual",
                    abs(actual.red - color.red) < 0.015f
                    && abs(actual.green - color.green) < 0.015f && abs(actual.blue - color.blue) < 0.015f,
                )
            }
        }
    }

    /** 覆盖栏必须取样滚动内容，不能直接盖上壁纸；动态内容改变后也要更新取样。 */
    @Test
    fun overlaySamplesActualContentInsteadOfErasingIt() {
        val mode = mutableIntStateOf(0)
        val wallpaper = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.BLUE) }.asImageBitmap()
        mCompose.setContent {
            AppTheme(dynamicColor = false) {
                Box(Modifier.size(width = 240.dp, height = 300.dp)) {
                    PageBackdrop(
                        state = PageBackgroundUiState(
                            settings = PageBackground(
                                page = BackgroundPage.General,
                                scrim = 0f,
                                bottomBarOpacity = 0f,
                                bottomBarBlur = 50,
                            ),
                            image = wallpaper,
                            surfaceImages = mapOf(PageSurfaceKind.BottomBar to wallpaper),
                        ),
                    ) {
                        PageOverlayHost(
                            content = {
                            Box(
                                modifier = Modifier
                                .fillMaxSize()
                                .background(if (mode.intValue == 0) Color.Red else Color.Green),
                            )
                        },
                            overlay = {
                            Box(Modifier.fillMaxSize()) {
                                AppSurface(
                                    modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .size(width = 200.dp, height = 80.dp)
                                    .testTag("overlay"),
                                    shape = RoundedCornerShape(20.dp),
                                    kind = PageSurfaceKind.BottomBar,
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        Box(
                                            modifier = Modifier
                                            .offset(x = 30.dp, y = 30.dp)
                                            .size(8.dp)
                                            .background(Color.White)
                                            .testTag("overlay-icon"),
                                        )
                                    }
                                }
                            }
                        },
                        )
                    }
                }
            }
        }
        // 中心像素必须来自实际页面；导航栏自己的图标不能被模糊或录入后景。
        for (value in 0..1) {
            mCompose.runOnIdle { mode.intValue = value }
            mCompose.waitForIdle()
            val pixels = mCompose
                .onNodeWithTag("overlay")
                .captureToImage()
                .toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue(
                "Overlay erased its source: $center",
                center.blue < 0.01f
                && (if (value == 0) center.red else center.green) > 0.99f,
            )
            val icon = mCompose
                .onNodeWithTag("overlay-icon")
                .captureToImage()
                .toPixelMap()[0, 0]
            assertTrue(icon.red > 0.99f && icon.green > 0.99f && icon.blue > 0.99f)
        }
    }

}
