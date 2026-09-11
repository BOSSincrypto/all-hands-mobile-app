@file:Suppress("DEPRECATION")

package dev.openhands.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class SecureStore(context: Context) {
    private val appContext = context.applicationContext

    private val masterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        try {
            encryptedPrefs()
        } catch (_: Exception) {
            appContext.deleteSharedPreferences("openhands_secure")
            encryptedPrefs()
        }
    }

    private fun encryptedPrefs(): SharedPreferences =
        EncryptedSharedPreferences.create(
            appContext,
            "openhands_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_API, value.trim()).apply()
        }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE, DEFAULT_BASE).orEmpty().ifBlank { DEFAULT_BASE }
        set(value) {
            val cleaned = value.trim().trimEnd('/')
            val https = if (cleaned.startsWith("https://")) cleaned else DEFAULT_BASE
            prefs.edit().putString(KEY_BASE, https).apply()
        }

    var defaultRepo: String
        get() = prefs.getString(KEY_REPO, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_REPO, value.trim()).apply()
        }

    var defaultBranch: String
        get() = prefs.getString(KEY_BRANCH, "main").orEmpty().ifBlank { "main" }
        set(value) {
            prefs.edit().putString(KEY_BRANCH, value.trim().ifBlank { "main" }).apply()
        }

    fun clearSecrets() {
        prefs.edit()
            .remove(KEY_API)
            .remove(KEY_JOBS)
            .remove(KEY_CACHE)
            .remove(KEY_REPOS)
            .apply()
    }

    fun cacheConversations(list: List<Conversation>) {
        val arr = JSONArray()
        list.take(40).forEach { arr.put(conversationToJson(it)) }
        prefs.edit().putString(KEY_CACHE, arr.toString()).apply()
    }

    fun cachedConversations(): List<Conversation> {
        val raw = prefs.getString(KEY_CACHE, "[]") ?: "[]"
        return JSONArray(raw).objects().map(::conversationFromCache).filter { it.id.isNotBlank() }
    }

    fun cacheRepos(list: List<GitRepo>) {
        val arr = JSONArray()
        list.take(80).forEach { repo ->
            arr.put(
                JSONObject()
                    .put("id", repo.id)
                    .put("full_name", repo.fullName)
                    .put("git_provider", repo.provider)
                    .put("main_branch", repo.mainBranch)
                    .put("is_public", repo.isPublic),
            )
        }
        prefs.edit().putString(KEY_REPOS, arr.toString()).apply()
    }

    fun cachedRepos(): List<GitRepo> {
        val raw = prefs.getString(KEY_REPOS, "[]") ?: "[]"
        return JSONArray(raw).objects().map(::gitRepoFrom).filter { it.fullName.isNotBlank() }
    }

    fun trackedJobs(): List<TrackedJob> {
        val raw = prefs.getString(KEY_JOBS, "[]") ?: "[]"
        return JSONArray(raw).objects().mapNotNull { obj ->
            val start = obj.str("startTaskId")
            val conv = obj.str("conversationId")
            if (start == null && conv == null) null
            else TrackedJob(
                startTaskId = start,
                conversationId = conv,
                title = obj.str("title"),
                lastNotified = obj.str("lastNotified"),
            )
        }
    }

    fun upsertJob(job: TrackedJob) {
        val jobs = trackedJobs().toMutableList()
        val idx = jobs.indexOfFirst {
            (job.conversationId != null && it.conversationId == job.conversationId) ||
                (job.startTaskId != null && it.startTaskId == job.startTaskId)
        }
        if (idx >= 0) jobs[idx] = jobs[idx].copy(
            startTaskId = job.startTaskId ?: jobs[idx].startTaskId,
            conversationId = job.conversationId ?: jobs[idx].conversationId,
            title = job.title ?: jobs[idx].title,
            lastNotified = job.lastNotified ?: jobs[idx].lastNotified,
        ) else {
            jobs.add(0, job)
        }
        writeJobs(jobs.take(40))
    }

    fun markNotified(conversationId: String?, status: String) {
        if (conversationId.isNullOrBlank()) return
        writeJobs(
            trackedJobs().map {
                if (it.conversationId == conversationId) it.copy(lastNotified = status) else it
            },
        )
    }

    fun dropJob(conversationId: String?, startTaskId: String?) {
        writeJobs(
            trackedJobs().filterNot {
                (conversationId != null && it.conversationId == conversationId) ||
                    (startTaskId != null && it.startTaskId == startTaskId)
            },
        )
    }

    private fun writeJobs(jobs: List<TrackedJob>) {
        val arr = JSONArray()
        jobs.forEach { job ->
            arr.put(
                JSONObject()
                    .put("startTaskId", job.startTaskId)
                    .put("conversationId", job.conversationId)
                    .put("title", job.title)
                    .put("lastNotified", job.lastNotified),
            )
        }
        prefs.edit().putString(KEY_JOBS, arr.toString()).apply()
    }

    companion object {
        const val DEFAULT_BASE = "https://app.all-hands.dev"
        private const val KEY_API = "api_key"
        private const val KEY_BASE = "base_url"
        private const val KEY_REPO = "default_repo"
        private const val KEY_BRANCH = "default_branch"
        private const val KEY_JOBS = "tracked_jobs"
        private const val KEY_CACHE = "conversation_cache"
        private const val KEY_REPOS = "repo_cache"
    }
}
