package cc.star0.zerobuds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cc.star0.zerobuds.protocol.LogDirection
import cc.star0.zerobuds.protocol.LogEntry
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun LogPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    logEntries: List<LogEntry>,
    onSendHex: (String) -> Unit,
    onClearLog: () -> Unit
) {
    val listState = rememberLazyListState()

    // 自动滚动到底部
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding,
            state = listState
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        text = str("log_clear"),
                        onClick = onClearLog
                    )
                }
            }

            if (logEntries.isEmpty()) {
                item {
                    Text(
                        text = str("log_empty"),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            items(logEntries) { entry ->
                LogEntryItem(entry)
            }
        }

        // 输入区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            var hexInput by remember { mutableStateOf("") }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = str("log_send_hint"),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = hexInput,
                        onValueChange = { hexInput = it.uppercase() },
                        modifier = Modifier.weight(1f),
                        label = "Hex",
                        useLabelAsPlaceholder = true
                    )
                    TextButton(
                        text = str("log_send"),
                        onClick = {
                            if (hexInput.isNotBlank()) {
                                onSendHex(hexInput)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeStr = timeFormat.format(entry.timestamp)

    val directionLabel = when (entry.direction) {
        LogDirection.SEND -> "[Send]"
        LogDirection.RECV -> "[Recv]"
    }

    val directionColor = when (entry.direction) {
        LogDirection.SEND -> MiuixTheme.colorScheme.primary
        LogDirection.RECV -> MiuixTheme.colorScheme.secondary
    }

    // 格式化 hex: 每2字符加空格
    val formattedHex = entry.hexData.chunked(2).joinToString(" ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Text(
            text = "$timeStr ",
            style = MiuixTheme.textStyles.body2,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = "$directionLabel ",
            style = MiuixTheme.textStyles.body2,
            fontFamily = FontFamily.Monospace,
            color = directionColor
        )
        Text(
            text = formattedHex,
            style = MiuixTheme.textStyles.body2,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onBackground
        )
    }
}
