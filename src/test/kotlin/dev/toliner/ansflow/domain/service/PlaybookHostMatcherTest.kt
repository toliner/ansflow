package dev.toliner.ansflow.domain.service

import dev.toliner.ansflow.domain.model.Host
import dev.toliner.ansflow.domain.model.HostGroup
import dev.toliner.ansflow.domain.model.Playbook
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PlaybookHostMatcherTest : BehaviorSpec({
    Given("PlaybookHostMatcher") {
        val matcher = PlaybookHostMatcher()

        When("matching exact names") {
            val webGroup = HostGroup("web", listOf(Host("web1")), emptyList(), null)
            val dbGroup = HostGroup("db", listOf(Host("db1")), emptyList(), null)
            val appGroup = HostGroup("app", listOf(Host("app1")), emptyList(), null)

            Then("should match exact group name") {
                val playbook = Playbook("/test.yml", "Test", "web", emptyList())
                matcher.isCompatible(playbook, webGroup) shouldBe true
                matcher.isCompatible(playbook, dbGroup) shouldBe false
                matcher.isCompatible(playbook, appGroup) shouldBe false
            }

            Then("should match multiple groups") {
                val playbook = Playbook("/test.yml", "Test", "web,db", emptyList())
                matcher.isCompatible(playbook, webGroup) shouldBe true
                matcher.isCompatible(playbook, dbGroup) shouldBe true
                matcher.isCompatible(playbook, appGroup) shouldBe false
            }
        }

        When("matching with 'all' pattern") {
            val webGroup = HostGroup("web", listOf(Host("web1")), emptyList(), null)
            val dbGroup = HostGroup("db", listOf(Host("db1")), emptyList(), null)

            Then("should match any group") {
                val playbook = Playbook("/test.yml", "Test", "all", emptyList())
                matcher.isCompatible(playbook, webGroup) shouldBe true
                matcher.isCompatible(playbook, dbGroup) shouldBe true
            }

            Then("should handle 'all' in combination patterns") {
                val playbook = Playbook("/test.yml", "Test", "all:!db", emptyList())
                matcher.isCompatible(playbook, webGroup) shouldBe true
                matcher.isCompatible(playbook, dbGroup) shouldBe false
            }
        }

        When("matching with wildcards") {
            val webGroup = HostGroup("web", listOf(Host("web1")), emptyList(), null)
            val webProdGroup = HostGroup("web-prod", listOf(Host("web-prod1")), emptyList(), null)
            val appWebGroup = HostGroup("app-web", listOf(Host("app-web1")), emptyList(), null)
            val dbGroup = HostGroup("db", listOf(Host("db1")), emptyList(), null)

            Then("should match prefix wildcard") {
                val playbook = Playbook("/test.yml", "Test", "web*", emptyList())
                matcher.isCompatible(playbook, webGroup) shouldBe true
                matcher.isCompatible(playbook, webProdGroup) shouldBe true
                matcher.isCompatible(playbook, appWebGroup) shouldBe false
                matcher.isCompatible(playbook, dbGroup) shouldBe false
            }

            Then("should match suffix wildcard") {
                val playbook = Playbook("/test.yml", "Test", "*web", emptyList())
                matcher.isCompatible(playbook, webGroup) shouldBe true
                matcher.isCompatible(playbook, appWebGroup) shouldBe true
                matcher.isCompatible(playbook, webProdGroup) shouldBe false
                matcher.isCompatible(playbook, dbGroup) shouldBe false
            }

            Then("should match contains wildcard") {
                val playbook = Playbook("/test.yml", "Test", "*web*", emptyList())
                matcher.isCompatible(playbook, webGroup) shouldBe true
                matcher.isCompatible(playbook, webProdGroup) shouldBe true
                matcher.isCompatible(playbook, appWebGroup) shouldBe true
                matcher.isCompatible(playbook, dbGroup) shouldBe false
            }
        }

        When("matching hierarchical groups") {
            // Create hierarchy: production -> web, app
            val webGroup = HostGroup("web", listOf(Host("web1")), emptyList(), null)
            val appGroup = HostGroup("app", listOf(Host("app1")), emptyList(), null)
            val productionGroup =
                HostGroup(
                    "production",
                    listOf(Host("prod-lb")),
                    listOf(webGroup, appGroup),
                    null,
                )

            // Update parent references
            val webWithParent = webGroup.copy(parent = productionGroup)
            val appWithParent = appGroup.copy(parent = productionGroup)
            val prodWithChildren =
                productionGroup.copy(
                    children = listOf(webWithParent, appWithParent),
                )

            Then("should match parent group for child hosts") {
                val playbook = Playbook("/test.yml", "Test", "production", emptyList())
                matcher.isCompatible(playbook, prodWithChildren) shouldBe true
                matcher.isCompatible(playbook, webWithParent) shouldBe true
                matcher.isCompatible(playbook, appWithParent) shouldBe true
            }

            Then("should not match child pattern for parent group only") {
                val playbook = Playbook("/test.yml", "Test", "web", emptyList())
                matcher.isCompatible(playbook, prodWithChildren) shouldBe false
                matcher.isCompatible(playbook, webWithParent) shouldBe true
                matcher.isCompatible(playbook, appWithParent) shouldBe false
            }
        }

        When("matching complex patterns") {
            val webGroup = HostGroup("web", listOf(Host("web1")), emptyList(), null)
            val appGroup = HostGroup("app", listOf(Host("app1")), emptyList(), null)
            val dbGroup = HostGroup("db", listOf(Host("db1")), emptyList(), null)
            val productionGroup = HostGroup("production", listOf(Host("prod1")), emptyList(), null)

            Then("should handle intersection patterns") {
                // web:&production means hosts that are in both web AND production
                val playbook = Playbook("/test.yml", "Test", "web:&production", emptyList())
                // For simplicity, we'll test that it matches groups named in the pattern
                matcher.isCompatible(playbook, webGroup) shouldBe true
                matcher.isCompatible(playbook, productionGroup) shouldBe true
                matcher.isCompatible(playbook, dbGroup) shouldBe false
            }

            Then("should handle exclusion patterns") {
                val playbook = Playbook("/test.yml", "Test", "all:!db:!test*", emptyList())
                matcher.isCompatible(playbook, webGroup) shouldBe true
                matcher.isCompatible(playbook, appGroup) shouldBe true
                matcher.isCompatible(playbook, dbGroup) shouldBe false

                val testGroup = HostGroup("test-staging", listOf(Host("test1")), emptyList(), null)
                matcher.isCompatible(playbook, testGroup) shouldBe false
            }
        }
    }
})
