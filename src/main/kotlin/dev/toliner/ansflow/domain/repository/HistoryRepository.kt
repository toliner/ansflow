package dev.toliner.ansflow.domain.repository

import dev.toliner.ansflow.domain.model.Environment
import dev.toliner.ansflow.domain.model.ExecutionHistory
import dev.toliner.ansflow.domain.model.ExecutionStatus

interface HistoryRepository {
    suspend fun save(history: ExecutionHistory): Result<Unit>

    suspend fun findById(id: String): Result<ExecutionHistory?>

    suspend fun findByEnvironment(
        environment: Environment,
        limit: Int = 50,
    ): Result<List<ExecutionHistory>>

    suspend fun findByPlaybook(
        playbookPath: String,
        limit: Int = 50,
    ): Result<List<ExecutionHistory>>

    suspend fun findByStatus(
        status: ExecutionStatus,
        limit: Int = 50,
    ): Result<List<ExecutionHistory>>

    suspend fun findRecent(limit: Int = 50): Result<List<ExecutionHistory>>

    suspend fun deleteOlderThan(days: Int): Result<Int> // Returns count deleted
}
