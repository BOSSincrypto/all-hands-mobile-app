package dev.openhands.android

import dev.openhands.android.data.conversationFromCache
import dev.openhands.android.data.conversationToJson
import dev.openhands.android.data.deviceAuthFrom
import dev.openhands.android.data.devicePollFrom
import dev.openhands.android.data.gitRepoFrom
import dev.openhands.android.data.Conversation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OauthParseTest {
    @Test
    fun deviceAuthParsesRfc8628Fields() {
        val auth = deviceAuthFrom(
            JSONObject(
                """
                {
                  "device_code":"d1",
                  "user_code":"ABCD-1234",
                  "verification_uri":"https://app.all-hands.dev/device",
                  "verification_uri_complete":"https://app.all-hands.dev/device?user_code=ABCD-1234",
                  "expires_in":600,
                  "interval":5
                }
                """.trimIndent(),
            ),
        )
        assertEquals("d1", auth.deviceCode)
        assertEquals("ABCD-1234", auth.userCode)
        assertTrue(auth.verificationUriComplete.startsWith("https://"))
        assertEquals(5, auth.interval)
    }

    @Test
    fun devicePollPendingHasNoToken() {
        val poll = devicePollFrom(400, """{"error":"authorization_pending"}""")
        assertNull(poll.accessToken)
        assertEquals("authorization_pending", poll.error)
    }

    @Test
    fun devicePollReturnsApiKey() {
        val poll = devicePollFrom(200, """{"access_token":"ohc_secret","token_type":"Bearer"}""")
        assertEquals("ohc_secret", poll.accessToken)
        assertNull(poll.error)
    }

    @Test
    fun gitRepoParsesSearchItem() {
        val repo = gitRepoFrom(
            JSONObject(
                """{"id":"1","full_name":"acme/api","git_provider":"github","is_public":true,"main_branch":"main"}""",
            ),
        )
        assertEquals("acme/api", repo.fullName)
        assertEquals("github", repo.provider)
        assertEquals("main", repo.mainBranch)
    }

    @Test
    fun conversationCacheRoundTrip() {
        val conv = Conversation(
            id = "c1",
            title = "Fix tests",
            repository = "acme/api",
            branch = "main",
            llmModel = null,
            sandboxId = "s1",
            sandboxStatus = "RUNNING",
            executionStatus = "running",
            createdAt = null,
            updatedAt = "2026-09-11T00:00:00Z",
        )
        val back = conversationFromCache(conversationToJson(conv))
        assertEquals("c1", back.id)
        assertEquals("Fix tests", back.title)
        assertEquals("acme/api", back.repository)
        assertEquals("running", back.executionStatus)
    }
}
