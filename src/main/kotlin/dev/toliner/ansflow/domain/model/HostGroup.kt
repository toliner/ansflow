package dev.toliner.ansflow.domain.model

data class HostGroup(
    val name: String,
    val hosts: List<Host>,
    val children: List<HostGroup>,
    val parent: HostGroup?,
) {
    fun getPath(): String {
        return if (parent != null) {
            "${parent.getPath()}:$name"
        } else {
            name
        }
    }

    fun getAllHosts(): List<Host> {
        val allHosts = hosts.toMutableList()
        children.forEach { child ->
            allHosts.addAll(child.getAllHosts())
        }
        return allHosts
    }

    fun findChild(name: String): HostGroup? {
        return children.find { it.name == name }
    }
}
