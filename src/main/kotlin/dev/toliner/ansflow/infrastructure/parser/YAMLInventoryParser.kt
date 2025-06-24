package dev.toliner.ansflow.infrastructure.parser

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import dev.toliner.ansflow.domain.model.Environment
import dev.toliner.ansflow.domain.model.Host
import dev.toliner.ansflow.domain.model.HostGroup
import dev.toliner.ansflow.domain.model.Inventory
import dev.toliner.ansflow.domain.service.InventoryParser
import java.io.File

class YAMLInventoryParser: InventoryParser {

    override val format: InventoryParser.Format = InventoryParser.Format.YAML

    private val yaml = Yaml(configuration = YamlConfiguration(
        strictMode = false,
    ))

    override fun parse(
        content: String,
        environment: Environment,
    ): Result<Inventory> {
        return try {
            val yamlNode = yaml.parseToYamlNode(content)
            val inventory = parseYamlNode(yamlNode, environment)
            Result.success(inventory)
        } catch (e: Exception) {
            when (e) {
                is com.charleskorn.kaml.YamlException -> {
                    Result.failure(IllegalArgumentException("Failed to parse YAML: ${e.message}", e))
                }
                else -> Result.failure(e)
            }
        }
    }

    override fun parseFile(
        path: String,
        environment: Environment,
    ): Result<Inventory> {
        return try {
            val content = File(path).readText()
            parse(content, environment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseYamlNode(
        node: YamlNode,
        environment: Environment,
    ): Inventory {
        when (node) {
            is YamlMap -> {
                val context = ParsingContext()

                // Check if 'all' group exists explicitly
                val hasAllGroup = node.get<YamlNode>("all") != null

                if (hasAllGroup) {
                    // Parse the explicit 'all' group
                    val allNode = node.get<YamlNode>("all")
                    if (allNode is YamlMap) {
                        parseGroup("all", allNode, context)
                    }

                    // Parse other top-level groups
                    node.entries.forEach { (key, value) ->
                        val groupName = (key as? YamlScalar)?.content ?: return@forEach
                        if (groupName != "all" && value is YamlMap) {
                            parseGroup(groupName, value, context)
                            // Add as child of 'all' if not already
                            context.addChild("all", groupName)
                        }
                    }
                } else {
                    // No explicit 'all' group, create implicit one
                    context.groups["all"] = GroupData("all")

                    // All top-level groups become children of implicit 'all'
                    node.entries.forEach { (key, value) ->
                        val groupName = (key as? YamlScalar)?.content ?: return@forEach
                        if (value is YamlMap) {
                            parseGroup(groupName, value, context)
                            context.addChild("all", groupName)
                        }
                    }
                }

                // Build the inventory from parsed data
                return buildInventory(context, environment)
            }
            else -> {
                throw IllegalArgumentException("Invalid YAML inventory format: root must be a map")
            }
        }
    }

    private fun parseGroup(
        groupName: String,
        groupNode: YamlMap,
        context: ParsingContext,
    ) {
        val groupData = context.groups.getOrPut(groupName) { GroupData(groupName) }

        groupNode.entries.forEach { (key, value) ->
            when ((key as? YamlScalar)?.content) {
                "hosts" -> parseHosts(value, groupData)
                "vars" -> parseVars(value, groupData)
                "children" -> parseChildren(value, groupData, context, groupName)
                else -> {
                    // Ignore unknown keys
                }
            }
        }
    }

    private fun parseHosts(
        hostsNode: YamlNode?,
        groupData: GroupData,
    ) {
        when (hostsNode) {
            is YamlMap -> {
                hostsNode.entries.forEach { (hostKey, hostValue) ->
                    val hostname = (hostKey as? YamlScalar)?.content ?: return@forEach
                    val hostVars = mutableMapOf<String, String>()

                    // Parse host variables
                    if (hostValue is YamlMap) {
                        hostValue.entries.forEach { (varKey, varValue) ->
                            val varName = (varKey as? YamlScalar)?.content ?: return@forEach
                            val varVal = extractValue(varValue)
                            if (varVal != null) {
                                hostVars[varName] = varVal
                            }
                        }
                    }

                    groupData.hosts.add(HostData(hostname, hostVars))
                }
            }
            is YamlNull, null -> {
                // Empty hosts section
            }
            else -> {
                throw IllegalArgumentException("'hosts' must be a map, not ${hostsNode.javaClass.simpleName}")
            }
        }
    }

    private fun parseVars(
        varsNode: YamlNode?,
        groupData: GroupData,
    ) {
        when (varsNode) {
            is YamlMap -> {
                varsNode.entries.forEach { (varKey, varValue) ->
                    val varName = (varKey as? YamlScalar)?.content ?: return@forEach
                    val varVal = extractValue(varValue)
                    if (varVal != null) {
                        groupData.variables[varName] = varVal
                    }
                }
            }
            is YamlNull, null -> {
                // Empty vars section
            }
            else -> {
                throw IllegalArgumentException("'vars' must be a map")
            }
        }
    }

    private fun parseChildren(
        childrenNode: YamlNode?,
        parentData: GroupData,
        context: ParsingContext,
        parentName: String,
    ) {
        when (childrenNode) {
            is YamlMap -> {
                childrenNode.entries.forEach { (childKey, childValue) ->
                    val childName = (childKey as? YamlScalar)?.content ?: return@forEach

                    when (childValue) {
                        is YamlMap -> {
                            // Child is defined inline with hosts/vars/children
                            // Use a composite key for inline groups to keep them separate
                            val inlineGroupKey = "$parentName:$childName"
                            val childGroupData = context.groups.getOrPut(inlineGroupKey) { GroupData(childName) }

                            // Parse the inline group
                            childValue.entries.forEach { (key, value) ->
                                when ((key as? YamlScalar)?.content) {
                                    "hosts" -> parseHosts(value, childGroupData)
                                    "vars" -> parseVars(value, childGroupData)
                                    "children" -> parseChildren(value, childGroupData, context, inlineGroupKey)
                                    else -> {
                                        // Ignore unknown keys
                                    }
                                }
                            }

                            context.addInlineChild(parentName, inlineGroupKey)
                        }
                        is YamlNull -> {
                            // Child is just a reference to an existing group
                            context.addChild(parentName, childName)
                            // Ensure the referenced group exists
                            context.groups.getOrPut(childName) { GroupData(childName) }
                        }
                        else -> {
                            throw IllegalArgumentException("Child '$childName' must be a map or null")
                        }
                    }
                }
            }
            is YamlNull, null -> {
                // Empty children section
            }
            else -> {
                throw IllegalArgumentException("'children' must be a map")
            }
        }
    }

    private fun extractValue(node: YamlNode?): String? {
        return when (node) {
            is YamlScalar -> node.content
            is YamlNull, null -> null
            else -> node.toString()
        }
    }

    private fun buildInventory(
        context: ParsingContext,
        environment: Environment,
    ): Inventory {
        val builtGroups = mutableMapOf<String, HostGroup>()
        val processedGroups = mutableSetOf<String>()

        fun buildGroup(
            groupName: String,
            parentVars: Map<String, String> = emptyMap(),
        ): HostGroup {
            if (builtGroups.containsKey(groupName)) {
                return builtGroups[groupName]!!
            }

            val groupData = context.groups[groupName] ?: throw IllegalStateException("Group not found: $groupName")

            // Get both types of children
            val childNames = context.childRelations[groupName] ?: emptyList()
            val inlineChildKeys = context.inlineChildRelations[groupName] ?: emptyList()

            // Merge parent vars with group vars
            val mergedVars = parentVars + groupData.variables

            // Create hosts with merged variables
            val hosts =
                groupData.hosts.map { hostData ->
                    // Group vars don't override host vars
                    val hostVars = mergedVars + hostData.variables
                    Host(
                        name = hostData.name,
                        variables = hostVars,
                    )
                }

            // Build referenced children with merged variables
            val referencedChildren =
                childNames.map { childName ->
                    buildGroup(childName, mergedVars)
                }

            // Build inline children with merged variables
            val inlineChildren =
                inlineChildKeys.map { childKey ->
                    buildGroup(childKey, mergedVars)
                }

            val children = referencedChildren + inlineChildren

            val group =
                HostGroup(
                    name = groupData.name,
                    hosts = hosts,
                    children = children,
                    parent = null,
                )

            builtGroups[groupName] = group

            // Set parent references for all children
            children.forEachIndexed { index, child ->
                // Check if this is an inline child or referenced child
                val childKey =
                    if (index < referencedChildren.size) {
                        // This is a referenced child
                        child.name
                    } else {
                        // This is an inline child
                        inlineChildKeys[index - referencedChildren.size]
                    }
                builtGroups[childKey] = child.copy(parent = group)
            }

            return group
        }

        // Build all root groups (those without parents)
        val rootGroups =
            context.groups.keys.filter { groupName ->
                !context.childRelations.values.flatten().contains(groupName) &&
                    !context.inlineChildRelations.values.flatten().contains(groupName)
            }

        rootGroups.forEach { rootGroup ->
            buildGroup(rootGroup)
        }

        // Fix parent references after all groups are built
        val updates = mutableMapOf<String, HostGroup>()
        builtGroups.forEach { (groupKey, group) ->
            group.children.forEach { child ->
                // Find the actual key for this child
                val childKey = builtGroups.entries.find { it.value.name == child.name && it.value.hosts == child.hosts }?.key
                if (childKey != null) {
                    updates[childKey] = child.copy(parent = group)
                }
            }
        }
        builtGroups.putAll(updates)

        // Return only top-level groups
        val topLevelGroups = builtGroups.values.filter { it.parent == null }

        return Inventory(
            environment = environment,
            groups = topLevelGroups,
        )
    }

    private class ParsingContext {
        val groups = mutableMapOf<String, GroupData>()
        val childRelations = mutableMapOf<String, MutableList<String>>()
        val inlineChildRelations = mutableMapOf<String, MutableList<String>>()

        fun addChild(
            parent: String,
            child: String,
        ) {
            childRelations.getOrPut(parent) { mutableListOf() }.add(child)
        }

        fun addInlineChild(
            parent: String,
            childKey: String,
        ) {
            inlineChildRelations.getOrPut(parent) { mutableListOf() }.add(childKey)
        }
    }

    private data class GroupData(
        val name: String,
        val hosts: MutableList<HostData> = mutableListOf(),
        val variables: MutableMap<String, String> = mutableMapOf(),
    )

    private data class HostData(
        val name: String,
        val variables: Map<String, String>,
    )
}
