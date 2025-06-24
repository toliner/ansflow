package dev.toliner.ansflow.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class PresetTest : BehaviorSpec({
    Given("a Preset") {
        When("creating a simple preset") {
            val preset =
                Preset(
                    id = "preset-web-deploy",
                    name = "Deploy Web Application",
                    description = "Standard deployment for web servers",
                    environment = Environment.PRODUCTION,
                    playbookPath = "/ansible/playbooks/deploy-web.yml",
                    hostGroups = listOf("web", "app"),
                    checkMode = false,
                    extraVars =
                        mapOf(
                            "version" to "latest",
                            "restart_services" to "true",
                        ),
                    createdAt = Instant.now(),
                    lastUsedAt = null,
                )

            Then("should have all properties set") {
                preset.id shouldBe "preset-web-deploy"
                preset.name shouldBe "Deploy Web Application"
                preset.description shouldBe "Standard deployment for web servers"
                preset.environment shouldBe Environment.PRODUCTION
                preset.playbookPath shouldBe "/ansible/playbooks/deploy-web.yml"
                preset.hostGroups shouldBe listOf("web", "app")
                preset.checkMode shouldBe false
                preset.extraVars shouldBe
                    mapOf(
                        "version" to "latest",
                        "restart_services" to "true",
                    )
                preset.createdAt shouldNotBe null
                preset.lastUsedAt shouldBe null
            }
        }

        When("creating preset for check mode") {
            val preset =
                Preset(
                    id = "preset-check",
                    name = "Dry Run Database Update",
                    description = "Check what would change without applying",
                    environment = Environment.DEVELOPMENT,
                    playbookPath = "/ansible/playbooks/db-update.yml",
                    hostGroups = listOf("db"),
                    checkMode = true,
                    extraVars = emptyMap(),
                    createdAt = Instant.now(),
                    lastUsedAt = null,
                )

            Then("should have check mode enabled") {
                preset.checkMode shouldBe true
                preset.environment shouldBe Environment.DEVELOPMENT
            }
        }

        When("updating last used time") {
            // 1 hour ago
            val createdTime = Instant.now().minusSeconds(3600)
            val preset =
                Preset(
                    id = "preset-1",
                    name = "Test Preset",
                    description = "Test",
                    environment = Environment.DEVELOPMENT,
                    playbookPath = "/test.yml",
                    hostGroups = listOf("test"),
                    checkMode = false,
                    extraVars = emptyMap(),
                    createdAt = createdTime,
                    lastUsedAt = null,
                )

            val usedTime = Instant.now()
            val updatedPreset = preset.copy(lastUsedAt = usedTime)

            Then("should update last used timestamp") {
                updatedPreset.lastUsedAt shouldBe usedTime
                updatedPreset.createdAt shouldBe createdTime // Should not change
            }
        }

        When("creating from execution history") {
            val history =
                ExecutionHistory(
                    id = "exec-123",
                    environment = Environment.PRODUCTION,
                    playbookPath = "/ansible/playbooks/backup.yml",
                    hostGroups = listOf("db", "storage"),
                    executedAt = Instant.now(),
                    duration = 120_000,
                    status = ExecutionStatus.SUCCESS,
                    outputPath = "/logs/exec-123.log",
                    checkMode = false,
                    extraVars = mapOf("backup_type" to "full"),
                )

            val preset =
                Preset.fromHistory(
                    history = history,
                    name = "Full Backup",
                    description = "Perform full backup of databases and storage",
                )

            Then("should create preset from history") {
                preset.name shouldBe "Full Backup"
                preset.description shouldBe "Perform full backup of databases and storage"
                preset.environment shouldBe Environment.PRODUCTION
                preset.playbookPath shouldBe "/ansible/playbooks/backup.yml"
                preset.hostGroups shouldBe listOf("db", "storage")
                preset.checkMode shouldBe false
                preset.extraVars shouldBe mapOf("backup_type" to "full")
                preset.id shouldNotBe "exec-123" // Should generate new ID
                preset.lastUsedAt shouldBe null
            }
        }
    }
})
