package dev.openhands.android.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

class OpenHandsApi(
    private val store: SecureStore,
    client: OkHttpClient? = null,
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    fun me(): Account {
        val obj = get("/api/v1/users/me").asObject()
        val git = obj.str("git_user_name")
            ?: runCatching { get("/api/v1/users/git-info").asObject().str("login") }.getOrNull()
        return Account(email = obj.str("email"), gitLogin = git)
    }

    fun startDeviceAuth(): DeviceAuth {
        val obj = postUnauthJson("/oauth/device/authorize", JSONObject()).asObject()
        val auth = deviceAuthFrom(obj)
        if (auth.deviceCode.isBlank() || auth.verificationUriComplete.isBlank()) {
            throw ApiException(500, "Cloud did not return a device code")
        }
        return auth
    }

    fun pollDeviceToken(deviceCode: String): DeviceTokenPoll {
        val form = "device_code=${URLEncoder.encode(deviceCode, "UTF-8")}"
        val (code, raw) = executeRaw(
            Request.Builder()
                .url(url("/oauth/device/token"))
                .post(form.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build(),
        )
        return devicePollFrom(code, raw)
    }

    fun searchRepositories(provider: String = "github", query: String? = null, limit: Int = 40): List<GitRepo> {
        val q = buildString {
            append("/api/v1/git/repositories/search?provider=")
            append(URLEncoder.encode(provider, "UTF-8"))
            append("&limit=").append(limit)
            if (!query.isNullOrBlank()) {
                append("&query=").append(URLEncoder.encode(query, "UTF-8"))
            }
        }
        val obj = get(q).asObject()
        return (obj.arr("items") ?: JSONArray()).objects().map(::gitRepoFrom).filter { it.fullName.isNotBlank() }
    }

    fun searchConversations(limit: Int = 30): List<Conversation> {
        val obj = get("/api/v1/app-conversations/search?limit=$limit").asObject()
        return (obj.arr("items") ?: JSONArray()).objects().map(::conversationFrom)
    }

    fun getConversations(ids: List<String>): List<Conversation> {
        if (ids.isEmpty()) return emptyList()
        val query = idsQuery(ids)
        return get("/api/v1/app-conversations?$query").asObjectList().map(::conversationFrom)
    }

    fun getStartTasks(ids: List<String>): List<StartTask> {
        if (ids.isEmpty()) return emptyList()
        val query = idsQuery(ids)
        return get("/api/v1/app-conversations/start-tasks?$query").asObjectList().map { obj ->
            StartTask(
                id = obj.str("id").orEmpty(),
                status = obj.str("status"),
                detail = obj.str("detail"),
                conversationId = obj.str("app_conversation_id"),
                sandboxId = obj.str("sandbox_id"),
            )
        }
    }

    fun startConversation(
        prompt: String,
        title: String? = null,
        repository: String? = null,
        branch: String? = null,
    ): StartTask {
        val body = JSONObject()
            .put(
                "initial_message",
                JSONObject()
                    .put("role", "user")
                    .put("run", true)
                    .put(
                        "content",
                        JSONArray().put(
                            JSONObject().put("type", "text").put("text", prompt),
                        ),
                    ),
            )
        title?.takeIf { it.isNotBlank() }?.let { body.put("title", it) }
        repository?.takeIf { it.isNotBlank() }?.let { body.put("selected_repository", it) }
        branch?.takeIf { it.isNotBlank() }?.let { body.put("selected_branch", it) }
        val obj = post("/api/v1/app-conversations", body).asObject()
        return StartTask(
            id = obj.str("id").orEmpty(),
            status = obj.str("status"),
            detail = obj.str("detail"),
            conversationId = obj.str("app_conversation_id"),
            sandboxId = obj.str("sandbox_id"),
        )
    }

    fun sendMessage(conversationId: String, text: String, run: Boolean = true) {
        val body = JSONObject()
            .put("role", "user")
            .put("run", run)
            .put(
                "content",
                JSONArray().put(JSONObject().put("type", "text").put("text", text)),
            )
        post("/api/v1/app-conversations/$conversationId/send-message", body)
    }

    fun events(conversationId: String, limit: Int = 80): List<EventItem> {
        val obj = get("/api/v1/conversation/$conversationId/events/search?limit=$limit").asObject()
        return (obj.arr("items") ?: JSONArray()).objects().map { event ->
            EventItem(
                id = event.str("id").orEmpty(),
                kind = event.str("kind").orEmpty(),
                source = event.str("source"),
                timestamp = event.str("timestamp"),
                text = eventText(event),
                toolName = event.str("tool_name"),
            )
        }
    }

    fun resumeSandbox(sandboxId: String) {
        post("/api/v1/sandboxes/$sandboxId/resume", JSONObject())
    }

    private fun idsQuery(ids: List<String>): String =
        ids.joinToString("&") { "ids=${URLEncoder.encode(it, "UTF-8")}" }

    private fun postUnauthJson(path: String, body: JSONObject): String = execute(
        Request.Builder()
            .url(url(path))
            .post(body.toString().toRequestBody(jsonType))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build(),
    )

    private fun conversationFrom(obj: JSONObject) = Conversation(
        id = obj.str("id").orEmpty(),
        title = obj.str("title"),
        repository = obj.str("selected_repository"),
        branch = obj.str("selected_branch"),
        llmModel = obj.str("llm_model"),
        sandboxId = obj.str("sandbox_id"),
        sandboxStatus = obj.str("sandbox_status"),
        executionStatus = obj.str("execution_status"),
        createdAt = obj.str("created_at"),
        updatedAt = obj.str("updated_at"),
    )

    private fun get(path: String) = execute(
        Request.Builder().url(url(path)).get().headers(authHeaders()).build(),
    )

    private fun post(path: String, body: JSONObject) = execute(
        Request.Builder()
            .url(url(path))
            .post(body.toString().toRequestBody(jsonType))
            .headers(authHeaders())
            .build(),
    )

    private fun url(path: String): String {
        val base = store.baseUrl.trimEnd('/')
        return if (path.startsWith("http")) path else base + path
    }

    private fun authHeaders(): okhttp3.Headers {
        val key = store.apiKey
        if (key.isBlank()) throw ApiException(401, "API key is missing")
        return okhttp3.Headers.Builder()
            .add("Authorization", "Bearer $key")
            .add("X-Access-Token", key)
            .add("Accept", "application/json")
            .add("User-Agent", USER_AGENT)
            .build()
    }

    private fun execute(request: Request): String {
        val (code, raw) = executeRaw(request)
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(raw).opt("detail")?.toString() }.getOrNull()
            val hint = detail?.take(240) ?: raw.take(240)
            throw ApiException(code, hint.ifBlank { "HTTP $code" })
        }
        return raw
    }

    private fun executeRaw(request: Request): Pair<Int, String> {
        http.newCall(request).execute().use { response ->
            return response.code to response.body.string()
        }
    }

    private fun String.asObject(): JSONObject = JSONObject(this)

    private fun String.asObjectList(): List<JSONObject> {
        val trimmed = trimStart()
        if (trimmed.startsWith("[")) return JSONArray(this).objects()
        val obj = JSONObject(this)
        val items = obj.arr("items")
        if (items != null) return items.objects()
        return if (obj.has("id")) listOf(obj) else emptyList()
    }

    companion object {
        const val USER_AGENT = "OpenHands-Android/0.2.0"
    }
}
