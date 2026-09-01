package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 将外部字符串状态绑定为可控制选择区的 [TextFieldState]。
 *
 * @param value 外部持有的文本内容
 * @param onValueChange 用户或调用方修改文本状态后的回调
 * @return 可直接编辑和插入内容的文本框状态
 */
@Composable
fun rememberBoundTextFieldState(
    value: String,
    onValueChange: (String) -> Unit
): TextFieldState {
    val textFieldState = remember { TextFieldState(value) }
    val currentExternalValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)

    // 外部载入、撤销或清空文本时，同步内容并尽量保留用户当前选择区。
    LaunchedEffect(value) {
        if (textFieldState.text.toString() == value) return@LaunchedEffect
        val previousSelection = textFieldState.selection
        textFieldState.edit {
            replace(0, length, value)
            selection = TextRange(
                start = previousSelection.start.coerceAtMost(value.length),
                end = previousSelection.end.coerceAtMost(value.length)
            )
        }
    }
    // 状态式文本框独立承接输入法编辑，再将稳定文本变化回传给页面状态树。
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                if (text != currentExternalValue.value) {
                    currentOnValueChange.value(text)
                }
            }
    }
    return textFieldState
}

/**
 * 使用调用方持有的文本编辑状态绘制带侧边指示器的可换行文本框。
 *
 * 适用于需要精确控制光标、选择区、宏插入或输出高亮的复杂编辑区域。
 *
 * @param state 文本与选择区状态
 * @param modifier 文本框布局修饰符
 * @param enabled 是否允许编辑
 * @param readOnly 是否只读
 * @param textStyle 文本样式
 * @param label 文本框标签
 * @param placeholder 空内容占位组件
 * @param leadingIcon 起始图标
 * @param trailingIcon 末尾图标
 * @param supportingText 辅助说明组件
 * @param isError 是否显示错误状态
 * @param outputTransformation 仅影响展示的文本转换
 * @param keyboardOptions 软键盘配置
 * @param onKeyboardAction 软键盘动作回调
 * @param singleLine 是否限制为单行；单行输入不启用纵向指示器
 * @param minLines 最少显示行数
 * @param maxLines 最多显示行数，超出后改为内部滚动
 * @param shape 文本框形状
 * @param colors 文本框颜色配置
 */
@Composable
fun RpScrollableOutlinedTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    val scrollState = rememberScrollState()
    // 状态与滚动位置由调用方和文本框分别持有，指示器只负责展示和拖动纵向位置。
    OutlinedTextField(
        state = state,
        modifier = if (singleLine) modifier else modifier.draggableScrollIndicator(scrollState),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label?.let { labelContent -> { labelContent() } },
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        outputTransformation = outputTransformation,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(
                minHeightInLines = minLines,
                maxHeightInLines = maxLines.coerceAtLeast(minLines)
            )
        },
        scrollState = scrollState,
        shape = shape,
        colors = colors
    )
}

/**
 * 带统一侧边滚动指示器的可换行 Material 3 描边文本框。
 *
 * - 内部使用状态式文本输入 API，以便将文本框真实的 [ScrollState] 交给指示器。
 * - 用户输入仍通过 [onValueChange] 回传，外部值重置时保留合法的光标与选择位置。
 * - 达到 [maxLines] 前文本框正常增高，溢出后才显示并启用侧边指示器。
 *
 * @param value 外部持有的文本内容
 * @param onValueChange 用户修改文本时的回调
 * @param modifier 文本框布局修饰符
 * @param enabled 是否允许编辑
 * @param readOnly 是否只读
 * @param textStyle 文本样式
 * @param label 文本框标签
 * @param placeholder 空内容占位组件
 * @param leadingIcon 起始图标
 * @param trailingIcon 末尾图标
 * @param supportingText 辅助说明组件
 * @param isError 是否显示错误状态
 * @param outputTransformation 仅影响展示的文本转换
 * @param keyboardOptions 软键盘配置
 * @param onKeyboardAction 软键盘动作回调
 * @param singleLine 是否限制为单行；单行输入不启用纵向指示器
 * @param minLines 最少显示行数
 * @param maxLines 最多显示行数，超出后改为内部滚动
 * @param shape 文本框形状
 * @param colors 文本框颜色配置
 */
@Composable
fun RpScrollableOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    // 状态式 API 让多行输入暴露真实滚动位置，单行输入则保持原有横向滚动行为。
    val textFieldState = rememberBoundTextFieldState(value, onValueChange)
    RpScrollableOutlinedTextField(
        state = textFieldState,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        outputTransformation = outputTransformation,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = shape,
        colors = colors
    )
}
