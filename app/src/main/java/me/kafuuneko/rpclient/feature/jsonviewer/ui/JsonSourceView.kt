package me.kafuuneko.rpclient.feature.jsonviewer.ui

import androidx.annotation.StringRes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonSourceBlock
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonSourceState
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.utils.JsonSyntaxTokenType
import me.kafuuneko.rpclient.utils.rememberDefaultJsonSyntaxColors

/** 后台生成完成后，按有界块懒加载整份文档源码。 */
@Composable
internal fun JsonSourceView(state: JsonSourceState, scrollState: LazyListState) {
    if (state == JsonSourceState.Loading || state == JsonSourceState.NotLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state !is JsonSourceState.Ready) {
        Box(
            Modifier.fillMaxSize().navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            SourceNotice(if (state == JsonSourceState.TooLarge) R.string.json_preview_too_large else R.string.json_invalid_desc)
        }
        return
    }
    // 整份源码共用一个外观底板，块之间不增加卡片边界或段间距。
    AppSurface(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        RpLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = PaddingValues(14.dp),
        ) {
            if (state.isRaw) item { SourceNotice(R.string.json_source_invalid) }
            items(items = state.blocks) { block -> SourceBlock(block) }
        }
    }
}

/** 源码高亮使用全局词法分析结果，字符串跨块时颜色保持一致。 */
@Composable
private fun SourceBlock(block: JsonSourceBlock) {
    val colors = rememberDefaultJsonSyntaxColors()
    val text = remember(block, colors) {
        buildAnnotatedString {
            append(block.text)
            // Token 的块内偏移已由后台处理，渲染只创建当前小块的 span。
            block.tokens.forEach { token ->
                val color = when (token.type) {
                    JsonSyntaxTokenType.Key -> colors.key
                    JsonSyntaxTokenType.String -> colors.string
                    JsonSyntaxTokenType.Number -> colors.number
                    JsonSyntaxTokenType.Literal -> colors.literal
                    JsonSyntaxTokenType.Punctuation -> colors.punctuation
                }
                addStyle(
                    style = SpanStyle(
                        color = color,
                        fontWeight = if (token.type == JsonSyntaxTokenType.Key) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    start = token.start,
                    end = token.end,
                )
            }
        }
    }
    // 每块独立选择，避免懒列表未组合的文本被“全选”遗漏，或为选择强行布局整份文档。
    SelectionContainer {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

/** 超限或非法内容显示持久提示，不向界面暴露异常原文。 */
@Composable
private fun SourceNotice(@StringRes message: Int) {
    RpInfoCard(
        modifier = Modifier.fillMaxWidth(),
        icon = Icons.Rounded.DataObject,
        title = stringResource(R.string.json_viewer),
        subtitle = stringResource(message),
    )
}
