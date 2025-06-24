package dev.toliner.ansflow.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class ExecutionHistoryTest : BehaviorSpec({
    Given("ExecutionHistory") {
        When("creating a successful execution") {
            val startTime = Instant.now()
            val entry =
                ExecutionHistory(
                    id = "exec-001",
                    environment = Environment.DEVELOPMENT,
                    playbookPath = "/ansible/playbooks/web.yml",
                    hostGroups = listOf("web", "app"),
                    executedAt = startTime,
                    duration = 45_000,
                    status = ExecutionStatus.SUCCESS,
                    outputPath = "/home/user/.local/ansflow/logs/exec-001.log",
                    checkMode = false,
                    extraVars = mapOf("version" to "1.2.3"),
                )

            Then("should have all properties set correctly") {
                entry.id shouldBe "exec-001"
                entry.environment shouldBe Environment.DEVELOPMENT
                entry.playbookPath shouldBe "/ansible/playbooks/web.yml"
                entry.hostGroups shouldBe listOf("web", "app")
                entry.executedAt shouldBe startTime
                entry.duration shouldBe 45_000
                entry.status shouldBe ExecutionStatus.SUCCESS
                entry.outputPath shouldBe "/home/user/.local/ansflow/logs/exec-001.log"
                entry.checkMode shouldBe false
                entry.extraVars shouldBe mapOf("version" to "1.2.3")
            }
        }

        When("creating a failed execution") {
            val entry =
                ExecutionHistory(
                    id = "exec-002",
                    environment = Environment.PRODUCTION,
                    playbookPath = "/ansible/playbooks/deploy.yml",
                    hostGroups = listOf("all"),
                    executedAt = Instant.now(),
                    duration = 12_500,
                    status = ExecutionStatus.FAILED,
                    outputPath = "/home/user/.local/ansflow/logs/exec-002.log",
                    checkMode = true,
                    extraVars = emptyMap(),
                    errorMessage = "Task 'Deploy application' failed on host web1",
                )

            Then("should include error message") {
                entry.status shouldBe ExecutionStatus.FAILED
                entry.errorMessage shouldBe "Task 'Deploy application' failed on host web1"
                entry.checkMode shouldBe true
            }
        }

        When("creating from current execution") {
            val beforeExecution = Instant.now().toEpochMilli()

            val entry =
                ExecutionHistory.fromExecution(
                    environment = Environment.DEVELOPMENT,
                    playbookPath = "/ansible/playbooks/test.yml",
                    hostGroups = listOf("test"),
                    duration = 5000,
                    status = ExecutionStatus.SUCCESS,
                    outputPath = "/logs/test.log",
                    checkMode = false,
                    extraVars = mapOf("debug" to "true"),
                )

            val afterExecution = Instant.now().toEpochMilli()

            Then("should generate unique ID") {
                entry.id shouldNotBe null
                entry.id.isNotEmpty() shouldBe true
            }

            Then("should set current timestamp") {
                val executionTime = entry.executedAt.toEpochMilli()
                executionTime shouldBeGreaterThan beforeExecution - 1
                executionTime shouldBeLessThanOrEqual afterExecution
            }

            Then("should have all other properties") {
                entry.environment shouldBe Environment.DEVELOPMENT
                entry.playbookPath shouldBe "/ansible/playbooks/test.yml"
                entry.hostGroups shouldBe listOf("test")
                entry.duration shouldBe 5000
                entry.status shouldBe ExecutionStatus.SUCCESS
                entry.outputPath shouldBe "/logs/test.log"
                entry.checkMode shouldBe false
                entry.extraVars shouldBe mapOf("debug" to "true")
            }
        }
    }

    Given("ExecutionStatus") {
        When("using status values") {
            Then("should have all expected statuses") {
                ExecutionStatus.SUCCESS shouldNotBe null
                ExecutionStatus.FAILED shouldNotBe null
                ExecutionStatus.CANCELLED shouldNotBe null
                ExecutionStatus.values().size shouldBe 3
            }
        }
    }

    Given("ExecutionSummary") {
        When("creating execution summary") {
            val summary =
                ExecutionSummary(
                    totalTasks = 25,
                    completedTasks = 23,
                    failedTasks = 2,
                    skippedTasks = 0,
                    changedTasks = 15,
                    unreachableHosts = emptyList(),
                    failedHosts = listOf("web2", "web3"),
                )

            Then("should track all execution metrics") {
                summary.totalTasks shouldBe 25
                summary.completedTasks shouldBe 23
                summary.failedTasks shouldBe 2
                summary.skippedTasks shouldBe 0
                summary.changedTasks shouldBe 15
                summary.unreachableHosts shouldBe emptyList()
                summary.failedHosts shouldBe listOf("web2", "web3")
            }

            Then("should calculate success state") {
                summary.isSuccess() shouldBe false // Has failed tasks
            }
        }

        When("creating successful summary") {
            val summary =
                ExecutionSummary(
                    totalTasks = 10,
                    completedTasks = 10,
                    failedTasks = 0,
                    skippedTasks = 3,
                    changedTasks = 7,
                    unreachableHosts = emptyList(),
                    failedHosts = emptyList(),
                )

            Then("should be marked as successful") {
                summary.isSuccess() shouldBe true
            }
        }
    }
})
