package dev.toliner.ansflow.domain.service

import dev.toliner.ansflow.domain.model.Environment
import dev.toliner.ansflow.domain.model.Inventory

interface InventoryParser {
    val format: Format

    fun parse(content: String, environment: Environment): Result<Inventory>

    fun parseFile(path: String, environment: Environment): Result<Inventory>

    enum class Format {
        INI,
        YAML
    }
}