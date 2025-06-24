package dev.toliner.ansflow.domain.repository

import dev.toliner.ansflow.domain.model.Environment
import dev.toliner.ansflow.domain.model.Inventory

interface InventoryRepository {
    suspend fun loadInventory(environment: Environment): Result<Inventory>

    suspend fun loadInventoryFromPath(path: String): Result<Inventory>
}
