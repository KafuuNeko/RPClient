package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/** 三种创建页共用的角色搜索输入框。 */
@Composable
fun CharacterSearchField(
    query: String,
    enabled: Boolean = true,
    onQueryChange: (String) -> Unit
) {
    // 沿用页面底板与输入边框，壁纸主题下保持相同对比度。
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().generalContentBackground(RoundedCornerShape(16.dp)),
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        placeholder = {
            Text(
                text = stringResource(R.string.search_characters),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}
