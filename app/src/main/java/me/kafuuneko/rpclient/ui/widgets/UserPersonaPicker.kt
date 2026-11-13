package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.room.entity.UserPersona
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection

/**
 * 单聊、群聊、故事和默认身份共用的设置选择器。
 * @param state 页面持有的选项与选中 ID。
 * @param onSelect 用户选择已有身份后的回调。
 * @param modifier 选择器布局修饰符。
 * @param allowNone 是否显示故事专用的禁用选项。
 * @param enabled 是否允许展开和选择。
 * @param onCreate 提供时在菜单末尾显示创建入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPersonaPicker(
    state: PersonaSelection,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
    enabled: Boolean = true,
    onCreate: (() -> Unit)? = null,
) {
    var expanded by remember(enabled) { mutableStateOf(false) }
    val specialOption = when {
        allowNone -> stringResource(R.string.persona_none)
        state.followDefaultName != null -> stringResource(
            R.string.persona_follow_default,
            state.followDefaultName
        )

        else -> null
    }
    // 同名身份仍可独立绑定，只为重名项显示其数据库编号。
    val repeatedNames = remember(state.options) {
        state.options.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    }
    val shape = RoundedCornerShape(12.dp)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && enabled },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (state.selectedId == UserPersona.NONE) specialOption.orEmpty() else state.selectedName?.let { name ->
                if (name in repeatedNames) stringResource(
                    id = R.string.persona_name_with_id,
                    formatArgs = arrayOf<Any>(name, state.selectedId),
                ) else name
            }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            shape = shape,
            label = { Text(stringResource(R.string.persona_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled
                )
                .generalContentBackground(shape = shape, hasLabel = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // 先收起菜单，再由页面接收身份选择并持久化。
            val choose: (Long) -> Unit = { id ->
                expanded = false
                onSelect(id)
            }
            if (specialOption != null) {
                PersonaMenuItem(
                    name = specialOption,
                    isSelected = state.selectedId == UserPersona.NONE,
                    onClick = { choose(UserPersona.NONE) },
                )
            }
            state.options.forEach { persona ->
                val label = if (persona.name in repeatedNames) {
                    stringResource(R.string.persona_name_with_id, persona.name, persona.id)
                } else persona.name
                PersonaMenuItem(
                    name = label,
                    isSelected = state.selectedId == persona.id,
                    onClick = { choose(persona.id) },
                )
            }
            if (onCreate != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.persona_create)) },
                    onClick = {
                        expanded = false
                        onCreate()
                    },
                )
            }
        }
    }
}

@Composable
private fun PersonaMenuItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        modifier = Modifier.semantics { selected = isSelected },
        text = { Text(name) },
        trailingIcon = { if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null) },
        onClick = onClick,
    )
}
