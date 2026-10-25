package me.kafuuneko.rpclient.feature.imagecrop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.imagecrop.presentation.CropMaskShape
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropAspectRatio
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropPurpose
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiIntent
import me.kafuuneko.rpclient.feature.imagecrop.presentation.ImageCropUiState
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedButton as OutlinedButton
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip

private val DarkroomBackgroundColor = Color(0xFF090C10)
private val DarkroomSurfaceColor = Color(0xFF141923)

/** 图片裁剪页 Compose 入口，仅渲染状态并发送交互意图。 */
@Composable
fun ImageCropLayout(
    uiState: ImageCropUiState,
    emit: ImageCropUiIntent.() -> Unit,
) {
    BackHandler(
        enabled = uiState !is ImageCropUiState.Finished,
        onBack = { ImageCropUiIntent.Back.emit() })
    when (uiState) {
        ImageCropUiState.None -> ImageCropLoading(emit)
        is ImageCropUiState.Loading -> ImageCropLoading(
            emit = emit,
            isBackground = uiState.purpose == ImageCropPurpose.Background,
        )

        is ImageCropUiState.Normal -> ImageCropNormal(uiState, emit)
        is ImageCropUiState.Failed -> ImageCropFailed(
            emit = emit,
            isBackground = (uiState.previous as? ImageCropUiState.Loading)?.purpose == ImageCropPurpose.Background,
        )

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
    isBackground: Boolean = false,
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
                text = stringResource(if (isBackground) R.string.image_crop_background_title else R.string.image_crop_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    // 画布独占剩余空间，矩形裁剪框随当前窗口适配。
    DarkroomContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            ImageCropTopBar(
                isDefaultTransform = state.transform.isDefault && state.aspectRatio == ImageCropAspectRatio.Screen,
                isSaving = state.saving,
                isBackground = state.purpose == ImageCropPurpose.Background,
                emit = emit,
            )
            ImageCropCanvas(
                state = state,
                emit = emit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(DarkroomBackgroundColor),
            )
            ImageCropBottomControls(state, emit)
        }
    }
}

/** 底部现代悬浮编辑工具栏与确认操作区。 */
@OptIn(ExperimentalLayoutApi::class)
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
                text = stringResource(if (state.purpose == ImageCropPurpose.Background) R.string.image_crop_background_helper else R.string.image_crop_helper),
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

                if (state.purpose == ImageCropPurpose.Avatar) CropToolButton(
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

            if (state.purpose == ImageCropPurpose.Background) {
                // 暗房中使用不透明选中色与勾选标识，避免浅色描边比实际选中项更突出。
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageCropAspectRatio.entries.forEach { ratio ->
                        val selected = state.aspectRatio == ratio
                        FilterChip(
                            selected = selected,
                            enabled = !state.saving,
                            onClick = { ImageCropUiIntent.SelectAspectRatio(ratio).emit() },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else null,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(
                                    alpha = 0.24f
                                ),
                            ),
                            colors = pageFilterChipColors(
                                labelColor = Color.White.copy(alpha = 0.72f),
                                disabledLabelColor = Color(0x44FFFFFF),
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            label = {
                                Text(stringResource(if (ratio == ImageCropAspectRatio.Screen) R.string.image_crop_ratio_screen else R.string.image_crop_ratio_original))
                            },
                        )
                    }
                }
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
                    color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(
                        0x1AFFFFFF
                    ),
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
private fun ImageCropLoading(emit: ImageCropUiIntent.() -> Unit, isBackground: Boolean = false) {
    // 读取源图前就显示加载反馈，并保留返回入口以便取消慢速相册读取。
    DarkroomContainer {
        Column(Modifier.fillMaxSize()) {
            ImageCropTopBar(
                isDefaultTransform = true,
                isSaving = false,
                emit = emit,
                isBackground = isBackground
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(text = stringResource(R.string.image_loading), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ImageCropFailed(emit: ImageCropUiIntent.() -> Unit, isBackground: Boolean = false) {
    // 加载失败保留对应用途的标题，用户可直接返回继续使用原有图片。
    DarkroomContainer {
        Column(Modifier.fillMaxSize()) {
            ImageCropTopBar(
                isDefaultTransform = true,
                isSaving = false,
                emit = emit,
                isBackground = isBackground
            )
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
        ImageCropLayout(uiState = ImageCropUiState.Failed(ImageCropUiState.Loading()), emit = { })
    }
}
