package dev.toliner.ansflow.infrastructure.parser

import dev.toliner.ansflow.domain.model.Environment
import dev.toliner.ansflow.domain.model.Host
import dev.toliner.ansflow.domain.model.HostGroup
import dev.toliner.ansflow.domain.model.Inventory
import dev.toliner.ansflow.domain.service.InventoryParser
import java.io.File

class IniInventoryParser : InventoryParser {
    override val format: InventoryParser.Format = InventoryParser.Format.INI

    private val sectionRegex = Regex("""^\[([^\]]+)\]$""")
    private val hostLineRegex = Regex("""^(\S+)(?:\s+(.+))?$""")
    private val variableRegex = Regex("""(\w+)=([^\s]+)""")

    override fun parse(
        content: String,
        environment: Environment,
    ): Result<Inventory> {
        return try {
            val context = ParsingContext()
            content.lines().forEachIndexed { lineNum, line ->
                parseLine(line.trim(), lineNum + 1, context)
            }

            val inventory = buildInventory(context, environment)
            Result.success(inventory)
        } catch (e: Exception) {
            Result.failure(e)
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

    private fun parseLine(
        line: String,
        lineNum: Int,
        context: ParsingContext,
    ) {
        when {
            line.isEmpty() || line.startsWith("#") || line.startsWith(";") -> {
                // Skip empty lines and comments
            }
            line.startsWith("[") -> {
                parseSection(line, lineNum, context)
            }
            context.currentSection != null -> {
                parseContentLine(line, lineNum, context)
            }
            else -> {
                // Content outside of sections is an error in strict mode
                if (line.isNotBlank()) {
                    throw IllegalArgumentException("Line $lineNum: Content outside of section: $line")
                }
            }
        }
    }

    private fun parseSection(
        line: String,
        lineNum: Int,
        context: ParsingContext,
    ) {
        val match = sectionRegex.matchEntire(line)
        if (match == null) {
            context.warnings.add("Line $lineNum: Invalid section format: $line")
            context.currentSection = null
            return
        }

        val sectionName = match.groupValues[1]
        when {
            sectionName.endsWith(":vars") -> {
                val groupName = sectionName.removeSuffix(":vars")
                context.currentSection = Section(groupName, SectionType.VARS)
                context.groupVars.putIfAbsent(groupName, mutableMapOf())
            }
            sectionName.endsWith(":children") -> {
                val groupName = sectionName.removeSuffix(":children")
                context.currentSection = Section(groupName, SectionType.CHILDREN)
                context.childGroups.putIfAbsent(groupName, mutableListOf())
            }
            else -> {
                context.currentSection = Section(sectionName, SectionType.HOSTS)
                context.groups.putIfAbsent(sectionName, mutableListOf())
            }
        }
    }

    private fun parseContentLine(
        line: String,
        lineNum: Int,
        context: ParsingContext,
    ) {
        val section = context.currentSection ?: return

        when (section.type) {
            SectionType.HOSTS -> parseHostLine(line, section.name, context)
            SectionType.VARS -> parseVarLine(line, section.name, context)
            SectionType.CHILDREN -> parseChildLine(line, section.name, context)
        }
    }

    private fun parseHostLine(
        line: String,
        groupName: String,
        context: ParsingContext,
    ) {
        val match = hostLineRegex.matchEntire(line) ?: return
        val hostname = match.groupValues[1]
        val varsString = match.groupValues[2]

        val hostVars = mutableMapOf<String, String>()
        if (!varsString.isNullOrEmpty()) {
            // Validate variable syntax
            val parts = varsString.split(" ")
            for (part in parts) {
                if (part.contains("=")) {
                    val varMatch = variableRegex.matchEntire(part)
                    if (varMatch != null) {
                        val key = varMatch.groupValues[1]
                        val value = varMatch.groupValues[2]
                        hostVars[key] = value
                    } else {
                        // Invalid variable syntax
                        throw IllegalArgumentException("Invalid variable syntax: $part")
                    }
                } else if (part.isNotBlank()) {
                    // Non-variable content after hostname
                    throw IllegalArgumentException("Invalid content after hostname: $part")
                }
            }
        }

        val hostData = HostData(hostname, hostVars)
        context.groups[groupName]?.add(hostData)
    }

    private fun parseVarLine(
        line: String,
        groupName: String,
        context: ParsingContext,
    ) {
        val match = variableRegex.matchEntire(line)
        if (match != null) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            context.groupVars[groupName]?.put(key, value)
        }
    }

    private fun parseChildLine(
        line: String,
        groupName: String,
        context: ParsingContext,
    ) {
        if (line.isNotBlank()) {
            context.childGroups[groupName]?.add(line)
        }
    }

    private fun buildInventory(
        context: ParsingContext,
        environment: Environment,
    ): Inventory {
        // Check for circular references first
        context.childGroups.forEach { (parent, children) ->
            children.forEach { child ->
                if (hasCircularReference(parent, child, context)) {
                    throw IllegalArgumentException("Circular reference detected: $parent -> $child")
                }
            }
        }

        val allGroups = mutableMapOf<String, HostGroup>()

        // First, build all basic groups with basic host data (without group vars inheritance)
        context.groups.forEach { (groupName, hostsData) ->
            val hosts =
                hostsData.map { hostData ->
                    Host(hostData.name, hostData.variables)
                }

            allGroups[groupName] =
                HostGroup(
                    name = groupName,
                    hosts = hosts,
                    children = emptyList(),
                    parent = null,
                )
        }

        // Create empty groups for parents that only have children
        context.childGroups.forEach { (parentName, _) ->
            if (!allGroups.containsKey(parentName)) {
                allGroups[parentName] =
                    HostGroup(
                        name = parentName,
                        hosts = emptyList(),
                        children = emptyList(),
                        parent = null,
                    )
            }
        }

        // Build parent-child relationships
        val parentChildMap = mutableMapOf<String, MutableList<String>>()
        context.childGroups.forEach { (parent, children) ->
            parentChildMap[parent] = children.toMutableList()
        }

        // Update groups with parent-child relationships
        val finalGroups = mutableMapOf<String, HostGroup>()

        // Process all groups
        allGroups.forEach { (name, group) ->
            finalGroups[name] = group
        }

        // Set up parent-child relationships and apply variables in one pass
        val processedGroups = mutableSetOf<String>()

        fun processGroupHierarchy(
            groupName: String,
            parentGroup: HostGroup? = null,
        ): HostGroup {
            if (groupName in processedGroups) {
                return finalGroups[groupName]!!
            }

            val group = finalGroups[groupName] ?: allGroups[groupName]!!
            processedGroups.add(groupName)

            // Set parent reference first
            var updatedGroup =
                if (parentGroup != null) {
                    group.copy(parent = parentGroup)
                } else {
                    group
                }

            // Process children with this group as parent
            val childNames = parentChildMap[groupName] ?: emptyList()
            val processedChildren =
                childNames.map { childName ->
                    processGroupHierarchy(childName, updatedGroup)
                }

            // Update group with processed children
            updatedGroup = updatedGroup.copy(children = processedChildren)

            // Apply variables to this group's hosts (now with parent set)
            if (updatedGroup.hosts.isNotEmpty()) {
                val updatedHosts = applyGroupVarsToHosts(updatedGroup, context.groupVars)
                updatedGroup = updatedGroup.copy(hosts = updatedHosts)
            }

            finalGroups[groupName] = updatedGroup
            return updatedGroup
        }

        // Process all top-level groups (those without parents)
        allGroups.keys.forEach { groupName ->
            if (!isChildOfAnyGroup(groupName, context)) {
                processGroupHierarchy(groupName)
            }
        }

        // Return only top-level groups (those without parents)
        return Inventory(
            environment = environment,
            groups = finalGroups.values.filter { it.parent == null },
        )
    }

    private fun hasCircularReference(
        parent: String,
        child: String,
        context: ParsingContext,
    ): Boolean {
        // Check if the child's descendants contain the parent
        val visited = mutableSetOf<String>()
        return hasCircularReferenceHelper(child, parent, context, visited)
    }

    private fun hasCircularReferenceHelper(
        current: String,
        target: String,
        context: ParsingContext,
        visited: MutableSet<String>,
    ): Boolean {
        if (current in visited) return false

        visited.add(current)
        val children = context.childGroups[current] ?: return false

        if (target in children) return true

        return children.any { child ->
            hasCircularReferenceHelper(child, target, context, visited)
        }
    }

    private fun isChildOfAnyGroup(
        groupName: String,
        context: ParsingContext,
    ): Boolean {
        return context.childGroups.values.any { children ->
            groupName in children
        }
    }

    private fun applyGroupVarsToHosts(
        group: HostGroup,
        groupVars: Map<String, Map<String, String>>,
    ): List<Host> {
        if (group.hosts.isEmpty()) return emptyList()

        val varsToApply = mutableMapOf<String, String>()

        // Always apply all:vars first
        groupVars["all"]?.let { varsToApply.putAll(it) }

        // Collect vars from all parents (from root to current)
        val groupHierarchy = mutableListOf<String>()
        var current: HostGroup? = group
        while (current != null) {
            groupHierarchy.add(0, current.name)
            current = current.parent
        }

        // Apply vars in order (parent vars first, then child vars)
        groupHierarchy.forEach { groupName ->
            groupVars[groupName]?.let { varsToApply.putAll(it) }
        }

        // Process direct hosts
        return group.hosts.map { host ->
            val mergedVars = varsToApply.toMutableMap()
            mergedVars.putAll(host.variables) // Host vars take precedence
            host.copy(variables = mergedVars)
        }
    }

    private data class ParsingContext(
        var currentSection: Section? = null,
        val groups: MutableMap<String, MutableList<HostData>> = mutableMapOf(),
        val groupVars: MutableMap<String, MutableMap<String, String>> = mutableMapOf(),
        val childGroups: MutableMap<String, MutableList<String>> = mutableMapOf(),
        val warnings: MutableList<String> = mutableListOf(),
    )

    private data class Section(
        val name: String,
        val type: SectionType,
    )

    private enum class SectionType {
        HOSTS,
        VARS,
        CHILDREN,
    }

    private data class HostData(
        val name: String,
        val variables: Map<String, String>,
    )
}
