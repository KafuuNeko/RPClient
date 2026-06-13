package me.kafuuneko.rpclient.feature.requestlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogItem
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogDialogState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiIntent
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle

/** LLM 请求日志列表与清理确认对话框的 Compose 入口。 */
@Composable
fun RequestLogLayout(
    uiState: RequestLogUiState,
    emit: RequestLogUiIntent.() -> Unit = {}
) {
    BackHandler { RequestLogUiIntent.Back.emit() }
    when (uiState) {
        RequestLogUiState.None, RequestLogUiState.Finished -> Unit
        is RequestLogUiState.Normal -> {
            NormalView(uiState.logs, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun NormalView(
    logs: List<RequestLogItem>,
    emit: RequestLogUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.request_logs),
                onBack = { RequestLogUiIntent.Back.emit() },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { emit(RequestLogUiIntent.ShowClearConfirmDialog) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.clear_logs),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.request_logs),
                    subtitle = stringResource(R.string.request_logs_subtitle)
                )
            }
            if (logs.isEmpty()) {
                item {
                    RpInfoCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.DataObject,
                        title = stringResource(R.string.no_request_logs),
                        subtitle = stringResource(R.string.no_request_logs_desc)
                    )
                }
            }
            items(logs) { log ->
                RequestLogCard(log = log, emit = emit)
            }
        }
    }
}

@Composable
private fun RequestLogCard(
    log: RequestLogItem,
    emit: RequestLogUiIntent.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = log.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = log.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    )
                }

                val rotationAngle by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "chevron_rotation"
                )

                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.hide) else stringResource(R.string.show),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f)
                )
            }

            if (expanded) {
                val requestJsonTitle = stringResource(R.string.request_json)
                val responseJsonTitle = stringResource(R.string.response_json)
                JsonBlock(
                    title = requestJsonTitle,
                    json = log.requestJson,
                    onCopy = { RequestLogUiIntent.CopyRequestJson(log.id).emit() },
                    onOpen = { RequestLogUiIntent.OpenRequestJson(log.id, requestJsonTitle).emit() }
                )
                JsonBlock(
                    title = responseJsonTitle,
                    json = log.responseJson,
                    onCopy = { RequestLogUiIntent.CopyResponseJson(log.id).emit() },
                    onOpen = { RequestLogUiIntent.OpenResponseJson(log.id, responseJsonTitle).emit() }
                )
            }
        }
    }
}

@Composable
private fun DialogSwitch(
    dialogState: RequestLogDialogState,
    emit: RequestLogUiIntent.() -> Unit
) {
    when (dialogState) {
        RequestLogDialogState.None -> Unit
        RequestLogDialogState.ClearConfirm -> AlertDialog(
            onDismissRequest = { emit(RequestLogUiIntent.DismissDialog) },
            title = { Text(stringResource(R.string.clear_logs)) },
            text = { Text(stringResource(R.string.clear_logs_confirm)) },
            confirmButton = {
                TextButton(onClick = { emit(RequestLogUiIntent.ConfirmClearLogs) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { emit(RequestLogUiIntent.DismissDialog) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun JsonBlock(
    title: String,
    json: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
            TextButton(onClick = onOpen) {
                Icon(Icons.Rounded.DataObject, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.view_json))
            }
            OutlinedButton(
                onClick = onCopy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.copy))
            }
        }
        Text(
            text = json,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
                .padding(10.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RequestLogLayoutPreview() {
    AppTheme(dynamicColor = false) {
        RequestLogLayout(
            uiState = RequestLogUiState.Normal(
                logs = listOf(
                    RequestLogItem(
                        id = 1L,
                        title = "ChatGPT / gpt-4o-mini",
                        subtitle = "05-12 01:20:00 · OpenAICompatible · once",
                        requestJson = """{"model":"gpt-4o-mini","messages":[]}""",
                        responseJson = """{"choices":[{"message":{"content":"Hello"}}]}"""
                    )
                )
            )
        )
    }
}
