package dev.toliner.ansflow.domain.repository

import dev.toliner.ansflow.domain.model.Environment
import dev.toliner.ansflow.domain.model.Preset

interface PresetRepository {
    suspend fun save(preset: Preset): Result<Unit>

    suspend fun update(preset: Preset): Result<Unit>

    suspend fun findById(id: String): Result<Preset?>

    suspend fun findByName(name: String): Result<Preset?>

    suspend fun findByEnvironment(environment: Environment): Result<List<Preset>>

    suspend fun findAll(): Result<List<Preset>>

    suspend fun delete(id: String): Result<Unit>

    suspend fun updateLastUsed(id: String): Result<Unit>
}
