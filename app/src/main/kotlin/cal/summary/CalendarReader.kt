package cal.summary

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.component.VEvent
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId

object CalendarReader {
    fun readCalendar(
        inputStream: InputStream,
        mapping: CalendarConfig<MaterializedEventType>,
        dateRange: ClosedRange<LocalDate>,
    ): Map<String, List<CalendarEvent>> {
        val cal = CalendarBuilder().build(inputStream)
        val events = cal.components.filterIsInstance<VEvent>()
        val eventsByType =
            mapping.mapping.keys.associateWith {
                mutableListOf<CalendarEvent>()
            }.toMutableMap()
        val localZone = ZoneId.systemDefault()
        val rangeStart = dateRange.start.atStartOfDay()
        val rangeEndExclusive = dateRange.endInclusive.plusDays(1).atStartOfDay()

        events.forEach { event ->
            val maybeMatch =
                mapping.mapping.entries.asSequence().mapNotNull {
                    val title = event.summary?.value
                    if (title != null && it.value.matcher.matches(title)) {
                        val start = event.startDate.date.toInstant().atZone(localZone).toLocalDateTime()
                        val end = event.endDate.date.toInstant().atZone(localZone).toLocalDateTime()
                        if (start >= rangeStart && end < rangeEndExclusive) {
                            it.key to CalendarEvent(title, start, end)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }.firstOrNull()
            if (maybeMatch != null) {
                val (key, match) = maybeMatch
                eventsByType[key]!!.add(match)
            }
        }
        return eventsByType
    }
}
