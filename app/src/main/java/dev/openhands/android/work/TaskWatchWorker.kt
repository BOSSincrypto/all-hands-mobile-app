package dev.openhands.android.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.openhands.android.data.ApiException
import dev.openhands.android.data.OpenHandsApi
import dev.openhands.android.data.SecureStore
import dev.openhands.android.data.isDeadSandbox
import dev.openhands.android.data.isTerminalExecution
import dev.openhands.android.data.statusLine
import dev.openhands.android.notify.TaskNotifier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Polls Cloud for tracked conversations. The agent itself runs in OpenHands Cloud;
 * this worker only reports status after the user leaves the app.
 */
class TaskWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SecureStore(applicationContext)
        if (store.apiKey.isBlank()) return Result.success()
        val jobs = store.trackedJobs()
        if (jobs.isEmpty()) return Result.success()

        val api = OpenHandsApi(store)
        val notifier = TaskNotifier(applicationContext)
        return try {
            withContext(Dispatchers.IO) {
                val startIds = jobs.mapNotNull { it.startTaskId }.filter { it.isNotBlank() }
                val starts = if (startIds.isEmpty()) emptyList() else api.getStartTasks(startIds)
                starts.forEach { task ->
                    val match = jobs.firstOrNull { it.startTaskId == task.id } ?: return@forEach
                    if (!task.conversationId.isNullOrBlank()) {
                        store.upsertJob(match.copy(conversationId = task.conversationId))
                    } else if (task.status.equals("ERROR", true)) {
                        notifier.update(
                            id = task.id,
                            title = match.title ?: "Start failed",
                            body = task.detail ?: "Cloud could not start the sandbox",
                            ongoing = false,
                            conversationId = null,
                        )
                        store.dropJob(null, task.id)
                    }
                }

                val conversationIds = store.trackedJobs().mapNotNull { it.conversationId }.distinct()
                if (conversationIds.isNotEmpty()) {
                    val conversations = api.getConversations(conversationIds)
                    conversations.forEach { conv ->
                        val status = conv.executionStatus.orEmpty()
                        val sandbox = conv.sandboxStatus.orEmpty()
                        val terminal = isTerminalExecution(status) || isDeadSandbox(sandbox)
                        val title = conv.title ?: "Conversation ${conv.id.take(6)}"
                        val body = statusLine(sandbox, status)
                        val previous = store.trackedJobs().firstOrNull { it.conversationId == conv.id }
                        if (previous?.lastNotified != "$sandbox:$status") {
                            notifier.update(conv.id, title, body, ongoing = !terminal)
                            store.markNotified(conv.id, "$sandbox:$status")
                        }
                        if (terminal) store.dropJob(conv.id, null)
                    }
                }
            }
            if (store.trackedJobs().isNotEmpty()) scheduleSoon(applicationContext)
            Result.success()
        } catch (e: ApiException) {
            if (e.code == 401 || e.code == 403) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE = "openhands-task-watch"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodic = PeriodicWorkRequestBuilder<TaskWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
        }

        fun kick(context: Context) {
            enqueueOneShot(context, "$UNIQUE-now", ExistingWorkPolicy.REPLACE, 0)
            enqueue(context)
        }

        fun scheduleSoon(context: Context) {
            enqueueOneShot(context, "$UNIQUE-soon", ExistingWorkPolicy.KEEP, 90)
        }

        private fun enqueueOneShot(
            context: Context,
            name: String,
            policy: ExistingWorkPolicy,
            delaySeconds: Long,
        ) {
            val request = OneTimeWorkRequestBuilder<TaskWatchWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
        }
    }
}
