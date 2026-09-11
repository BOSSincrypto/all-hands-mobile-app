package dev.openhands.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.openhands.android.UiState
import dev.openhands.android.data.Conversation
import dev.openhands.android.data.GitRepo
import dev.openhands.android.ui.theme.Ink
import dev.openhands.android.ui.theme.Lime
import dev.openhands.android.ui.theme.Mute
import dev.openhands.android.ui.theme.Panel

@Composable
fun HomeScreen(
    state: UiState,
    onPrompt: (String) -> Unit,
    onStart: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (Conversation) -> Unit,
    onOpenWeb: (String) -> Unit,
    onRepoQuery: (String) -> Unit,
    onPickRepo: (GitRepo) -> Unit,
    onRepoManual: (String) -> Unit,
    onBranch: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("TASKS", style = MaterialTheme.typography.labelSmall)
            Text(
                listOfNotNull(state.account?.email, state.account?.gitLogin).joinToString(" · ").ifBlank { "Connected" },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.offline) {
                Text("Offline · last known list", color = Lime, style = MaterialTheme.typography.labelSmall)
            }
        }
        item {
            Field(
                label = "New task for the cloud agent",
                value = state.prompt,
                onChange = onPrompt,
                singleLine = false,
                minLines = 5,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Repository  owner/name", state.defaultRepo, onRepoManual)
                Field("Branch", state.defaultBranch, onBranch)
                Field("Search GitHub repos", state.repoQuery, onRepoQuery)
                if (state.reposLoading) {
                    Text("Loading repos…", style = MaterialTheme.typography.bodyMedium, color = Mute)
                }
            }
        }
        items(state.repos.take(12), key = { "repo-${it.id}-${it.fullName}" }) { repo ->
            Text(
                repo.fullName + if (repo.fullName == state.defaultRepo) "  · selected" else "",
                color = if (repo.fullName == state.defaultRepo) Lime else Mute,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .clickable { onPickRepo(repo) }
                    .padding(12.dp),
            )
        }
        item {
            Button(
                onClick = onStart,
                enabled = !state.sending && state.prompt.isNotBlank() && !state.offline,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
            ) {
                Text(if (state.sending) "Starting…" else "Run in cloud")
            }
            state.startHint?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("RECENT", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onRefresh) { Text("Refresh", color = Lime) }
            }
        }
        items(state.conversations, key = { it.id }) { conv ->
            ConversationRow(conv, onOpen = { onOpen(conv) }, onOpenWeb = { onOpenWeb(conv.id) })
        }
        if (state.conversations.isEmpty() && !state.loading) {
            item { Text("No conversations yet.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ConversationRow(
    conv: Conversation,
    onOpen: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(conv.title ?: "Conversation ${conv.id.take(8)}", style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(conv.repository, conv.executionStatus, conv.sandboxStatus).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Open on web", color = Lime, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable(onClick = onOpenWeb).padding(top = 6.dp))
    }
}
