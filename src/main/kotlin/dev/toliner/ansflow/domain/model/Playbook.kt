package dev.toliner.ansflow.domain.model

data class Playbook(
    val path: String,
    val name: String,
    val hosts: String,
    val tasks: List<Task>,
)

data class Task(
    val name: String,
    val module: String,
    val args: Map<String, String>,
)
