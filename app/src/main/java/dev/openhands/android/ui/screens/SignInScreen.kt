package dev.openhands.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.openhands.android.UiState
import dev.openhands.android.ui.theme.Ink
import dev.openhands.android.ui.theme.Lime
import dev.openhands.android.ui.theme.Mute

@Composable
fun SignInScreen(
    state: UiState,
    onApiKey: (String) -> Unit,
    onBaseUrl: (String) -> Unit,
    onRepo: (String) -> Unit,
    onBranch: (String) -> Unit,
    onSignIn: () -> Unit,
    onOauth: () -> Unit,
    onCancelOauth: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Text("OPENHANDS", style = MaterialTheme.typography.labelSmall)
        Text("Give a task.\nLeave the phone.", style = MaterialTheme.typography.headlineLarge)
        Text(
            "The agent runs in OpenHands Cloud. Closing the app does not stop it. This client only starts work and watches status.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Field("Cloud URL", state.baseUrl, onBaseUrl)
        Button(
            onClick = onOauth,
            enabled = !state.oauthWaiting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
        ) {
            Text(if (state.oauthWaiting) "Waiting for browser…" else "Sign in with Cloud")
        }
        if (state.oauthWaiting) {
            Text(
                if (state.oauthUserCode.isNotBlank()) "Confirm code ${state.oauthUserCode} in the browser, then return here."
                else "Opening Cloud login…",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCancelOauth, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel login", color = Lime)
            }
        }
        Text("OR PASTE AN API KEY", style = MaterialTheme.typography.labelSmall)
        Field("API key", state.apiKey, onApiKey, secret = true)
        Field("Default repo  owner/name", state.defaultRepo, onRepo)
        Field("Default branch", state.defaultBranch, onBranch)
        Button(
            onClick = onSignIn,
            enabled = state.apiKey.isNotBlank() && !state.oauthWaiting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
        ) {
            Text("Connect with key")
        }
        Text(
            "Browser login uses Cloud OAuth device flow (same as the CLI). The returned API key is stored in EncryptedSharedPreferences and never backed up.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    secret: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Mute) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Lime,
            unfocusedBorderColor = Mute.copy(alpha = 0.4f),
            focusedLabelColor = Lime,
            cursorColor = Lime,
        ),
    )
}
