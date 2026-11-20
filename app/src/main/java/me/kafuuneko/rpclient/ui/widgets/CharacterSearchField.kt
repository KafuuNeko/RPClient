package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/**
 * 角色列表与选择器共用的搜索输入框，支持一键清空当前检索词。
 *
 * @param query 当前搜索关键词
 * @param enabled 是否允许编辑与清空
 * @param onQueryChange 搜索关键词变化回调
 */
@Composable
fun CharacterSearchField(
    query: String,
    enabled: Boolean = true,
    onQueryChange: (String) -> Unit,
) {
    // 沿用页面底板与输入边框，弹窗内自动跳过页面壁纸绘制以保持清晰对比度
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .generalContentBackground(RoundedCornerShape(14.dp)),
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    enabled = enabled,
                    onClick = { onQueryChange("") },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = stringResource(R.string.search_characters),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
    )
}
