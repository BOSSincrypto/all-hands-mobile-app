package dev.openhands.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openhands.android.data.Account
import dev.openhands.android.data.ApiException
import dev.openhands.android.data.Conversation
import dev.openhands.android.data.EventItem
import dev.openhands.android.data.GitRepo
import dev.openhands.android.data.OpenHandsApi
import dev.openhands.android.data.SecureStore
import dev.openhands.android.data.StartTask
import dev.openhands.android.data.TrackedJob
import dev.openhands.android.work.TaskWatchWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val apiKey: String = "",
    val baseUrl: String = SecureStore.DEFAULT_BASE,
    val defaultRepo: String = "",
    val defaultBranch: String = "main",
    val signedIn: Boolean = false,
    val account: Account? = null,
    val conversations: List<Conversation> = emptyList(),
    val events: List<EventItem> = emptyList(),
    val selected: Conversation? = null,
    val prompt: String = "",
    val reply: String = "",
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    val startHint: String? = null,
    val offline: Boolean = false,
    val oauthWaiting: Boolean = false,
    val oauthUserCode: String = "",
    val oauthUrl: String = "",
    val repos: List<GitRepo> = emptyList(),
    val repoQuery: String = "",
    val reposLoading: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureStore(application)
    private val api = OpenHandsApi(store)
    private val _state = MutableStateFlow(
        UiState(
            apiKey = store.apiKey,
            baseUrl = store.baseUrl,
            defaultRepo = store.defaultRepo,
            defaultBranch = store.defaultBranch,
            signedIn = store.apiKey.isNotBlank(),
        ),
    )
    val state: StateFlow<UiState> = _state
    private var pollJob: Job? = null
    private var oauthJob: Job? = null
    private var repoJob: Job? = null

    init {
        val cached = store.cachedConversations()
        val cachedRepos = store.cachedRepos()
        if (cached.isNotEmpty() || cachedRepos.isNotEmpty()) {
            _state.update {
                it.copy(
                    conversations = cached.ifEmpty { it.conversations },
                    repos = cachedRepos.ifEmpty { it.repos },
                    offline = cached.isNotEmpty() && store.apiKey.isNotBlank(),
                )
            }
        }
        if (store.apiKey.isNotBlank()) refresh()
        TaskWatchWorker.enqueue(application)
    }

    fun onApiKey(value: String) = _state.update { it.copy(apiKey = value, error = null) }
    fun onBaseUrl(value: String) = _state.update { it.copy(baseUrl = value) }
    fun onRepo(value: String) = _state.update { it.copy(defaultRepo = value) }
    fun onBranch(value: String) = _state.update { it.copy(defaultBranch = value) }
    fun onPrompt(value: String) = _state.update { it.copy(prompt = value) }
    fun onReply(value: String) = _state.update { it.copy(reply = value) }
    fun onRepoQuery(value: String) {
        _state.update { it.copy(repoQuery = value) }
        loadRepos(value)
    }
    fun dismissError() = _state.update { it.copy(error = null, startHint = null) }

    fun pickRepo(repo: GitRepo) {
        _state.update {
            it.copy(
                defaultRepo = repo.fullName,
                defaultBranch = repo.mainBranch?.ifBlank { it.defaultBranch } ?: it.defaultBranch,
            )
        }
        saveSettings()
    }

    fun cancelOauth() {
        oauthJob?.cancel()
        _state.update { it.copy(oauthWaiting = false, oauthUserCode = "", oauthUrl = "") }
    }

    fun startOauth(openUrl: (String) -> Unit) {
        store.baseUrl = _state.value.baseUrl
        oauthJob?.cancel()
        oauthJob = viewModelScope.launch {
            _state.update { it.copy(oauthWaiting = true, error = null, oauthUserCode = "", oauthUrl = "") }
            runCatching { withContext(Dispatchers.IO) { api.startDeviceAuth() } }
                .onFailure { e ->
                    _state.update { it.copy(oauthWaiting = false, error = human(e)) }
                    return@launch
                }
                .onSuccess { auth ->
                    if (!auth.verificationUriComplete.startsWith("https://")) {
                        _state.update { it.copy(oauthWaiting = false, error = "Cloud returned a non-HTTPS login URL") }
                        return@launch
                    }
                    _state.update {
                        it.copy(
                            oauthUserCode = auth.userCode,
                            oauthUrl = auth.verificationUriComplete,
                        )
                    }
                    openUrl(auth.verificationUriComplete)
                    try {
                        pollOauth(auth.deviceCode, auth.interval, auth.expiresIn)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.update { it.copy(oauthWaiting = false, error = human(e)) }
                    }
                }
        }
    }

    private suspend fun pollOauth(deviceCode: String, startInterval: Int, expiresIn: Int) {
        var interval = startInterval.coerceIn(1, 30)
        val deadline = System.currentTimeMillis() + expiresIn.coerceAtLeast(30) * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val poll = try {
                withContext(Dispatchers.IO) { api.pollDeviceToken(deviceCode) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            val token = poll.accessToken
            if (!token.isNullOrBlank()) {
                store.apiKey = token
                _state.update {
                    it.copy(
                        apiKey = token,
                        oauthWaiting = false,
                        oauthUserCode = "",
                        oauthUrl = "",
                    )
                }
                saveAndSignIn()
                return
            }
            when (poll.error) {
                "authorization_pending" -> Unit
                "slow_down" -> interval = (poll.interval ?: (interval * 2)).coerceAtMost(30)
                "expired_token" -> {
                    _state.update { it.copy(oauthWaiting = false, error = "Login code expired. Try again.") }
                    return
                }
                "access_denied" -> {
                    _state.update { it.copy(oauthWaiting = false, error = "Cloud login was denied") }
                    return
                }
                null -> Unit
                else -> {
                    _state.update {
                        it.copy(oauthWaiting = false, error = poll.description ?: poll.error)
                    }
                    return
                }
            }
        }
        _state.update { it.copy(oauthWaiting = false, error = "Login timed out. Try again.") }
    }

    fun saveAndSignIn() {
        store.apiKey = _state.value.apiKey
        store.baseUrl = _state.value.baseUrl
        store.defaultRepo = _state.value.defaultRepo
        store.defaultBranch = _state.value.defaultBranch
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.me() } }
                .onSuccess { account ->
                    _state.update { it.copy(signedIn = true, account = account, error = null, offline = false) }
                    refresh()
                    loadRepos()
                }
                .onFailure { e ->
                    _state.update { it.copy(signedIn = false, error = human(e)) }
                }
        }
    }

    fun signOut() {
        pollJob?.cancel()
        oauthJob?.cancel()
        repoJob?.cancel()
        store.clearSecrets()
        _state.update {
            UiState(
                baseUrl = store.baseUrl,
                defaultRepo = store.defaultRepo,
                defaultBranch = store.defaultBranch,
            )
        }
    }

    fun saveSettings() {
        store.baseUrl = _state.value.baseUrl
        store.defaultRepo = _state.value.defaultRepo
        store.defaultBranch = _state.value.defaultBranch
        if (_state.value.apiKey.isNotBlank()) store.apiKey = _state.value.apiKey
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val me = api.me()
                    val list = api.searchConversations()
                    me to list
                }
            }.onSuccess { (me, list) ->
                store.cacheConversations(list)
                _state.update {
                    it.copy(
                        loading = false,
                        account = me,
                        conversations = list,
                        signedIn = true,
                        offline = false,
                    )
                }
                if (_state.value.repos.isEmpty()) loadRepos(_state.value.repoQuery)
            }.onFailure { e ->
                val cached = store.cachedConversations()
                val unauthorized = e is ApiException && (e.code == 401 || e.code == 403)
                _state.update {
                    it.copy(
                        loading = false,
                        conversations = cached.ifEmpty { it.conversations },
                        offline = cached.isNotEmpty() && !unauthorized,
                        error = if (cached.isNotEmpty() && !unauthorized) null else human(e),
                        signedIn = !unauthorized,
                        startHint = if (cached.isNotEmpty() && !unauthorized) "Showing last known tasks. Cloud is unreachable." else it.startHint,
                    )
                }
            }
        }
    }

    fun loadRepos(query: String = _state.value.repoQuery) {
        if (store.apiKey.isBlank()) return
        repoJob?.cancel()
        repoJob = viewModelScope.launch {
            _state.update { it.copy(reposLoading = true) }
            delay(if (query.isBlank()) 0 else 280)
            runCatching {
                withContext(Dispatchers.IO) { api.searchRepositories(query = query.ifBlank { null }) }
            }.onSuccess { repos ->
                if (query.isBlank()) store.cacheRepos(repos)
                _state.update { it.copy(repos = repos, reposLoading = false) }
            }.onFailure {
                _state.update {
                    it.copy(
                        repos = store.cachedRepos().ifEmpty { it.repos },
                        reposLoading = false,
                    )
                }
            }
        }
    }

    fun startTask() {
        val prompt = _state.value.prompt.trim()
        if (prompt.isBlank()) {
            _state.update { it.copy(error = "Write a task first") }
            return
        }
        if (_state.value.offline) {
            _state.update { it.copy(error = "Cloud is unreachable. Wait for a network, then retry.") }
            return
        }
        saveSettings()
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null, startHint = "Starting cloud sandbox…") }
            runCatching {
                withContext(Dispatchers.IO) {
                    api.startConversation(
                        prompt = prompt,
                        repository = store.defaultRepo.ifBlank { null },
                        branch = store.defaultBranch.ifBlank { null },
                    )
                }
            }.onSuccess { task ->
                store.upsertJob(
                    TrackedJob(
                        startTaskId = task.id,
                        conversationId = task.conversationId,
                        title = prompt.lineSequence().first().take(80),
                    ),
                )
                TaskWatchWorker.kick(getApplication())
                _state.update {
                    it.copy(
                        sending = false,
                        prompt = "",
                        startHint = "Task is running in the cloud. You can leave the app.",
                    )
                }
                waitForStart(task)
            }.onFailure { e ->
                _state.update { it.copy(sending = false, startHint = null, error = human(e)) }
            }
        }
    }

    fun openConversation(conversation: Conversation) {
        _state.update { it.copy(selected = conversation, events = emptyList(), reply = "") }
        startPolling(conversation.id)
    }

    fun openConversation(id: String) {
        val existing = _state.value.conversations.firstOrNull { it.id == id }
        if (existing != null) openConversation(existing)
        else {
            viewModelScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { api.getConversations(listOf(id)).firstOrNull() }
                }.onSuccess { conv -> if (conv != null) openConversation(conv) }
            }
        }
    }

    fun closeConversation() {
        pollJob?.cancel()
        _state.update { it.copy(selected = null, events = emptyList()) }
    }

    fun sendReply() {
        val selected = _state.value.selected ?: return
        val text = _state.value.reply.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    if (selected.sandboxStatus.equals("PAUSED", true) && !selected.sandboxId.isNullOrBlank()) {
                        api.resumeSandbox(selected.sandboxId)
                    }
                    api.sendMessage(selected.id, text)
                }
            }.onSuccess {
                store.upsertJob(
                    TrackedJob(
                        startTaskId = null,
                        conversationId = selected.id,
                        title = selected.title,
                    ),
                )
                TaskWatchWorker.kick(getApplication())
                _state.update { it.copy(sending = false, reply = "") }
                loadEvents(selected.id)
            }.onFailure { e ->
                _state.update { it.copy(sending = false, error = human(e)) }
            }
        }
    }

    private fun waitForStart(task: StartTask) {
        viewModelScope.launch {
            try {
                var current = task
                var tries = 0
                while (
                    current.conversationId.isNullOrBlank() &&
                    !current.status.equals("ERROR", true) &&
                    tries < 40
                ) {
                    delay(2_000)
                    current = withContext(Dispatchers.IO) {
                        api.getStartTasks(listOf(current.id)).firstOrNull() ?: current
                    }
                    val previous = store.trackedJobs().firstOrNull { it.startTaskId == current.id }
                    store.upsertJob(
                        TrackedJob(
                            startTaskId = current.id,
                            conversationId = current.conversationId,
                            title = previous?.title,
                            lastNotified = previous?.lastNotified,
                        ),
                    )
                    _state.update { it.copy(startHint = current.status ?: it.startHint) }
                    tries++
                }
                if (current.status.equals("ERROR", true)) {
                    _state.update {
                        it.copy(startHint = null, error = current.detail ?: "Cloud could not start the sandbox")
                    }
                    return@launch
                }
                refresh()
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        startHint = "Started in the cloud. Status will arrive via notification.",
                        error = null,
                    )
                }
                refresh()
            }
        }
    }

    private fun startPolling(conversationId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                loadEvents(conversationId)
                delay(4_000)
            }
        }
    }

    private suspend fun loadEvents(conversationId: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                val conv = api.getConversations(listOf(conversationId)).firstOrNull()
                val events = api.events(conversationId)
                conv to events
            }
        }.onSuccess { (conv, events) ->
            _state.update {
                it.copy(
                    selected = conv ?: it.selected,
                    events = events.filter { event -> event.kind != "ConversationStateUpdateEvent" },
                )
            }
        }
    }

    private fun human(error: Throwable): String = when (error) {
        is ApiException -> when (error.code) {
            401, 403 -> "API key rejected"
            409 -> "Sandbox is not running. Resume it from the conversation."
            410 -> "This conversation was archived"
            429 -> "Rate limited. Wait and retry."
            else -> error.message ?: "HTTP ${error.code}"
        }
        else -> error.message ?: "Network error"
    }
}
