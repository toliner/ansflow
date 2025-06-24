package dev.toliner.ansflow.domain.model

data class Host(
    val name: String,
    val variables: Map<String, String> = emptyMap(),
)
