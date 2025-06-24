package dev.toliner.ansflow.domain.repository

import dev.toliner.ansflow.domain.model.Environment
import dev.toliner.ansflow.domain.model.HostGroup
import dev.toliner.ansflow.domain.model.Playbook

interface PlaybookRepository {
    suspend fun loadPlaybook(path: String): Result<Playbook>

    suspend fun listPlaybooks(environment: Environment): Result<List<Playbook>>

    suspend fun findCompatibleGroups(
        playbook: Playbook,
        groups: List<HostGroup>,
    ): List<HostGroup>
}
