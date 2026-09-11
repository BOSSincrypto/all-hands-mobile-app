package dev.openhands.android

import android.app.Application
import dev.openhands.android.notify.TaskNotifier
import dev.openhands.android.work.TaskWatchWorker

class OpenHandsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskNotifier(this).ensureChannel()
        TaskWatchWorker.enqueue(this)
    }
}
