package dev.openhands.android.data

data class Account(
    val email: String?,
    val gitLogin: String? = null,
)

data class DeviceAuth(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresIn: Int,
    val interval: Int,
)

data class DeviceTokenPoll(
    val accessToken: String? = null,
    val error: String? = null,
    val description: String? = null,
    val interval: Int? = null,
)

data class GitRepo(
    val id: String,
    val fullName: String,
    val provider: String,
    val mainBranch: String?,
    val isPublic: Boolean,
)

data class Conversation(
    val id: String,
    val title: String?,
    val repository: String?,
    val branch: String?,
    val llmModel: String?,
    val sandboxId: String?,
    val sandboxStatus: String?,
    val executionStatus: String?,
    val createdAt: String?,
    val updatedAt: String?,
)

data class StartTask(
    val id: String,
    val status: String?,
    val detail: String?,
    val conversationId: String?,
    val sandboxId: String?,
)

data class EventItem(
    val id: String,
    val kind: String,
    val source: String?,
    val timestamp: String?,
    val text: String,
    val toolName: String?,
)

data class TrackedJob(
    val startTaskId: String?,
    val conversationId: String?,
    val title: String?,
    val lastNotified: String? = null,
)
