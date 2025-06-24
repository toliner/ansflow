package dev.toliner.ansflow.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class InventoryTest : BehaviorSpec({
    Given("an Inventory") {
        val webGroup =
            HostGroup(
                name = "web",
                hosts = listOf(Host("web1"), Host("web2")),
                children = emptyList(),
                parent = null,
            )

        val dbGroup =
            HostGroup(
                name = "db",
                hosts = listOf(Host("db1")),
                children = emptyList(),
                parent = null,
            )

        When("creating with host groups") {
            val inventory =
                Inventory(
                    environment = Environment.DEVELOPMENT,
                    groups = listOf(webGroup, dbGroup),
                )

            Then("should have correct environment") {
                inventory.environment shouldBe Environment.DEVELOPMENT
            }

            Then("should contain all groups") {
                inventory.groups shouldHaveSize 2
                inventory.groups shouldContainExactly listOf(webGroup, dbGroup)
            }

            Then("should find group by name") {
                inventory.findGroup("web") shouldBe webGroup
                inventory.findGroup("db") shouldBe dbGroup
                inventory.findGroup("nonexistent") shouldBe null
            }
        }

        When("creating empty inventory") {
            val inventory =
                Inventory(
                    environment = Environment.PRODUCTION,
                    groups = emptyList(),
                )

            Then("should have no groups") {
                inventory.groups shouldHaveSize 0
            }

            Then("should not find any groups") {
                inventory.findGroup("any") shouldBe null
            }
        }

        When("merging inventories") {
            val inventory1 =
                Inventory(
                    environment = Environment.DEVELOPMENT,
                    groups = listOf(webGroup),
                )

            val inventory2 =
                Inventory(
                    environment = Environment.DEVELOPMENT,
                    groups = listOf(dbGroup),
                )

            val merged = inventory1.merge(inventory2)

            Then("should contain groups from both inventories") {
                merged.groups shouldHaveSize 2
                merged.groups.map { it.name } shouldContainExactly listOf("web", "db")
            }

            Then("should keep the same environment") {
                merged.environment shouldBe Environment.DEVELOPMENT
            }
        }
    }
})
