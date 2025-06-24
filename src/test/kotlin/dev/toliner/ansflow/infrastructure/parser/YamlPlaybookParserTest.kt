package dev.toliner.ansflow.infrastructure.parser

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class YamlPlaybookParserTest : BehaviorSpec({
    Given("YamlPlaybookParser") {
        val parser = YamlPlaybookParser()

        When("parsing simple playbook") {
            val content =
                """
                ---
                - name: Simple playbook
                  hosts: web
                  tasks:
                    - name: Ensure nginx is installed
                      package:
                        name: nginx
                        state: present
                    
                    - name: Start nginx service
                      service:
                        name: nginx
                        state: started
                        enabled: yes
                """.trimIndent()

            Then("should extract basic playbook information") {
                val result = parser.parse(content)

                result.shouldBeSuccess()
                val playbook = result.getOrNull()!!

                playbook.name shouldBe "Simple playbook"
                playbook.hosts shouldBe "web"
                playbook.path shouldBe "inline"
                playbook.tasks shouldHaveSize 2

                val firstTask = playbook.tasks[0]
                firstTask.name shouldBe "Ensure nginx is installed"
                firstTask.module shouldBe "package"
                firstTask.args shouldContainKey "name"
                firstTask.args shouldContainKey "state"

                val secondTask = playbook.tasks[1]
                secondTask.name shouldBe "Start nginx service"
                secondTask.module shouldBe "service"
            }
        }

        When("parsing playbook with multiple hosts") {
            val content =
                """
                ---
                - name: Deploy to multiple hosts
                  hosts: web:db:app
                  tasks:
                    - name: Update system
                      apt:
                        update_cache: yes
                """.trimIndent()

            Then("should parse all host patterns") {
                val result = parser.parse(content)

                result.shouldBeSuccess()
                val playbook = result.getOrNull()!!

                playbook.hosts shouldBe "web:db:app"
            }
        }

        When("parsing playbook with complex host patterns") {
            val content =
                """
                ---
                - name: Complex hosts
                  hosts: web:&production:!web-test
                  tasks:
                    - debug:
                        msg: "Complex host pattern"
                """.trimIndent()

            Then("should handle complex patterns") {
                val result = parser.parse(content)

                result.shouldBeSuccess()
                val playbook = result.getOrNull()!!

                // Complex patterns should be preserved as-is for Ansible compatibility
                playbook.hosts shouldBe "web:&production:!web-test"
            }
        }

        When("parsing playbook with variable hosts") {
            val content =
                """
                ---
                - name: Variable hosts
                  hosts: "{{ target_hosts | default('all') }}"
                  tasks:
                    - name: Test task
                      debug:
                        msg: "Running on variable hosts"
                """.trimIndent()

            Then("should handle variable host patterns") {
                val result = parser.parse(content)

                result.shouldBeSuccess()
                val playbook = result.getOrNull()!!

                // Variable patterns should be preserved
                playbook.hosts shouldBe "{{ target_hosts | default('all') }}"
            }
        }

        When("parsing playbook with list hosts") {
            val content =
                """
                ---
                - name: List hosts
                  hosts:
                    - web
                    - db
                    - app
                  tasks:
                    - name: Test task
                      debug:
                        msg: "Running on list hosts"
                """.trimIndent()

            Then("should convert list to colon-separated string") {
                val result = parser.parse(content)

                result.shouldBeSuccess()
                val playbook = result.getOrNull()!!

                playbook.hosts shouldBe "web:db:app"
            }
        }

        When("parsing playbook with multiple plays") {
            val content =
                """
                ---
                - name: First play
                  hosts: web
                  tasks:
                    - name: Web task
                      debug:
                        msg: "Web"
                
                - name: Second play
                  hosts: db
                  tasks:
                    - name: DB task
                      debug:
                        msg: "DB"
                """.trimIndent()

            Then("should parse first play only") {
                val result = parser.parse(content)

                result.shouldBeSuccess()
                val playbook = result.getOrNull()!!

                // Parser should focus on the first play for simplicity
                playbook.name shouldBe "First play"
                playbook.hosts shouldBe "web"
            }
        }

        When("parsing playbook with various task modules") {
            val content =
                """
                ---
                - name: Various modules
                  hosts: all
                  tasks:
                    - name: Debug message
                      debug:
                        msg: "Hello, World!"
                    
                    - name: Install packages
                      package:
                        name:
                          - git
                          - curl
                          - vim
                        state: present
                    
                    - name: Create directory
                      file:
                        path: /opt/app
                        state: directory
                        mode: '0755'
                """.trimIndent()

            Then("should parse different module types") {
                val result = parser.parse(content)

                result.shouldBeSuccess()
                val playbook = result.getOrNull()!!

                playbook.tasks shouldHaveSize 3

                val debugTask = playbook.tasks[0]
                debugTask.module shouldBe "debug"
                debugTask.args["msg"] shouldBe "Hello, World!"

                val packageTask = playbook.tasks[1]
                packageTask.module shouldBe "package"
                packageTask.args["name"] shouldBe "git,curl,vim"

                val fileTask = playbook.tasks[2]
                fileTask.module shouldBe "file"
                fileTask.args["path"] shouldBe "/opt/app"
                fileTask.args["mode"] shouldBe "0755"
            }
        }

        When("parsing malformed YAML") {
            val content =
                """
                ---
                - name: Bad YAML
                  hosts: web
                 tasks:  # Wrong indentation
                    - name: Task
                      debug:
                        msg: "test"
                """.trimIndent()

            Then("should return failure") {
                val result = parser.parse(content)

                result.shouldBeFailure()
                result.exceptionOrNull()?.message.shouldNotBeNull()
            }
        }

        When("parsing non-playbook YAML") {
            val content =
                """
                ---
                key: value
                another_key: another_value
                """.trimIndent()

            Then("should return failure") {
                val result = parser.parse(content)

                result.shouldBeFailure()
                result.exceptionOrNull()?.message shouldContain "must have a 'name' field"
            }
        }

        When("parsing empty YAML") {
            val content = "---"

            Then("should return failure") {
                val result = parser.parse(content)

                result.shouldBeFailure()
            }
        }

        When("parsing playbook without name") {
            val content =
                """
                ---
                - hosts: web
                  tasks:
                    - debug:
                        msg: "No name"
                """.trimIndent()

            Then("should return failure") {
                val result = parser.parse(content)

                result.shouldBeFailure()
                result.exceptionOrNull()?.message shouldContain "must have a 'name' field"
            }
        }

        When("parsing playbook without hosts") {
            val content =
                """
                ---
                - name: No hosts
                  tasks:
                    - debug:
                        msg: "No hosts"
                """.trimIndent()

            Then("should return failure") {
                val result = parser.parse(content)

                result.shouldBeFailure()
                result.exceptionOrNull()?.message shouldContain "must have a 'hosts' field"
            }
        }

        When("parsing from file") {
            val testFile = File("src/test/resources/playbooks/simple.yml")

            Then("should read and parse file content") {
                val result = parser.parseFile(testFile.absolutePath)

                result.shouldBeSuccess()
                val playbook = result.getOrNull()!!

                playbook.name shouldBe "Simple playbook"
                playbook.hosts shouldBe "web"
                playbook.path shouldBe testFile.absolutePath
            }
        }

        When("parsing complex playbook from file") {
            val testFile = File("src/test/resources/playbooks/complex.yml")

            Then("should extract essential information") {
                val result = parser.parseFile(testFile.absolutePath)

                result.shouldBeSuccess()
                val playbook = result.getOrNull()!!

                playbook.name shouldBe "Complex deployment playbook"
                playbook.hosts shouldBe "production"
                playbook.tasks.isNotEmpty() shouldBe true

                // Should have parsed at least the git task
                val gitTask = playbook.tasks.find { it.module == "git" }
                gitTask.shouldNotBeNull()
                gitTask.args shouldContainKey "repo"
                gitTask.args shouldContainKey "dest"
            }
        }
    }
})
