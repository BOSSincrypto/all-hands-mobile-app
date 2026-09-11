package dev.openhands.android.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.openhands.android.MainActivity
import dev.openhands.android.R

class TaskNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.channel_tasks),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_tasks_desc)
            },
        )
    }

    fun update(
        id: String,
        title: String,
        body: String,
        ongoing: Boolean,
        conversationId: String? = id,
    ) {
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!conversationId.isNullOrBlank()) {
            intent.putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
        }
        val pending = PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title.ifBlank { "OpenHands" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(ongoing)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    companion object {
        const val CHANNEL = "agent_tasks"
    }
}
