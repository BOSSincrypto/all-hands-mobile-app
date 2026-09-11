package dev.openhands.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.openhands.android.UiState
import dev.openhands.android.ui.theme.Danger
import dev.openhands.android.ui.theme.Ink
import dev.openhands.android.ui.theme.Lime

@Composable
fun SettingsScreen(
    state: UiState,
    onApiKey: (String) -> Unit,
    onBaseUrl: (String) -> Unit,
    onRepo: (String) -> Unit,
    onBranch: (String) -> Unit,
    onSave: () -> Unit,
    onSignOut: () -> Unit,
    onOpenCanvas: () -> Unit,
    onOpenCloud: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SETTINGS", style = MaterialTheme.typography.labelSmall)
        Text("Cloud stays on when you leave.", style = MaterialTheme.typography.titleLarge)
        Text(
            "API key is encrypted on-device. Cloud backups are disabled. Traffic is HTTPS only. The agent process lives on app.all-hands.dev, not on this phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.offline) {
            Text("Offline. Cached task list is shown until Cloud is reachable.", style = MaterialTheme.typography.bodyMedium)
        }
        Field("API key", state.apiKey, onApiKey, secret = true)
        Field("Cloud URL", state.baseUrl, onBaseUrl)
        Field("Default repo", state.defaultRepo, onRepo)
        Field("Default branch", state.defaultBranch, onBranch)
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
        ) { Text("Save") }
        TextButton(onClick = onOpenCanvas, modifier = Modifier.fillMaxWidth()) { Text("Open Agent Canvas", color = Lime) }
        TextButton(onClick = onOpenCloud, modifier = Modifier.fillMaxWidth()) { Text("Open Cloud dashboard", color = Lime) }
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out and wipe key", color = Danger) }
    }
}
