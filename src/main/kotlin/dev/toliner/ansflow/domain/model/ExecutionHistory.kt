package dev.toliner.ansflow.domain.model

import java.time.Instant
import java.util.UUID

data class ExecutionHistory(
    val id: String,
    val environment: Environment,
    val playbookPath: String,
    val hostGroups: List<String>,
    val executedAt: Instant,
    val duration: Long,
    val status: ExecutionStatus,
    val outputPath: String,
    val checkMode: Boolean,
    val extraVars: Map<String, String>,
    val errorMessage: String? = null,
) {
    companion object {
        fun fromExecution(
            environment: Environment,
            playbookPath: String,
            hostGroups: List<String>,
            duration: Long,
            status: ExecutionStatus,
            outputPath: String,
            checkMode: Boolean,
            extraVars: Map<String, String>,
            errorMessage: String? = null,
        ): ExecutionHistory {
            return ExecutionHistory(
                id = UUID.randomUUID().toString(),
                environment = environment,
                playbookPath = playbookPath,
                hostGroups = hostGroups,
                executedAt = Instant.now(),
                duration = duration,
                status = status,
                outputPath = outputPath,
                checkMode = checkMode,
                extraVars = extraVars,
                errorMessage = errorMessage,
            )
        }
    }
}

enum class ExecutionStatus {
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class ExecutionSummary(
    val totalTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val skippedTasks: Int,
    val changedTasks: Int,
    val unreachableHosts: List<String>,
    val failedHosts: List<String>,
) {
    fun isSuccess(): Boolean {
        return failedTasks == 0 && unreachableHosts.isEmpty() && failedHosts.isEmpty()
    }
}
