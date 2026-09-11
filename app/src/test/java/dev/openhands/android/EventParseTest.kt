package dev.openhands.android

import dev.openhands.android.data.eventText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventParseTest {
    @Test
    fun messageEventUsesLlmContent() {
        val json = JSONObject(
            """
            {
              "kind": "MessageEvent",
              "source": "user",
              "llm_message": {
                "role": "user",
                "content": [{"type":"text","text":"Fix the flaky test"}]
              }
            }
            """.trimIndent(),
        )
        assertEquals("Fix the flaky test", eventText(json))
    }

    @Test
    fun actionEventFallsBackToSummary() {
        val json = JSONObject(
            """
            {"kind":"ActionEvent","tool_name":"terminal","summary":"Run tests","thought":[{"type":"text","text":"I'll run tests"}]}
            """.trimIndent(),
        )
        assertEquals("I'll run tests", eventText(json))
    }

    @Test
    fun observationUsesContentBlocks() {
        val json = JSONObject(
            """
            {"kind":"ObservationEvent","observation":{"content":[{"type":"text","text":"exit 0"}]}}
            """.trimIndent(),
        )
        assertEquals("exit 0", eventText(json))
    }

    @Test
    fun startTaskReadyHasConversationId() {
        val task = JSONObject("""{"id":"t1","status":"READY","app_conversation_id":"c9"}""")
        assertEquals("c9", task.optString("app_conversation_id"))
        assertTrue(task.optString("status") == "READY")
    }

    @Test
    fun emptyContentIsBlank() {
        val json = JSONObject("""{"kind":"ObservationEvent"}""")
        assertEquals("", eventText(json))
    }
}
