package dev.toliner.ansflow.infrastructure.parser

import dev.toliner.ansflow.domain.model.Environment
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class YAMLInventoryParserTest : BehaviorSpec({
    Given("YAMLInventoryParser") {
        val parser = YAMLInventoryParser()

        When("parsing simple YAML inventory") {
            val content =
                """
                all:
                  hosts:
                    web1.example.com:
                    web2.example.com:
                    db1.example.com:
                    db2.example.com:
                """.trimIndent()

            Then("should parse all hosts in the all group") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!
                inventory.environment shouldBe Environment.DEVELOPMENT

                // Should have one top-level group: all
                inventory.groups shouldHaveSize 1
                val allGroup = inventory.groups.first()
                allGroup.name shouldBe "all"
                allGroup.hosts shouldHaveSize 4
                allGroup.hosts.map { it.name } shouldContainExactlyInAnyOrder
                    listOf(
                        "web1.example.com",
                        "web2.example.com",
                        "db1.example.com",
                        "db2.example.com",
                    )
            }
        }

        When("parsing YAML inventory with groups and variables") {
            val content =
                """
                all:
                  hosts:
                    localhost:
                      ansible_connection: local
                  children:
                    web:
                      hosts:
                        web1.example.com:
                          ansible_host: 192.168.1.10
                          ansible_port: 2222
                        web2.example.com:
                          ansible_host: 192.168.1.11
                      vars:
                        http_port: 80
                        ansible_user: webuser
                    db:
                      hosts:
                        db1.example.com:
                          ansible_user: dbadmin
                        db2.example.com:
                      vars:
                        db_port: 5432
                        ansible_user: dbuser
                """.trimIndent()

            Then("should parse groups with their variables") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!

                val allGroup = inventory.findGroup("all")!!
                allGroup.hosts shouldHaveSize 1
                allGroup.children shouldHaveSize 2

                // Check localhost host
                val localhost = allGroup.hosts.first()
                localhost.name shouldBe "localhost"
                localhost.variables shouldContainExactly
                    mapOf(
                        "ansible_connection" to "local",
                    )

                // Check web group
                val webGroup = allGroup.findChild("web")!!
                webGroup.hosts shouldHaveSize 2

                val web1 = webGroup.hosts.find { it.name == "web1.example.com" }!!
                web1.variables shouldContainExactly
                    mapOf(
                        "ansible_host" to "192.168.1.10",
                        "ansible_port" to "2222",
                        "http_port" to "80",
                        "ansible_user" to "webuser",
                    )

                val web2 = webGroup.hosts.find { it.name == "web2.example.com" }!!
                web2.variables shouldContainExactly
                    mapOf(
                        "ansible_host" to "192.168.1.11",
                        "http_port" to "80",
                        "ansible_user" to "webuser",
                    )

                // Check db group
                val dbGroup = allGroup.findChild("db")!!
                val db1 = dbGroup.hosts.find { it.name == "db1.example.com" }!!
                db1.variables shouldContainExactly
                    mapOf(
                        // Host var overrides group var
                        "ansible_user" to "dbadmin",
                        "db_port" to "5432",
                    )
            }
        }

        When("parsing YAML inventory with nested groups") {
            val content =
                """
                all:
                  children:
                    production:
                      children:
                        web:
                          hosts:
                            web1.example.com:
                            web2.example.com:
                        db:
                          hosts:
                            db1.example.com:
                        app:
                          hosts:
                            app1.example.com:
                            app2.example.com:
                      vars:
                        env: prod
                        ansible_ssh_common_args: '-o StrictHostKeyChecking=no'
                    staging:
                      children:
                        web:
                          hosts:
                            web-staging.example.com:
                      vars:
                        env: staging
                """.trimIndent()

            Then("should create hierarchical group structure") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!

                val allGroup = inventory.findGroup("all")!!
                allGroup.children shouldHaveSize 2

                // Check production hierarchy
                val productionGroup = allGroup.findChild("production")!!
                productionGroup.children shouldHaveSize 3
                productionGroup.children.map { it.name } shouldContainExactlyInAnyOrder listOf("web", "db", "app")

                // Check that parent references are correct
                val webGroup = productionGroup.findChild("web")!!
                webGroup.parent?.name shouldBe "production"

                // Check that group vars are inherited
                val allHosts = productionGroup.getAllHosts()
                allHosts.forEach { host ->
                    host.variables["env"] shouldBe "prod"
                    host.variables["ansible_ssh_common_args"] shouldBe "-o StrictHostKeyChecking=no"
                }

                // Check staging hierarchy
                val stagingGroup = allGroup.findChild("staging")!!
                stagingGroup.children shouldHaveSize 1
                val stagingWebGroup = stagingGroup.findChild("web")!!
                stagingWebGroup.hosts.first().variables["env"] shouldBe "staging"
            }
        }

        When("parsing complex YAML inventory") {
            val content = File("src/test/resources/inventory/complex.yml").readText()

            Then("should handle all features together") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!

                val allGroup = inventory.findGroup("all")!!

                // Check all:vars are applied globally
                allGroup.getAllHosts().forEach { host ->
                    host.variables.containsKey("ansible_user") shouldBe true
                    host.variables.containsKey("ansible_python_interpreter") shouldBe true
                }

                // Check ungrouped hosts
                val ungroupedGroup = allGroup.findChild("ungrouped")
                ungroupedGroup.shouldNotBeNull()
                ungroupedGroup.hosts shouldHaveSize 1

                // Check production group inherits children
                val productionGroup = allGroup.findChild("production")!!
                productionGroup.children.map { it.name } shouldContainExactlyInAnyOrder listOf("webservers", "databases")

                // Check that production hosts have both global and production vars
                val prodHosts = productionGroup.getAllHosts()
                prodHosts.forEach { host ->
                    host.variables["env"] shouldBe "production"
                    host.variables["debug_mode"] shouldBe "false"
                    host.variables["ansible_user"] shouldBe "deploy"
                }
            }
        }

        When("parsing YAML inventory without all group") {
            val content =
                """
                webservers:
                  hosts:
                    web1.example.com:
                    web2.example.com:
                databases:
                  hosts:
                    db1.example.com:
                """.trimIndent()

            Then("should create implicit all group") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!

                // Parser should create an implicit 'all' group
                inventory.groups shouldHaveSize 1
                val allGroup = inventory.groups.first()
                allGroup.name shouldBe "all"
                allGroup.children.map { it.name } shouldContainExactlyInAnyOrder listOf("webservers", "databases")
            }
        }

        When("parsing empty YAML inventory") {
            val content =
                """
                all:
                  hosts:
                  children:
                """.trimIndent()

            Then("should create empty inventory") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!
                inventory.groups shouldHaveSize 1
                inventory.groups.first().hosts shouldHaveSize 0
                inventory.groups.first().children shouldHaveSize 0
            }
        }

        When("parsing malformed YAML inventory") {
            Then("should fail on syntax errors") {
                val content =
                    """
                    all:
                      hosts:
                        web1.example.com
                          ansible_host: 192.168.1.10
                    """.trimIndent()

                val result = parser.parse(content, Environment.DEVELOPMENT)
                result.shouldBeFailure()
                result.exceptionOrNull()?.message shouldContain "YAML"
            }

            Then("should fail on invalid structure") {
                val content =
                    """
                    all:
                      hosts:
                        - web1.example.com
                        - web2.example.com
                    """.trimIndent()

                val result = parser.parse(content, Environment.DEVELOPMENT)
                result.shouldBeFailure()
                result.exceptionOrNull()?.message shouldContain "must be a map"
            }
        }

        When("parsing from file") {
            val testFile = File("src/test/resources/inventory/simple.yml")

            Then("should read and parse file content") {
                val result = parser.parseFile(testFile.absolutePath, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!
                val allGroup = inventory.groups.first()
                allGroup.hosts shouldHaveSize 4
            }
        }

        When("handling special cases") {
            Then("should handle hosts without children") {
                val content =
                    """
                    all:
                      hosts:
                        web1.example.com:
                        web2.example.com:
                    """.trimIndent()

                val result = parser.parse(content, Environment.DEVELOPMENT)
                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!
                inventory.groups.first().hosts shouldHaveSize 2
            }

            Then("should handle groups with only vars") {
                val content =
                    """
                    all:
                      vars:
                        ansible_user: deploy
                      children:
                        web:
                          hosts:
                            web1.example.com:
                    """.trimIndent()

                val result = parser.parse(content, Environment.DEVELOPMENT)
                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!
                val webHost = inventory.groups.first().children.first().hosts.first()
                webHost.variables["ansible_user"] shouldBe "deploy"
            }

            Then("should handle numeric values in variables") {
                val content =
                    """
                    all:
                      hosts:
                        web1.example.com:
                          ansible_port: 2222
                          max_connections: 100
                          load_factor: 0.75
                          enabled: true
                    """.trimIndent()

                val result = parser.parse(content, Environment.DEVELOPMENT)
                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!
                val host = inventory.groups.first().hosts.first()
                host.variables["ansible_port"] shouldBe "2222"
                host.variables["max_connections"] shouldBe "100"
                host.variables["load_factor"] shouldBe "0.75"
                host.variables["enabled"] shouldBe "true"
            }
        }
    }
})
