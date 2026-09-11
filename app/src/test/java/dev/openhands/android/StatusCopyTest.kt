package dev.openhands.android

import dev.openhands.android.data.isDeadSandbox
import dev.openhands.android.data.isTerminalExecution
import dev.openhands.android.data.statusLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusCopyTest {
    @Test
    fun runningIsNotTerminal() {
        assertFalse(isTerminalExecution("running"))
        assertFalse(isDeadSandbox("RUNNING"))
        assertEquals("Agent is working in the cloud", statusLine("RUNNING", "running"))
    }

    @Test
    fun finishedIsTerminal() {
        assertTrue(isTerminalExecution("finished"))
        assertEquals("Task finished", statusLine("RUNNING", "finished"))
    }

    @Test
    fun missingSandboxIsDead() {
        assertTrue(isDeadSandbox("MISSING"))
        assertEquals("Sandbox MISSING", statusLine("MISSING", "idle"))
    }

    @Test
    fun deletingIsTerminal() {
        assertTrue(isTerminalExecution("deleting"))
        assertEquals("Deleting", statusLine("RUNNING", "deleting"))
    }
}
