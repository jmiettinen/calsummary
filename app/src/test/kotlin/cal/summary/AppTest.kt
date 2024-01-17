package cal.summary

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppTest {
    @Test
    fun `Matchers drop preparation sessions`() {
        val preps = listOf("Interview preparation", "Interview prep")
        val interviewMatch = defaultConfig.first()
        interviewMatch.name.shouldBe("interview")

        preps.filter { interviewMatch.matcher.matches(it) }.shouldBeEmpty()
    }
}
