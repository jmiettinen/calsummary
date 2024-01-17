/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package cal.summary

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarReaderTest {
    private val allTheTime = LocalDate.MIN.plusDays(5)..LocalDate.MAX.minusDays(5)

    @Test
    fun `Multiple bookings are counted only once`() {
        val commonPrefix = "INTERVIEW"
        val commonStart = LocalDateTime.of(2024, 2, 2, 14, 0, 0)
        val commonEnd =
            commonStart.let { d ->
                LocalDateTime.of(d.year, d.month, d.dayOfMonth, d.hour + 2, d.minute, d.second)
            }
        val events =
            listOf(
                CalendarEvent("${commonPrefix}_1", commonStart, commonEnd),
                CalendarEvent("${commonPrefix}_2", commonStart, commonEnd),
                CalendarEvent("${commonPrefix}_3", commonStart, commonEnd),
            )
        val eventTypes = listOf(MaterializedEventType(name = commonPrefix, matcher = Regex("$commonPrefix.*")))

        val res = CalendarReader.read(events.asSequence(), eventTypes, allTheTime)
        val resultEvents = res[eventTypes.first().name].shouldNotBeNull()
        resultEvents.size shouldBe 1
    }
}