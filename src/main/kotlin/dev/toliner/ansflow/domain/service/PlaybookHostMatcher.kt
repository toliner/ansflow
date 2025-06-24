package dev.toliner.ansflow.domain.service

import dev.toliner.ansflow.domain.model.HostGroup
import dev.toliner.ansflow.domain.model.Playbook

class PlaybookHostMatcher {
    fun isCompatible(
        playbook: Playbook,
        hostGroup: HostGroup,
    ): Boolean {
        val patterns = parseHostPatterns(playbook.hosts)
        return isGroupMatchingPatterns(hostGroup, patterns)
    }

    private fun parseHostPatterns(hostPattern: String): HostPatterns {
        // Split by : to handle complex patterns
        val parts = hostPattern.split(":")
        val includes = mutableListOf<String>()
        val excludes = mutableListOf<String>()
        val intersections = mutableListOf<String>()

        parts.forEach { part ->
            when {
                part.startsWith("!") -> excludes.add(part.substring(1))
                part.startsWith("&") -> intersections.add(part.substring(1))
                else -> {
                    // Handle comma-separated includes
                    includes.addAll(part.split(",").map { it.trim() })
                }
            }
        }

        return HostPatterns(includes, excludes, intersections)
    }

    private fun isGroupMatchingPatterns(
        hostGroup: HostGroup,
        patterns: HostPatterns,
    ): Boolean {
        // Check exclusions first
        if (patterns.excludes.any { matchesPattern(hostGroup.name, it) }) {
            return false
        }

        // Check if group matches any include pattern
        val matchesInclude =
            if (patterns.includes.isNotEmpty()) {
                patterns.includes.any { pattern ->
                    matchesPattern(hostGroup.name, pattern) || isParentMatching(hostGroup, pattern)
                }
            } else {
                false
            }

        // For intersections, in a simplified implementation, we check if the group
        // matches any of the intersection patterns (similar to includes)
        val matchesIntersection =
            if (patterns.intersections.isNotEmpty()) {
                patterns.intersections.any { pattern ->
                    matchesPattern(hostGroup.name, pattern) || isParentMatching(hostGroup, pattern)
                }
            } else {
                false
            }

        return matchesInclude || matchesIntersection
    }

    private fun isParentMatching(
        hostGroup: HostGroup,
        pattern: String,
    ): Boolean {
        var current = hostGroup.parent
        while (current != null) {
            if (matchesPattern(current.name, pattern)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun matchesPattern(
        name: String,
        pattern: String,
    ): Boolean {
        return when {
            pattern == "all" -> true
            pattern.contains("*") -> matchesWildcard(name, pattern)
            else -> name == pattern
        }
    }

    private fun matchesWildcard(
        name: String,
        pattern: String,
    ): Boolean {
        return when {
            pattern == "*" -> true
            pattern.startsWith("*") && pattern.endsWith("*") -> {
                val middle = pattern.substring(1, pattern.length - 1)
                name.contains(middle)
            }
            pattern.startsWith("*") -> {
                val suffix = pattern.substring(1)
                name.endsWith(suffix)
            }
            pattern.endsWith("*") -> {
                val prefix = pattern.substring(0, pattern.length - 1)
                name.startsWith(prefix)
            }
            else -> {
                // Handle patterns with * in the middle
                val regex = pattern.replace("*", ".*").toRegex()
                regex.matches(name)
            }
        }
    }

    private data class HostPatterns(
        val includes: List<String>,
        val excludes: List<String>,
        val intersections: List<String>,
    )
}
