package dev.toliner.ansflow.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class PlaybookTest : BehaviorSpec({
    Given("a Playbook") {
        When("creating a simple playbook") {
            val task1 = Task(name = "Install nginx", module = "apt", args = mapOf("name" to "nginx", "state" to "present"))
            val task2 = Task(name = "Start nginx", module = "service", args = mapOf("name" to "nginx", "state" to "started"))

            val playbook =
                Playbook(
                    path = "/ansible/playbooks/web.yml",
                    name = "Configure web servers",
                    hosts = "web",
                    tasks = listOf(task1, task2),
                )

            Then("should have correct properties") {
                playbook.path shouldBe "/ansible/playbooks/web.yml"
                playbook.name shouldBe "Configure web servers"
                playbook.hosts shouldBe "web"
                playbook.tasks shouldHaveSize 2
                playbook.tasks shouldContainExactly listOf(task1, task2)
            }
        }

        When("creating playbook with pattern hosts") {
            val playbook1 =
                Playbook(
                    path = "/ansible/playbooks/all.yml",
                    name = "Configure all servers",
                    hosts = "all",
                    tasks = emptyList(),
                )

            val playbook2 =
                Playbook(
                    path = "/ansible/playbooks/web-prod.yml",
                    name = "Configure production web",
                    hosts = "web:&production",
                    tasks = emptyList(),
                )

            val playbook3 =
                Playbook(
                    path = "/ansible/playbooks/not-db.yml",
                    name = "Configure non-database servers",
                    hosts = "all:!db",
                    tasks = emptyList(),
                )

            Then("should support various host patterns") {
                playbook1.hosts shouldBe "all"
                playbook2.hosts shouldBe "web:&production"
                playbook3.hosts shouldBe "all:!db"
            }
        }

        When("creating playbook with multiple host groups") {
            val playbook =
                Playbook(
                    path = "/ansible/playbooks/infra.yml",
                    name = "Configure infrastructure",
                    hosts = "web,app,db",
                    tasks = emptyList(),
                )

            Then("should have comma-separated hosts") {
                playbook.hosts shouldBe "web,app,db"
            }
        }
    }

    Given("a Task") {
        When("creating a task") {
            val task =
                Task(
                    name = "Install package",
                    module = "apt",
                    args =
                        mapOf(
                            "name" to "nginx",
                            "state" to "present",
                            "update_cache" to "yes",
                        ),
                )

            Then("should have correct properties") {
                task.name shouldBe "Install package"
                task.module shouldBe "apt"
                task.args shouldBe
                    mapOf(
                        "name" to "nginx",
                        "state" to "present",
                        "update_cache" to "yes",
                    )
            }
        }

        When("creating task without args") {
            val task =
                Task(
                    name = "Gather facts",
                    module = "setup",
                    args = emptyMap(),
                )

            Then("should have empty args map") {
                task.args shouldBe emptyMap()
            }
        }
    }
})
