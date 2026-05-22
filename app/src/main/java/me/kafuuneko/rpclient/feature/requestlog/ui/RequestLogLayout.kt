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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogItem
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiIntent
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpInfoCard
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle

@Composable
fun RequestLogLayout(
    uiState: RequestLogUiState,
    emit: RequestLogUiIntent.() -> Unit = {}
) {
    BackHandler { RequestLogUiIntent.Back.emit() }
    when (uiState) {
        RequestLogUiState.None, RequestLogUiState.Finished -> Unit
        is RequestLogUiState.Normal -> NormalView(uiState.logs, emit)
    }
}

@Composable
private fun NormalView(
    logs: List<RequestLogItem>,
    emit: RequestLogUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.request_logs),
                onBack = { RequestLogUiIntent.Back.emit() }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                shape = RoundedCornerShape(8.dp),
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
                .clip(RoundedCornerShape(8.dp))
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
