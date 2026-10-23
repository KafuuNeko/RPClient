package me.kafuuneko.rpclient.feature.imagecrop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imagecrop.presentation.CropMaskShape
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiIntent
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiState
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.LocalPageContentOpacity
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.ui.widgets.pageSurfaceBlur
import kotlin.math.roundToInt
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedButton as OutlinedButton

private val DarkroomBackgroundColor = Color(0xFF090C10)
private val DarkroomSurfaceColor = Color(0xFF141923)
private val DarkroomOverlayColor = Color(0xB8000000)

/** 图片裁剪页 Compose 入口，仅渲染状态并发送交互意图。 */
@Composable
fun ImageCropLayout(
    uiState: ImageCropUiState,
    emit: ImageCropUiIntent.() -> Unit,
) {
    BackHandler(enabled = uiState !is ImageCropUiState.Finished, onBack = { ImageCropUiIntent.Back.emit() })
    when (uiState) {
        ImageCropUiState.None, ImageCropUiState.Loading -> ImageCropLoading(emit)
        is ImageCropUiState.Normal -> ImageCropNormal(uiState, emit)
        is ImageCropUiState.Failed -> ImageCropFailed(emit)
        is ImageCropUiState.Finished -> ImageCropLayout(uiState = uiState.previous, emit = { })
    }
}

/** 沉浸式暗房全屏容器，沉底状态栏与导航栏。 */
@Composable
private fun DarkroomContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            modifier = modifier
            .fillMaxSize()
            .background(pageCanvasColor(DarkroomBackgroundColor)),
        ) { content() }
    }
}

/** 顶部极简悬浮操作栏。 */
@Composable
private fun ImageCropTopBar(
    isDefaultTransform: Boolean,
    isSaving: Boolean,
    emit: ImageCropUiIntent.() -> Unit,
) {
    AppSurface(
        kind = PageSurfaceKind.TopBar,
        color = DarkroomBackgroundColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { ImageCropUiIntent.Back.emit() },
                enabled = !isSaving,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.image_crop_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                color = Color.White,
                modifier = Modifier.weight(1f),
            )

            TextButton(
                onClick = { ImageCropUiIntent.Reset.emit() },
                enabled = !isDefaultTransform && !isSaving,
            ) {
                Text(
                    text = stringResource(R.string.reset),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (!isDefaultTransform && !isSaving) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color(0x55FFFFFF)
                    },
                )
            }
        }
    }
}

