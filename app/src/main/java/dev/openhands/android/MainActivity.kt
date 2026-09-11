package dev.openhands.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.openhands.android.ui.OpenHandsRoot
import dev.openhands.android.ui.theme.OpenHandsTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    private val openConversation = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        openConversation.value = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        setContent {
            val openId by openConversation.collectAsState()
            OpenHandsTheme {
                OpenHandsRoot(initialConversationId = openId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openConversation.value = intent.getStringExtra(EXTRA_CONVERSATION_ID)
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
