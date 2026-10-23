package me.kafuuneko.rpclient.ui.widgets

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.appearance.BackgroundBlur
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.appearance.surfaceBlur
import me.kafuuneko.rpclient.libs.appearance.surfaceOpacity
import kotlin.math.roundToInt

/** 只记录覆盖栏下方的内容，避免将导航栏自身反复录入产生反馈残影。 */
internal class PageOverlaySource(val layer: GraphicsLayer, val preview: GraphicsLayer) {
    var origin: Offset = Offset.Zero
    var revision by mutableIntStateOf(0)
    private var mDrawCount = 0

    /** 写入单独的绘制代数；记录端不订阅自身代数，避免触发无限重绘。 */
    fun changed() { revision = ++mDrawCount }
}

internal val LocalPageOverlaySource = staticCompositionLocalOf<PageOverlaySource?> { null }

/**
 * 为悬浮底栏提供实际后景，页面内容与覆盖栏使用两个独立绘制入口。
 * @param content 需要被覆盖栏取样的滚动页面内容。
 * @param overlay 位于内容上方的导航或操作栏。
 */
@Composable
fun PageOverlayHost(content: @Composable () -> Unit, overlay: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val preview = rememberGraphicsLayer()
    val source = remember(layer, preview) { PageOverlaySource(layer, preview) }
    val background = LocalPageBackdropState.current
    val appearance = LocalGeneralPageSurfaces.current
    val settings = background.settings
    val record = LocalGeneralPageSurfaces.current && settings != null
        && settings.surfaceOpacity(PageSurfaceKind.BottomBar) < 1f && settings.surfaceBlur(PageSurfaceKind.BottomBar) > 0
    // 无模糊时保留原始透明合成，不创建每帧快照。
    Box(Modifier.fillMaxSize()) {
        val capture = if (!record) Modifier else Modifier
            .graphicsLayer()
            .onGloballyPositioned(onGloballyPositioned = { layoutCoordinates -> source.origin = layoutCoordinates.positionInRoot() })
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    val ratio = minOf(a = 1f, b = 360f / maxOf(size.width, size.height))
                    preview.record(
                        size = IntSize(
                            width = (size.width * ratio).roundToInt().coerceAtLeast(1),
                            height = (size.height * ratio).roundToInt().coerceAtLeast(1),
                        ),
                    ) {
                        scale(ratio, ratio, Offset.Zero) { drawLayer(layer) }
                    }
                }
                source.changed()
            }
        Box(
            modifier = Modifier
            .fillMaxSize()
            .then(capture),
        ) {
            // 记录完整后景，透明卡片下的壁纸也参与同一次合成。
            PageBackdrop(state = background, useContainerAppearance = appearance, content = content)
        }
        CompositionLocalProvider(LocalPageOverlaySource provides if (record) source else null) { overlay() }
    }
}

/**
 * 合成包含壁纸的完整后景，防止底栏把滚过来的卡片和文字抹成壁纸。
 * @param source 不包含当前覆盖栏的绘制记录。
 * @param amount 当前栏位的模糊强度。
 * @param shape 覆盖栏的实际轮廓。
 */
@Composable
internal fun Modifier.pageOverlayBlur(source: PageOverlaySource, amount: Int, shape: Shape): Modifier {
    val blurred = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val software = rememberOverlaySoftwareBlur(source, amount)
    // 新系统直接重放图层；旧系统使用有界小图与原有 CPU 模糊，避免在主线程卷积。
    return onGloballyPositioned(onGloballyPositioned = { origin = it.positionInRoot() }).drawWithContent {
        val revision = source.revision
        if (revision > 0 && source.layer.size.width > 0) {
            val path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }
            val relative = source.origin - origin
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val radius = maxOf(source.layer.size.width, source.layer.size.height) * amount / 100f * 0.018f
                val margin = (radius * 3).roundToInt()
                blurred.renderEffect = BlurEffect(radiusX = radius, radiusY = radius, edgeTreatment = TileMode.Clamp)
                // 卷积仅覆盖底栏及边缘采样留白，避免每次滚动都模糊整个屏幕。
                blurred.record(
                    size = IntSize(
                        width = size.width.roundToInt() + margin * 2,
                        height = size.height.roundToInt() + margin * 2,
                    ),
                ) {
                    translate(relative.x + margin, relative.y + margin) { drawLayer(source.layer) }
                }
                clipPath(path) { translate(-margin.toFloat(), -margin.toFloat()) { drawLayer(blurred) } }
            } else {
                clipPath(path) {
                    translate(relative.x, relative.y) {
                        if (software != null) drawImage(image = software, dstSize = source.layer.size)
                        else drawLayer(source.layer)
                    }
                }
            }
        }
        drawContent()
    }
}

/** 旧系统只处理最新绘制帧，停留时不重复计算；离开页面自动取消收集。 */
@Composable
private fun rememberOverlaySoftwareBlur(source: PageOverlaySource, amount: Int): ImageBitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return null
    var image by remember(source, amount) { mutableStateOf<ImageBitmap?>(value = null) }
    LaunchedEffect(source, amount) {
        // 合并滚动时的过期帧，限制分辨率和刷新频率，避免图片随帧排队。
        snapshotFlow { source.revision }.conflate().collect { revision ->
            if (revision == 0 || source.preview.size.width == 0) return@collect
            val snapshot = source.preview.toImageBitmap()
            image = withContext(Dispatchers.Default) {
                val pixels = IntArray(snapshot.width * snapshot.height)
                snapshot.readPixels(pixels)
                val radius = (maxOf(snapshot.width, snapshot.height) * amount / 100f * 0.018f).roundToInt().coerceAtLeast(minimumValue = 1)
                val result = BackgroundBlur.apply(pixels, snapshot.width, snapshot.height, radius)
                Bitmap.createBitmap(result, snapshot.width, snapshot.height, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
            delay(timeMillis = 32)
        }
    }
    return image
}
