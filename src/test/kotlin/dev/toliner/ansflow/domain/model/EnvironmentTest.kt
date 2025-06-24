package dev.toliner.ansflow.domain.model

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EnvironmentTest : BehaviorSpec({
    Given("Environment enum") {
        When("accessing enum values") {
            Then("should have DEVELOPMENT value") {
                Environment.DEVELOPMENT shouldNotBe null
                Environment.DEVELOPMENT.name shouldBe "DEVELOPMENT"
            }

            Then("should have PRODUCTION value") {
                Environment.PRODUCTION shouldNotBe null
                Environment.PRODUCTION.name shouldBe "PRODUCTION"
            }

            Then("should have exactly 2 values") {
                Environment.values().size shouldBe 2
            }
        }

        When("converting from string") {
            Then("should parse valid values") {
                Environment.valueOf("DEVELOPMENT") shouldBe Environment.DEVELOPMENT
                Environment.valueOf("PRODUCTION") shouldBe Environment.PRODUCTION
            }
        }
    }
})
