package dev.toliner.ansflow.infrastructure.parser

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import dev.toliner.ansflow.domain.model.Playbook
import dev.toliner.ansflow.domain.model.Task
import java.io.File

class YamlPlaybookParser {
    private val yaml = Yaml.default

    fun parse(
        content: String,
        path: String = "inline",
    ): Result<Playbook> {
        return try {
            val yamlNode = yaml.parseToYamlNode(content)
            val playbook = parseYamlNode(yamlNode, path)
            Result.success(playbook)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseFile(path: String): Result<Playbook> {
        return try {
            val content = File(path).readText()
            parse(content, path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseYamlNode(
        node: YamlNode,
        path: String,
    ): Playbook {
        when (node) {
            is YamlList -> {
                // Ansible playbooks are lists of plays
                if (node.items.isEmpty()) {
                    throw IllegalArgumentException("Empty playbook")
                }

                // Parse the first play
                val firstPlay = node.items.first()
                if (firstPlay !is YamlMap) {
                    throw IllegalArgumentException("Invalid playbook structure: play must be a map")
                }

                return parsePlay(firstPlay, path)
            }
            is YamlMap -> {
                // Some playbooks might be a single play (not in a list)
                return parsePlay(node, path)
            }
            else -> {
                throw IllegalArgumentException("Not a valid playbook: must be a list or map")
            }
        }
    }

    private fun parsePlay(
        playNode: YamlMap,
        path: String,
    ): Playbook {
        val fields = mutableMapOf<String, YamlNode>()
        playNode.entries.forEach { (key, value) ->
            if (key is YamlScalar) {
                fields[key.content] = value
            }
        }

        // Extract name (required)
        val name =
            extractString(fields["name"])
                ?: throw IllegalArgumentException("Play must have a 'name' field")

        // Extract hosts (required)
        val hostsValue =
            fields["hosts"]
                ?: throw IllegalArgumentException("Play must have a 'hosts' field")
        val hosts = parseHosts(hostsValue)

        // Extract tasks
        val tasks = extractTasks(fields["tasks"])

        return Playbook(
            path = path,
            name = name,
            hosts = hosts,
            tasks = tasks,
        )
    }

    private fun parseHosts(node: YamlNode?): String {
        if (node == null) return ""

        return when (node) {
            is YamlScalar -> node.content
            is YamlList -> {
                // If hosts is a list, join them with colons
                node.items.mapNotNull { item ->
                    (item as? YamlScalar)?.content
                }.joinToString(":")
            }
            else -> ""
        }
    }

    private fun extractTasks(node: YamlNode?): List<Task> {
        if (node == null) return emptyList()

        return when (node) {
            is YamlList -> {
                node.items.mapNotNull { taskNode ->
                    if (taskNode is YamlMap) {
                        parseTask(taskNode)
                    } else {
                        null
                    }
                }
            }
            else -> emptyList()
        }
    }

    private fun parseTask(taskNode: YamlMap): Task? {
        val fields = mutableMapOf<String, YamlNode>()
        taskNode.entries.forEach { (key, value) ->
            if (key is YamlScalar) {
                fields[key.content] = value
            }
        }

        val name = extractString(fields["name"]) ?: return null

        // Find the module (first key that's not a known task keyword)
        val knownKeywords =
            setOf(
                "name", "when", "with_items", "loop", "tags",
                "register", "ignore_errors", "become", "notify",
                "delegate_to", "run_once", "vars", "environment",
            )

        val moduleEntry =
            fields.entries.find { (key, value) ->
                key !in knownKeywords && value is YamlMap
            }

        if (moduleEntry == null) {
            // Try to find simple modules like debug with msg
            val debugMsg = fields["debug"]
            if (debugMsg is YamlMap) {
                val args = mutableMapOf<String, String>()
                debugMsg.entries.forEach { (k, v) ->
                    if (k is YamlScalar && v is YamlScalar) {
                        args[k.content] = v.content
                    }
                }
                return Task(name = name, module = "debug", args = args)
            }

            // Check for other modules including simple ones
            fields.entries.forEach { (key, value) ->
                if (key !in knownKeywords) {
                    when (value) {
                        is YamlMap -> {
                            val args = parseModuleArgs(value)
                            return Task(name = name, module = key, args = args)
                        }
                        is YamlScalar -> {
                            // Simple module with string arg (like command, shell, raw)
                            return Task(name = name, module = key, args = mapOf("cmd" to value.content))
                        }
                        else -> {}
                    }
                }
            }

            return null
        }

        val module = moduleEntry.key
        val argsNode = moduleEntry.value as YamlMap
        val args = parseModuleArgs(argsNode)

        return Task(name = name, module = module, args = args)
    }

    private fun parseModuleArgs(argsNode: YamlMap): Map<String, String> {
        val args = mutableMapOf<String, String>()
        argsNode.entries.forEach { (k, v) ->
            if (k is YamlScalar) {
                when (v) {
                    is YamlScalar -> args[k.content] = v.content
                    is YamlList -> {
                        // Handle list values by joining them
                        args[k.content] =
                            v.items.joinToString(",") { item ->
                                (item as? YamlScalar)?.content ?: item.toString()
                            }
                    }
                    is YamlMap -> {
                        // For complex values, convert to string representation
                        args[k.content] = v.toString()
                    }
                    else -> args[k.content] = v.toString()
                }
            }
        }
        return args
    }

    private fun extractString(node: YamlNode?): String? {
        return (node as? YamlScalar)?.content
    }
}
