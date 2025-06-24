package dev.toliner.ansflow.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class HostTest : BehaviorSpec({
    Given("a Host") {
        When("creating with name only") {
            val host = Host(name = "web1")

            Then("should have correct name") {
                host.name shouldBe "web1"
            }

            Then("should have empty variables") {
                host.variables shouldBe emptyMap()
            }
        }

        When("creating with variables") {
            val variables =
                mapOf(
                    "ansible_host" to "192.168.1.10",
                    "ansible_port" to "22",
                    "ansible_user" to "ubuntu",
                )
            val host = Host(name = "web1", variables = variables)

            Then("should have correct name") {
                host.name shouldBe "web1"
            }

            Then("should have all variables") {
                host.variables shouldBe variables
                host.variables["ansible_host"] shouldBe "192.168.1.10"
                host.variables["ansible_port"] shouldBe "22"
                host.variables["ansible_user"] shouldBe "ubuntu"
            }
        }

        When("comparing hosts") {
            val host1 = Host("web1", mapOf("port" to "80"))
            val host2 = Host("web1", mapOf("port" to "80"))
            val host3 = Host("web2", mapOf("port" to "80"))
            val host4 = Host("web1", mapOf("port" to "8080"))

            Then("should be equal when name and variables match") {
                host1 shouldBe host2
                host1.hashCode() shouldBe host2.hashCode()
            }

            Then("should not be equal when name differs") {
                host1 shouldNotBe host3
            }

            Then("should not be equal when variables differ") {
                host1 shouldNotBe host4
            }
        }
    }
})
