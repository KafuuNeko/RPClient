package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.utils.rememberJsonSyntaxVisualTransformation

/**
 * 现代化等宽代码/JSON 编辑框。
 *
 * 禁用软折行，支持水平横向滑动与纵向滚动，内置语法高亮与轻量边框。
 */
@Composable
fun RpJsonCodeEditorField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    minHeight: Dp = 120.dp,
    maxHeight: Dp = 220.dp,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = rememberJsonSyntaxVisualTransformation()
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!label.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.extensions_json_size, value.length),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        val horizontalScrollState = rememberScrollState()
        val verticalScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val horizontalPadding = 24.dp
                val verticalPadding = 24.dp
                val minimumContentWidth = (maxWidth - horizontalPadding).coerceAtLeast(0.dp)
                val minimumContentHeight = (maxHeight - verticalPadding).coerceAtLeast(0.dp)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggableScrollIndicator(verticalScrollState)
                        .horizontalScroll(horizontalScrollState)
                        .verticalScroll(verticalScrollState)
                        .padding(12.dp)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        modifier = Modifier
                            .widthIn(min = minimumContentWidth)
                            .heightIn(min = minimumContentHeight),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        visualTransformation = visualTransformation,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
