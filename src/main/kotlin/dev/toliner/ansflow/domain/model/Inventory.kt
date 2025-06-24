package dev.toliner.ansflow.domain.model

data class Inventory(
    val environment: Environment,
    val groups: List<HostGroup>,
) {
    fun findGroup(name: String): HostGroup? {
        return groups.find { it.name == name }
    }

    fun merge(other: Inventory): Inventory {
        return Inventory(
            environment = environment,
            groups = groups + other.groups,
        )
    }
}
