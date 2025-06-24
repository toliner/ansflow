package dev.toliner.ansflow.domain.repository

import dev.toliner.ansflow.domain.model.ExecutionSummary
import kotlinx.coroutines.flow.Flow

interface AnsibleExecutor {
    data class ExecutionConfig(
        val playbookPath: String,
        val inventoryPath: String,
        val hostPattern: String,
        val checkMode: Boolean = false,
        val extraVars: Map<String, String> = emptyMap(),
        val additionalOptions: List<String> = emptyList(),
    )

    data class ExecutionOutput(
        val line: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isError: Boolean = false,
    )

    suspend fun execute(config: ExecutionConfig): Flow<ExecutionOutput>

    suspend fun executeAndWait(config: ExecutionConfig): Result<ExecutionSummary>
}