@Composable
private fun ImageCropNormal(
    state: ImageCropUiState.Normal,
    emit: ImageCropUiIntent.() -> Unit,
) {
    DarkroomContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            ImageCropTopBar(
                isDefaultTransform = state.transform.isDefault,
                isSaving = state.saving,
                emit = emit,
            )

            // 中央全视口画布区域
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pageSurfaceBlur(PageSurfaceKind.Content)
                    .background(DarkroomBackgroundColor.copy(alpha = LocalPageContentOpacity.current)),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val canvasWidthPx = constraints.maxWidth.toFloat()
                val canvasHeightPx = constraints.maxHeight.toFloat()

                // 计算裁剪框尺寸：居中正方形
                val cropSideDp = minOf(
                    a = maxWidth - 32.dp,
                    b = maxHeight - 24.dp,
                    c = 420.dp,
                ).coerceAtLeast(160.dp)
                val cropSidePx = with(density) { cropSideDp.toPx() }

                val cropLeft = (canvasWidthPx - cropSidePx) / 2f
                val cropTop = (canvasHeightPx - cropSidePx) / 2f
                val cropRect = Rect(
                    left = cropLeft,
                    top = cropTop,
                    right = cropLeft + cropSidePx,
                    bottom = cropTop + cropSidePx,
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(state.saving, cropSidePx) {
                            if (state.saving) return@pointerInput
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (cropSidePx > 0f) {
                                    ImageCropUiIntent.Transform(
                                        panX = pan.x / cropSidePx,
                                        panY = pan.y / cropSidePx,
                                        zoomChange = zoom,
                                    ).emit()
                                }
                            }
                        }
                        .pointerInput(state.saving, state.transform) {
                            if (state.saving) return@pointerInput
                            detectTapGestures(
                                onDoubleTap = {
                                    if (state.transform.zoom > 1.05f) {
                                        ImageCropUiIntent.Transform(
                                            panX = -state.transform.offsetX,
                                            panY = -state.transform.offsetY,
                                            zoomChange = 1f / state.transform.zoom,
                                        ).emit()
                                    } else {
                                        ImageCropUiIntent.Transform(
                                            panX = 0f,
                                            panY = 0f,
                                            zoomChange = 2f,
                                        ).emit()
                                    }
                                },
                            )
                        },
                    onDraw = {
                        val transform = state.transform
                        val effectiveAspect = transform.effectiveAspectRatio
                        val baseW = maxOf(a = effectiveAspect, b = 1f) * cropSidePx
                        val baseH = maxOf(a = 1f / effectiveAspect, b = 1f) * cropSidePx
                        val drawW = baseW * transform.zoom
                        val drawH = baseH * transform.zoom

                        val imgCenterX = cropRect.center.x + transform.offsetX * cropSidePx
                        val imgCenterY = cropRect.center.y + transform.offsetY * cropSidePx

                        // 全局绘制原图（以图片中心为 pivot 进行平移、旋转与水平镜像）
                        withTransform(
                            transformBlock = {
                            translate(left = imgCenterX, top = imgCenterY)
                            rotate(degrees = transform.rotationDegrees.toFloat(), pivot = Offset.Zero)
                            scale(
                                scaleX = if (transform.isFlippedHorizontal) -1f else 1f,
                                scaleY = 1f,
                                pivot = Offset.Zero,
                            )
                        },
                        ) {
                            val imgW = if (transform.isRotated90) drawH else drawW
                            val imgH = if (transform.isRotated90) drawW else drawH
                            drawImage(
                                image = state.image,
                                dstOffset = IntOffset((-imgW / 2f).roundToInt(), (-imgH / 2f).roundToInt()),
                                dstSize = IntSize(imgW.roundToInt(), imgH.roundToInt()),
                                filterQuality = FilterQuality.High,
                            )
                        }

                        // 绘制 EvenOdd 镂空暗区遮罩
                        val cornerRadiusPx = 24.dp.toPx()
                        val maskPath = Path().apply {
                            addRect(Rect(left = 0f, top = 0f, right = size.width, bottom = size.height))
                            if (state.maskShape == CropMaskShape.Circle) {
                                addOval(cropRect)
                            } else {
                                addRoundRect(RoundRect(cropRect, CornerRadius(cornerRadiusPx)))
                            }
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(path = maskPath, color = DarkroomOverlayColor)

                        // 绘制九宫格构图辅助线（三分法）
                        val oneThird = cropSidePx / 3f
                        val twoThirds = cropSidePx * 2f / 3f
                        val gridColor = Color(0x30FFFFFF)
                        val gridStrokePx = 1.dp.toPx()

                        drawLine(
                            color = gridColor,
                            start = Offset(cropRect.left + oneThird, cropRect.top),
                            end = Offset(cropRect.left + oneThird, cropRect.bottom),
                            strokeWidth = gridStrokePx,
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(cropRect.left + twoThirds, cropRect.top),
                            end = Offset(cropRect.left + twoThirds, cropRect.bottom),
                            strokeWidth = gridStrokePx,
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(cropRect.left, cropRect.top + oneThird),
                            end = Offset(cropRect.right, cropRect.top + oneThird),
                            strokeWidth = gridStrokePx,
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(cropRect.left, cropRect.top + twoThirds),
                            end = Offset(cropRect.right, cropRect.top + twoThirds),
                            strokeWidth = gridStrokePx,
                        )

                        // 绘制裁剪框轮廓与 4 角专业 L 形高光角标
                        if (state.maskShape == CropMaskShape.Circle) {
                            drawOval(
                                color = Color(0x80FFFFFF),
                                topLeft = cropRect.topLeft,
                                size = cropRect.size,
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        } else {
                            drawRoundRect(
                                color = Color(0x60FFFFFF),
                                topLeft = cropRect.topLeft,
                                size = cropRect.size,
                                cornerRadius = CornerRadius(cornerRadiusPx),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )

                            val bracketLength = 18.dp.toPx()
                            val bracketStroke = 3.dp.toPx()
                            val bracketColor = Color.White

                            // 左上角
                            drawLine(
                                color = bracketColor,
                                start = Offset(cropRect.left, cropRect.top),
                                end = Offset(
                                    x = cropRect.left + bracketLength,
                                    y = cropRect.top,
                                ),
                                strokeWidth = bracketStroke,
                            )
                            drawLine(
                                color = bracketColor,
                                start = Offset(cropRect.left, cropRect.top),
                                end = Offset(
                                    x = cropRect.left,
                                    y = cropRect.top + bracketLength,
                                ),
                                strokeWidth = bracketStroke,
                            )

                            // 右上角
                            drawLine(
                                color = bracketColor,
                                start = Offset(cropRect.right, cropRect.top),
                                end = Offset(
                                    x = cropRect.right - bracketLength,
                                    y = cropRect.top,
                                ),
                                strokeWidth = bracketStroke,
                            )
                            drawLine(
                                color = bracketColor,
                                start = Offset(cropRect.right, cropRect.top),
                                end = Offset(
                                    x = cropRect.right,
                                    y = cropRect.top + bracketLength,
                                ),
                                strokeWidth = bracketStroke,
                            )

                            // 左下角
                            drawLine(
                                color = bracketColor,
                                start = Offset(cropRect.left, cropRect.bottom),
                                end = Offset(
                                    x = cropRect.left + bracketLength,
                                    y = cropRect.bottom,
                                ),
                                strokeWidth = bracketStroke,
                            )
                            drawLine(
                                color = bracketColor,
                                start = Offset(cropRect.left, cropRect.bottom),
                                end = Offset(
                                    x = cropRect.left,
                                    y = cropRect.bottom - bracketLength,
                                ),
                                strokeWidth = bracketStroke,
                            )

                            // 右下角
                            drawLine(
                                color = bracketColor,
                                start = Offset(cropRect.right, cropRect.bottom),
                                end = Offset(
                                    x = cropRect.right - bracketLength,
                                    y = cropRect.bottom,
                                ),
                                strokeWidth = bracketStroke,
                            )
                            drawLine(
                                color = bracketColor,
                                start = Offset(cropRect.right, cropRect.bottom),
                                end = Offset(
                                    x = cropRect.right,
                                    y = cropRect.bottom - bracketLength,
                                ),
                                strokeWidth = bracketStroke,
                            )
                        }
                    },
                )
            }

            // 底部控制与操作区
            ImageCropBottomControls(state, emit)
        }
    }
}

