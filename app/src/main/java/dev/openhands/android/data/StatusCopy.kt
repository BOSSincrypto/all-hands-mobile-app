package dev.openhands.android.data

fun isTerminalExecution(status: String?): Boolean =
    status.equals("finished", true) ||
        status.equals("error", true) ||
        status.equals("stuck", true) ||
        status.equals("deleting", true)

fun isDeadSandbox(status: String?): Boolean =
    status.equals("ERROR", true) || status.equals("MISSING", true)

fun statusLine(sandbox: String?, execution: String?): String {
    val sandboxStatus = sandbox.orEmpty()
    val executionStatus = execution.orEmpty()
    return when {
        sandboxStatus.equals("PAUSED", true) -> "Sandbox paused. Open the app to resume."
        sandboxStatus.equals("ERROR", true) || sandboxStatus.equals("MISSING", true) ->
            "Sandbox $sandboxStatus"
        executionStatus.equals("running", true) -> "Agent is working in the cloud"
        executionStatus.equals("waiting_for_confirmation", true) -> "Waiting for your confirmation"
        executionStatus.equals("finished", true) -> "Task finished"
        executionStatus.equals("error", true) -> "Agent reported an error"
        executionStatus.equals("stuck", true) -> "Agent is stuck"
        executionStatus.equals("paused", true) -> "Agent paused"
        executionStatus.equals("idle", true) -> "Idle"
        executionStatus.equals("deleting", true) -> "Deleting"
        else -> "Sandbox $sandboxStatus · $executionStatus"
    }
}
