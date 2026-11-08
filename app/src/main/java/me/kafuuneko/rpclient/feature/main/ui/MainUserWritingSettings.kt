package me.kafuuneko.rpclient.feature.main.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.libs.chat.ImpersonationOutput
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile

/** 全局扮演用户输出位置，既有用户默认继续写入聊天记录。 */
@Composable
internal fun MainUserWritingSettings(
    output: ImpersonationOutput,
    emit: MainUiIntent.() -> Unit,
) {
    RpSettingsGroup {
        Text(
            text = stringResource(R.string.user_writing_output_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        // 单选项使用稳定枚举，通过意图保存偏好，Compose 不直接访问 AppModel。
        ImpersonationOutput.entries.forEach { choice ->
            val isDraft = choice == ImpersonationOutput.Draft
            RpSettingsTile(
                title = stringResource(if (isDraft) R.string.user_writing_output_draft else R.string.user_writing_output_message),
                modifier = Modifier.semantics {
                    selected = output == choice; role = Role.RadioButton
                },
                onClick = { MainUiIntent.SelectImpersonationOutput(choice).emit() },
                trailing = { RadioButton(selected = output == choice, onClick = null) },
            )
        }
    }
}

@Preview(widthDp = 360, showBackground = true)
@Composable
private fun MainUserWritingSettingsPreview() {
    AppTheme(dynamicColor = false) {
        MainUserWritingSettings(output = ImpersonationOutput.Draft, emit = {})
    }
}