/** 底部现代悬浮编辑工具栏与确认操作区。 */
@Composable
private fun ImageCropBottomControls(
    state: ImageCropUiState.Normal,
    emit: ImageCropUiIntent.() -> Unit,
) {
    AppSurface(kind = PageSurfaceKind.BottomBar, color = DarkroomSurfaceColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 提示文案
            Text(
                text = stringResource(R.string.image_crop_helper),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0x99FFFFFF),
            )

            Spacer(Modifier.height(14.dp))

            // 核心编辑工具条（旋转、水平镜像、形态切换）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CropToolButton(
                    icon = Icons.AutoMirrored.Rounded.RotateRight,
                    label = stringResource(R.string.image_crop_rotate),
                    enabled = !state.saving,
                    onClick = { ImageCropUiIntent.RotateRight.emit() },
                )

                CropToolButton(
                    icon = Icons.Rounded.Flip,
                    label = stringResource(R.string.image_crop_flip),
                    enabled = !state.saving,
                    isActive = state.transform.isFlippedHorizontal,
                    onClick = { ImageCropUiIntent.FlipHorizontal.emit() },
                )

                CropToolButton(
                    icon = if (state.maskShape == CropMaskShape.Circle) {
                        Icons.Rounded.CropSquare
                    } else {
                        Icons.Rounded.AccountCircle
                    },
                    label = if (state.maskShape == CropMaskShape.Circle) {
                        stringResource(R.string.image_crop_shape_squircle)
                    } else {
                        stringResource(R.string.image_crop_shape_circle)
                    },
                    enabled = !state.saving,
                    onClick = { ImageCropUiIntent.ToggleMaskShape.emit() },
                )
            }

            Spacer(Modifier.height(16.dp))

            // 操作按钮行（取消 / 完成）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { ImageCropUiIntent.Back.emit() },
                    enabled = !state.saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(width = 1.dp, color = Color(0x33FFFFFF)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color(0x44FFFFFF),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontWeight = FontWeight.Medium,
                    )
                }

                Button(
                    onClick = { ImageCropUiIntent.Confirm.emit() },
                    enabled = !state.saving,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.confirm),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** 单个编辑工具小组件。 */
@Composable
private fun CropToolButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint = when {
        !enabled -> Color(0x44FFFFFF)
        isActive -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0x1AFFFFFF),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = tint,
        )
    }
}

@Composable
private fun ImageCropLoading(emit: ImageCropUiIntent.() -> Unit) {
    DarkroomContainer {
        Column(Modifier.fillMaxSize()) {
            ImageCropTopBar(isDefaultTransform = true, isSaving = true, emit = emit)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ImageCropFailed(emit: ImageCropUiIntent.() -> Unit) {
    DarkroomContainer {
        Column(Modifier.fillMaxSize()) {
            ImageCropTopBar(isDefaultTransform = true, isSaving = false, emit = emit)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.image_crop_load_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ImageCropPreview() {
    ActivityPreview(darkTheme = true) {
        ImageCropLayout(uiState = ImageCropUiState.Failed(ImageCropUiState.Loading), emit = { })
    }
}
