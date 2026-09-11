package dev.openhands.android.data

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

fun JSONObject.obj(key: String): JSONObject? =
    if (isNull(key)) null else optJSONObject(key)

fun JSONObject.arr(key: String): JSONArray? =
    if (isNull(key)) null else optJSONArray(key)

fun JSONArray.objects(): List<JSONObject> = buildList {
    for (i in 0 until length()) optJSONObject(i)?.let(::add)
}

fun textBlocks(value: Any?): String {
    return when (value) {
        null, JSONObject.NULL -> ""
        is String -> value
        is JSONArray -> buildString {
            for (i in 0 until value.length()) {
                val part = textBlocks(value.opt(i))
                if (part.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(part)
                }
            }
        }
        is JSONObject -> {
            val type = value.optString("type")
            when {
                type == "text" || value.has("text") -> value.optString("text")
                value.has("content") -> textBlocks(value.opt("content"))
                else -> ""
            }
        }
        else -> value.toString()
    }.trim()
}

fun deviceAuthFrom(obj: JSONObject) = DeviceAuth(
    deviceCode = obj.str("device_code").orEmpty(),
    userCode = obj.str("user_code").orEmpty(),
    verificationUri = obj.str("verification_uri").orEmpty(),
    verificationUriComplete = obj.str("verification_uri_complete").orEmpty(),
    expiresIn = obj.optInt("expires_in", 600),
    interval = obj.optInt("interval", 5).coerceAtLeast(1),
)

fun devicePollFrom(httpCode: Int, raw: String): DeviceTokenPoll {
    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return DeviceTokenPoll(
        error = "server_error",
        description = "HTTP $httpCode",
    )
    if (httpCode in 200..299) {
        val token = obj.str("access_token")
        if (!token.isNullOrBlank()) return DeviceTokenPoll(accessToken = token)
        return DeviceTokenPoll(error = obj.str("error") ?: "server_error", description = obj.str("error_description"))
    }
    return DeviceTokenPoll(
        error = obj.str("error") ?: "server_error",
        description = obj.str("error_description"),
        interval = obj.optInt("interval").takeIf { obj.has("interval") },
    )
}

fun gitRepoFrom(obj: JSONObject) = GitRepo(
    id = obj.str("id").orEmpty(),
    fullName = obj.str("full_name").orEmpty(),
    provider = obj.str("git_provider").orEmpty(),
    mainBranch = obj.str("main_branch"),
    isPublic = obj.optBoolean("is_public", false),
)

fun conversationToJson(conv: Conversation) = JSONObject()
    .put("id", conv.id)
    .put("title", conv.title)
    .put("selected_repository", conv.repository)
    .put("selected_branch", conv.branch)
    .put("llm_model", conv.llmModel)
    .put("sandbox_id", conv.sandboxId)
    .put("sandbox_status", conv.sandboxStatus)
    .put("execution_status", conv.executionStatus)
    .put("created_at", conv.createdAt)
    .put("updated_at", conv.updatedAt)

fun conversationFromCache(obj: JSONObject) = Conversation(
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

fun eventText(event: JSONObject): String {
    val llm = event.obj("llm_message")
    val fromMessage = textBlocks(llm?.opt("content"))
    if (fromMessage.isNotBlank()) return fromMessage
    val thought = textBlocks(event.opt("thought"))
    if (thought.isNotBlank()) return thought
    val summary = event.str("summary")
    if (!summary.isNullOrBlank()) return summary
    val observation = event.obj("observation")
    val obs = textBlocks(observation?.opt("content") ?: observation)
    if (obs.isNotBlank()) return obs
    val action = event.obj("action")
    val actionText = textBlocks(action?.opt("command") ?: action?.opt("content"))
    if (actionText.isNotBlank()) return actionText
    return event.str("detail").orEmpty()
}
