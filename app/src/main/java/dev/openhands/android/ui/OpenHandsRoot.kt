package dev.openhands.android.ui

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.openhands.android.AppViewModel
import dev.openhands.android.ui.screens.ConversationScreen
import dev.openhands.android.ui.screens.HomeScreen
import dev.openhands.android.ui.screens.SignInScreen
import dev.openhands.android.ui.screens.SettingsScreen
import dev.openhands.android.ui.theme.Ink
import dev.openhands.android.ui.theme.Lime
import dev.openhands.android.ui.theme.Mute

private fun canvasUrl(base: String) = "${base.trimEnd('/')}/canvas"
private fun conversationUrl(base: String, id: String) = "${base.trimEnd('/')}/conversations/$id"

@Composable
fun OpenHandsRoot(
    initialConversationId: String?,
    vm: AppViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val nav = rememberNavController()
    val context = LocalContext.current
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(initialConversationId, state.signedIn) {
        if (state.signedIn && !initialConversationId.isNullOrBlank()) {
            vm.openConversation(initialConversationId)
            nav.navigate("conversation") { launchSingleTop = true }
        }
    }

    fun openUrl(url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(Ink.toArgb())
                        .build(),
                )
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
        } catch (_: Exception) {
            val view = Intent(Intent.ACTION_VIEW, uri)
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(view)
        }
    }

    if (!state.signedIn) {
        SignInScreen(
            state = state,
            onApiKey = vm::onApiKey,
            onBaseUrl = vm::onBaseUrl,
            onRepo = vm::onRepo,
            onBranch = vm::onBranch,
            onSignIn = vm::saveAndSignIn,
            onOauth = { vm.startOauth { openUrl(it) } },
            onCancelOauth = vm::cancelOauth,
        )
        state.error?.let { ErrorDialog(it, vm::dismissError) }
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (route != "conversation") {
                NavigationBar(containerColor = Ink) {
                    listOf("home" to "Tasks", "canvas" to "Canvas", "settings" to "Settings").forEach { (id, label) ->
                        NavigationBarItem(
                            selected = route == id,
                            onClick = {
                                if (id == "canvas") openUrl(canvasUrl(state.baseUrl))
                                else nav.navigate(id) { launchSingleTop = true }
                            },
                            icon = { Text(if (id == "home") "◎" else if (id == "canvas") "◻" else "☰", color = if (route == id) Lime else Mute) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Lime,
                                selectedTextColor = Lime,
                                unselectedIconColor = Mute,
                                unselectedTextColor = Mute,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    state = state,
                    onPrompt = vm::onPrompt,
                    onStart = vm::startTask,
                    onRefresh = vm::refresh,
                    onOpen = {
                        vm.openConversation(it)
                        nav.navigate("conversation") { launchSingleTop = true }
                    },
                    onOpenWeb = { id -> openUrl(conversationUrl(state.baseUrl, id)) },
                    onRepoQuery = vm::onRepoQuery,
                    onPickRepo = vm::pickRepo,
                    onRepoManual = vm::onRepo,
                    onBranch = vm::onBranch,
                )
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    onApiKey = vm::onApiKey,
                    onBaseUrl = vm::onBaseUrl,
                    onRepo = vm::onRepo,
                    onBranch = vm::onBranch,
                    onSave = vm::saveSettings,
                    onSignOut = vm::signOut,
                    onOpenCanvas = { openUrl(canvasUrl(state.baseUrl)) },
                    onOpenCloud = { openUrl(state.baseUrl) },
                )
            }
            composable("conversation") {
                ConversationScreen(
                    state = state,
                    onBack = {
                        vm.closeConversation()
                        nav.popBackStack()
                    },
                    onReply = vm::onReply,
                    onSend = vm::sendReply,
                    onOpenWeb = { id -> openUrl(conversationUrl(state.baseUrl, id)) },
                )
            }
        }
    }

    state.error?.let { ErrorDialog(it, vm::dismissError) }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Lime) } },
        title = { Text("Something failed") },
        text = { Text(message) },
        containerColor = Ink,
    )
}
