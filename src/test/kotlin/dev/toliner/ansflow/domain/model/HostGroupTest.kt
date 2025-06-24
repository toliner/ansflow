package dev.toliner.ansflow.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class HostGroupTest : BehaviorSpec({
    Given("a HostGroup hierarchy") {
        When("creating a simple group") {
            val group =
                HostGroup(
                    name = "web",
                    hosts = listOf(Host("web1"), Host("web2")),
                    children = emptyList(),
                    parent = null,
                )

            Then("should have correct properties") {
                group.name shouldBe "web"
                group.hosts shouldHaveSize 2
                group.children shouldHaveSize 0
                group.parent shouldBe null
            }

            Then("getPath() should return single name") {
                group.getPath() shouldBe "web"
            }

            Then("getAllHosts() should return direct hosts") {
                group.getAllHosts() shouldContainExactlyInAnyOrder listOf(Host("web1"), Host("web2"))
            }
        }

        When("creating nested groups") {
            // Build hierarchy from bottom up with proper parent references
            lateinit var rootGroup: HostGroup
            lateinit var prodGroup: HostGroup

            // Create leaf groups first
            var webGroup = HostGroup(
                    name = "web",
                    hosts = listOf(Host("web1"), Host("web2")),
                    children = emptyList(),
                    parent = null,
                )

            var appGroup = HostGroup(
                    name = "app",
                    hosts = listOf(Host("app1"), Host("app2"), Host("app3")),
                    children = emptyList(),
                    parent = null,
                )

            // Create production group
            prodGroup =
                HostGroup(
                    name = "production",
                    hosts = listOf(Host("prod-lb")),
                    children = listOf(webGroup, appGroup),
                    parent = null,
                )

            // Update children with parent reference
            webGroup = webGroup.copy(parent = prodGroup)
            appGroup = appGroup.copy(parent = prodGroup)
            prodGroup = prodGroup.copy(children = listOf(webGroup, appGroup))

            // Create root group
            rootGroup =
                HostGroup(
                    name = "all",
                    hosts = emptyList(),
                    children = listOf(prodGroup),
                    parent = null,
                )

            // Update production group with parent and rebuild the tree
            prodGroup = prodGroup.copy(parent = rootGroup)
            // Need to update the children of prodGroup to maintain parent references
            webGroup = webGroup.copy(parent = prodGroup)
            appGroup = appGroup.copy(parent = prodGroup)
            prodGroup = prodGroup.copy(children = listOf(webGroup, appGroup))
            rootGroup = rootGroup.copy(children = listOf(prodGroup))

            Then("getPath() should return hierarchical path") {
                webGroup.getPath() shouldBe "all:production:web"
                appGroup.getPath() shouldBe "all:production:app"
                prodGroup.getPath() shouldBe "all:production"
                rootGroup.getPath() shouldBe "all"
            }

            Then("getAllHosts() should return all hosts recursively") {
                webGroup.getAllHosts() shouldContainExactlyInAnyOrder
                    listOf(
                        Host("web1"), Host("web2"),
                    )

                appGroup.getAllHosts() shouldContainExactlyInAnyOrder
                    listOf(
                        Host("app1"), Host("app2"), Host("app3"),
                    )

                prodGroup.getAllHosts() shouldContainExactlyInAnyOrder
                    listOf(
                        Host("prod-lb"),
                        Host("web1"), Host("web2"),
                        Host("app1"), Host("app2"), Host("app3"),
                    )

                rootGroup.getAllHosts() shouldContainExactlyInAnyOrder
                    listOf(
                        Host("prod-lb"),
                        Host("web1"), Host("web2"),
                        Host("app1"), Host("app2"), Host("app3"),
                    )
            }
        }

        When("finding child groups") {
            val child1 = HostGroup("child1", emptyList(), emptyList(), null)
            val child2 = HostGroup("child2", emptyList(), emptyList(), null)
            val parent =
                HostGroup(
                    name = "parent",
                    hosts = emptyList(),
                    children = listOf(child1, child2),
                    parent = null,
                )

            Then("should find direct children by name") {
                parent.findChild("child1") shouldBe child1
                parent.findChild("child2") shouldBe child2
                parent.findChild("nonexistent") shouldBe null
            }
        }
    }
})
