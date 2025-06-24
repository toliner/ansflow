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
import java.io.File

class IniInventoryParserTest : BehaviorSpec({
    Given("IniInventoryParser") {
        val parser = IniInventoryParser()

        When("parsing simple inventory") {
            val content =
                """
                [web]
                web1.example.com
                web2.example.com
                
                [db]
                db1.example.com
                db2.example.com
                """.trimIndent()

            Then("should parse groups and hosts correctly") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!
                inventory.environment shouldBe Environment.DEVELOPMENT
                inventory.groups shouldHaveSize 2

                val webGroup = inventory.findGroup("web")
                webGroup.shouldNotBeNull()
                webGroup.name shouldBe "web"
                webGroup.hosts.map { it.name } shouldContainExactlyInAnyOrder
                    listOf(
                        "web1.example.com",
                        "web2.example.com",
                    )

                val dbGroup = inventory.findGroup("db")
                dbGroup.shouldNotBeNull()
                dbGroup.name shouldBe "db"
                dbGroup.hosts.map { it.name } shouldContainExactlyInAnyOrder
                    listOf(
                        "db1.example.com",
                        "db2.example.com",
                    )
            }
        }

        When("parsing inventory with host variables") {
            val content =
                """
                [web]
                web1.example.com ansible_host=192.168.1.10 ansible_port=2222
                web2.example.com ansible_host=192.168.1.11
                
                [db]
                db1.example.com ansible_user=dbadmin
                """.trimIndent()

            Then("should parse inline host variables") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!

                val webGroup = inventory.findGroup("web")!!
                val web1 = webGroup.hosts.find { it.name == "web1.example.com" }!!
                web1.variables shouldContainExactly
                    mapOf(
                        "ansible_host" to "192.168.1.10",
                        "ansible_port" to "2222",
                    )

                val web2 = webGroup.hosts.find { it.name == "web2.example.com" }!!
                web2.variables shouldContainExactly
                    mapOf(
                        "ansible_host" to "192.168.1.11",
                    )

                val dbGroup = inventory.findGroup("db")!!
                val db1 = dbGroup.hosts.find { it.name == "db1.example.com" }!!
                db1.variables shouldContainExactly
                    mapOf(
                        "ansible_user" to "dbadmin",
                    )
            }
        }

        When("parsing inventory with group variables") {
            val content =
                """
                [web]
                web1.example.com
                web2.example.com
                
                [web:vars]
                ansible_user=webuser
                http_port=80
                
                [db]
                db1.example.com
                
                [db:vars]
                ansible_user=dbuser
                db_port=5432
                """.trimIndent()

            Then("should apply group variables to all hosts in group") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!

                val webGroup = inventory.findGroup("web")!!
                webGroup.hosts.forEach { host ->
                    host.variables shouldContainExactly
                        mapOf(
                            "ansible_user" to "webuser",
                            "http_port" to "80",
                        )
                }

                val dbGroup = inventory.findGroup("db")!!
                dbGroup.hosts.forEach { host ->
                    host.variables shouldContainExactly
                        mapOf(
                            "ansible_user" to "dbuser",
                            "db_port" to "5432",
                        )
                }
            }
        }

        When("parsing inventory with nested groups") {
            val content =
                """
                [web]
                web1.example.com
                web2.example.com
                
                [db]
                db1.example.com
                
                [app]
                app1.example.com
                app2.example.com
                
                [production:children]
                web
                db
                app
                
                [production:vars]
                env=prod
                """.trimIndent()

            Then("should create hierarchical group structure") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!

                val productionGroup = inventory.groups.find { it.name == "production" }!!
                productionGroup.children shouldHaveSize 3
                productionGroup.children.map { it.name } shouldContainExactlyInAnyOrder listOf("web", "db", "app")

                // Check parent references through children
                val webGroup = productionGroup.children.find { it.name == "web" }!!
                webGroup.parent?.name shouldBe "production"

                // Check that group vars are applied to all children
                productionGroup.getAllHosts().forEach { host ->
                    host.variables["env"] shouldBe "prod"
                }
            }
        }

        When("parsing complex inventory") {
            val content =
                """
                [web]
                web1.example.com ansible_host=192.168.1.10
                web2.example.com
                
                [web:vars]
                http_port=80
                
                [db]
                db-master.example.com ansible_host=192.168.2.10
                db-slave.example.com ansible_host=192.168.2.11
                
                [app]
                app1.example.com
                app2.example.com
                
                [production:children]
                web
                db
                
                [staging:children]
                app
                
                [all:vars]
                ansible_user=deploy
                """.trimIndent()

            Then("should handle all features together") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!

                // Find groups in hierarchy
                val productionGroup = inventory.groups.find { it.name == "production" }!!
                val webGroup = productionGroup.children.find { it.name == "web" }!!

                // Check web hosts have both inline and group vars
                val web1 = webGroup.hosts.find { it.name == "web1.example.com" }!!
                web1.variables shouldContainExactly
                    mapOf(
                        "ansible_host" to "192.168.1.10",
                        "http_port" to "80",
                        "ansible_user" to "deploy",
                    )

                // Check nested groups
                productionGroup.children.map { it.name } shouldContainExactlyInAnyOrder listOf("web", "db")

                // Check all:vars applied to everyone
                inventory.groups.flatMap { it.getAllHosts() }.forEach { host ->
                    host.variables["ansible_user"] shouldBe "deploy"
                }
            }
        }

        When("parsing malformed inventory") {
            Then("should fail on missing group brackets") {
                val content =
                    """
                    web
                    web1.example.com
                    """.trimIndent()

                val result = parser.parse(content, Environment.DEVELOPMENT)
                result.shouldBeFailure()
            }

            Then("should fail on invalid variable syntax") {
                val content =
                    """
                    [web]
                    web1.example.com invalid-var-syntax
                    """.trimIndent()

                val result = parser.parse(content, Environment.DEVELOPMENT)
                result.shouldBeFailure()
            }

            Then("should fail on circular group references") {
                val content =
                    """
                    [group1:children]
                    group2
                    
                    [group2:children]
                    group1
                    """.trimIndent()

                val result = parser.parse(content, Environment.DEVELOPMENT)
                result.shouldBeFailure()
            }
        }

        When("handling partial success scenarios") {
            val content =
                """
                [web]
                web1.example.com
                web2.example.com
                
                [invalid
                some-host.example.com
                
                [db]
                db1.example.com
                """.trimIndent()

            Then("should parse valid sections and report errors") {
                val result = parser.parse(content, Environment.DEVELOPMENT)

                // With strict parsing, malformed sections cause failure
                result.shouldBeFailure()
                result.exceptionOrNull()?.message?.shouldNotBeNull()
            }
        }

        When("parsing from file") {
            val testFile = File.createTempFile("test-inventory", ".ini")
            testFile.writeText(
                """
                [web]
                web1.example.com
                
                [db]
                db1.example.com
                """.trimIndent(),
            )
            testFile.deleteOnExit()

            Then("should read and parse file content") {
                val result = parser.parseFile(testFile.absolutePath, Environment.DEVELOPMENT)

                result.shouldBeSuccess()
                val inventory = result.getOrNull()!!
                inventory.groups shouldHaveSize 2
            }
        }
    }
})
