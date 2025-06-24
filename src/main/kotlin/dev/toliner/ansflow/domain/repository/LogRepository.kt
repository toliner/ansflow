package dev.toliner.ansflow.domain.repository

interface LogRepository {
    suspend fun createLogFile(executionId: String): Result<String> // Returns path

    suspend fun appendToLog(
        path: String,
        content: String,
    ): Result<Unit>

    suspend fun readLog(path: String): Result<String>

    suspend fun deleteOldLogs(daysToKeep: Int = 30): Result<Int> // Returns count deleted

    suspend fun getLogSize(path: String): Result<Long> // Size in bytes

    suspend fun rotateLogIfNeeded(path: String, maxSizeBytes: Long = 10_485_760): Result<String?> // 10MB default
}
