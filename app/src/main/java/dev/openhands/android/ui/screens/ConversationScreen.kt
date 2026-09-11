package dev.openhands.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.openhands.android.UiState
import dev.openhands.android.data.EventItem
import dev.openhands.android.ui.theme.Cream
import dev.openhands.android.ui.theme.Ink
import dev.openhands.android.ui.theme.Lime
import dev.openhands.android.ui.theme.Mute
import dev.openhands.android.ui.theme.Panel

@Composable
fun ConversationScreen(
    state: UiState,
    onBack: () -> Unit,
    onReply: (String) -> Unit,
    onSend: () -> Unit,
    onOpenWeb: (String) -> Unit,
) {
    val conv = state.selected
    val listState = rememberLazyListState()
    LaunchedEffect(state.events.size) {
        if (state.events.isNotEmpty()) listState.animateScrollToItem(state.events.lastIndex)
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back", color = Lime) }
            Column(Modifier.weight(1f)) {
                Text(conv?.title ?: "Conversation", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(conv?.executionStatus, conv?.sandboxStatus).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (conv != null) TextButton(onClick = { onOpenWeb(conv.id) }) { Text("Web", color = Lime) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.events, key = { it.id }) { EventBubble(it) }
        }
        Column(Modifier.background(Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Follow-up", state.reply, onReply, singleLine = false, minLines = 2)
            Button(
                onClick = onSend,
                enabled = !state.sending && state.reply.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
            ) { Text(if (state.sending) "Sending…" else "Send") }
        }
    }
}

@Composable
private fun EventBubble(event: EventItem) {
    val label = when (event.kind) {
        "MessageEvent" -> event.source ?: "message"
        "ActionEvent" -> event.toolName ?: "tool"
        "ObservationEvent" -> "result"
        else -> event.kind.removeSuffix("Event")
    }
    if (event.text.isBlank() && event.kind != "MessageEvent") return
    Column(Modifier.fillMaxWidth().background(Panel).padding(12.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Lime)
        Text(event.text.ifBlank { "…" }, color = Cream, style = MaterialTheme.typography.bodyLarge)
        event.timestamp?.let { Text(it, color = Mute, style = MaterialTheme.typography.bodyMedium) }
    }
}
