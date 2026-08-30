package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import java.util.Locale
import kotlin.math.roundToInt

/** 数值编辑对话框的可选快捷输入项。 */
data class NumericEditQuickOption(
    /** 供界面展示的简短标签。 */
    val label: String,
    /** 点击快捷选项后写入数值编辑器的值。 */
    val value: String
)

/** 参数滑动条配置。 */
data class SliderConfig(
    /** 当前对象覆盖的有效区间。 */
    val range: ClosedFloatingPointRange<Float>,
    /** 数值控件每次增减使用的步长。 */
    val step: Float = 0.05f,
    /** 数值控件最小边界的显示文本。 */
    val minLabel: String? = null,
    /** 数值控件最大边界的显示文本。 */
    val maxLabel: String? = null
)

/**
 * 现代化通用数值与连续参数调节对话框。
 *
 * 当传入 [sliderConfig] 时，自动启用「大字号动态看板 + 双侧步进微调器 + 连续滑动条 + 快捷胶囊」复合模式；
 * 否则降级展示为带键盘的精准数值输入框。
 */
@Composable
fun NumericEditDialog(
    title: String,
    value: String,
    decimalInput: Boolean,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sliderConfig: SliderConfig? = null,
    quickOptions: List<NumericEditQuickOption> = emptyList()
) {
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    if (sliderConfig == null) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }

    AppDialogScaffold(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.edit_numeric_value_title, title),
        subtitle = subtitle,
        badgeIcon = Icons.Rounded.Tune,
        badgeTone = DialogBadgeTone.Primary,
        compactHeader = true,
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = value.isNotBlank(),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sliderConfig != null) {
                // 连续范围使用滑动条与步进按钮共同调整
                val currentFloat = value.toFloatOrNull() ?: sliderConfig.range.start
                val clampedFloat = currentFloat.coerceIn(sliderConfig.range.start, sliderConfig.range.endInclusive)

                // 顶部突出显示当前值，并提供精确的单步增减操作
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 减少操作会在最小值处截断，避免生成越界输入
                    FilledTonalIconButton(
                        onClick = {
                            val nextVal = (clampedFloat - sliderConfig.step).coerceAtLeast(sliderConfig.range.start)
                            onValueChange(String.format(Locale.US, "%.2f", nextVal))
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = stringResource(R.string.decrease)
                        )
                    }

                    // 平滑切换格式化后的当前数值
                    AnimatedContent(
                        targetState = String.format(Locale.US, "%.2f", clampedFloat),
                        label = "numericIndicator"
                    ) { formatted ->
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // 增加操作会在最大值处截断，避免生成越界输入
                    FilledTonalIconButton(
                        onClick = {
                            val nextVal = (clampedFloat + sliderConfig.step).coerceAtMost(sliderConfig.range.endInclusive)
                            onValueChange(String.format(Locale.US, "%.2f", nextVal))
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.increase)
                        )
                    }
                }

                // 滑动结果按步长吸附，确保与步进按钮产生一致的值
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = clampedFloat,
                        onValueChange = { newVal ->
                            val snapped = (newVal / sliderConfig.step).roundToInt() * sliderConfig.step
                            val formatted = String.format(Locale.US, "%.2f", snapped)
                            if (formatted != value) {
                                onValueChange(formatted)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        valueRange = sliderConfig.range,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!sliderConfig.minLabel.isNullOrBlank() || !sliderConfig.maxLabel.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sliderConfig.minLabel.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = sliderConfig.maxLabel.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // 无连续范围时保留直接输入，适配较大或离散数值
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text(title) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimalInput) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Number
                        },
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (value.isNotBlank()) onConfirm()
                        }
                    ),
                    trailingIcon = {
                        if (value.isNotEmpty()) {
                            IconButton(onClick = { onValueChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }

            // 预设项作为快捷输入，仍复用同一数值变更回调
            QuickOptionChips(
                value = value,
                options = quickOptions,
                onValueChange = { selectedValue ->
                    onValueChange(selectedValue)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickOptionChips(
    value: String,
    options: List<NumericEditQuickOption>,
    onValueChange: (String) -> Unit
) {
    if (options.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.quick_select),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                val isSelected = value == option.value
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    label = "chipBackground"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "chipContent"
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = backgroundColor,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onValueChange(option.value) }
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "NumericEditDialog - Slider", showBackground = true)
@Composable
private fun NumericEditDialogSliderPreview() {
    AppTheme(dynamicColor = false) {
        NumericEditDialog(
            title = "Temperature",
            subtitle = "控制回复随机性：越低越严谨确定，越高越自由丰富",
            value = "0.70",
            decimalInput = true,
            onValueChange = {},
            onConfirm = {},
            onDismiss = {},
            sliderConfig = SliderConfig(
                range = 0.00f..2.00f,
                step = 0.05f,
                minLabel = "0.0 严谨",
                maxLabel = "2.0 发散"
            ),
            quickOptions = listOf(
                NumericEditQuickOption("0.2 精准", "0.20"),
                NumericEditQuickOption("0.7 均衡", "0.70"),
                NumericEditQuickOption("1.2 创意", "1.20")
            )
        )
    }
}

@Preview(name = "NumericEditDialog - Input Field", showBackground = true)
@Composable
private fun NumericEditDialogInputPreview() {
    AppTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            NumericEditDialog(
                title = "Max Tokens",
                subtitle = "单次回复最大生成的 Token 上限",
                value = "4096",
                decimalInput = false,
                onValueChange = {},
                onConfirm = {},
                onDismiss = {},
                quickOptions = listOf(
                    NumericEditQuickOption("2K", "2048"),
                    NumericEditQuickOption("4K", "4096"),
                    NumericEditQuickOption("8K", "8192")
                )
            )
        }
    }
}
