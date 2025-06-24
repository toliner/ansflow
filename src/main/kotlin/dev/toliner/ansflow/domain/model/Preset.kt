package dev.toliner.ansflow.domain.model

import java.time.Instant
import java.util.UUID

data class Preset(
    val id: String,
    val name: String,
    val description: String,
    val environment: Environment,
    val playbookPath: String,
    val hostGroups: List<String>,
    val checkMode: Boolean,
    val extraVars: Map<String, String>,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
) {
    companion object {
        fun fromHistory(
            history: ExecutionHistory,
            name: String,
            description: String,
        ): Preset {
            return Preset(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                environment = history.environment,
                playbookPath = history.playbookPath,
                hostGroups = history.hostGroups,
                checkMode = history.checkMode,
                extraVars = history.extraVars,
                createdAt = Instant.now(),
                lastUsedAt = null,
            )
        }
    }
}
