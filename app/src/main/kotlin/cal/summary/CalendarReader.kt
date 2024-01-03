package cal.summary

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.component.VEvent
import java.io.InputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object CalendarReader {
    fun read(
        inputStream: InputStream,
        mapping: List<MaterializedEventType>,
        dateRange: ClosedRange<LocalDate>,
    ): Map<String, List<CalendarEvent>> {
        val cal = CalendarBuilder().build(inputStream)
        val events = cal.components.filterIsInstance<VEvent>()
        val eventsByType =
            mapping.associate {
                it.name to mutableListOf<CalendarEvent>()
            }.toMutableMap()
        val localZone = ZoneId.systemDefault()
        val rangeStart = dateRange.start.atStartOfDay()
        val rangeEndExclusive = dateRange.endInclusive.plusDays(1).atStartOfDay()

        events.forEach { event ->
            val maybeMatch =
                mapping.asSequence().mapNotNull {
                    val title = event.summary?.value
                    if (title != null && it.matcher.matches(title)) {
                        val start = event.startDate.date.toInstant().atZone(localZone).toLocalDateTime()
                        val end = event.endDate.date.toInstant().atZone(localZone).toLocalDateTime()
                        if (start >= rangeStart && end < rangeEndExclusive) {
                            it.name to CalendarEvent(title, start, end)
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

    fun calculateSummary(
        calendarData: Map<String, List<CalendarEvent>>,
        mapping: List<MaterializedEventType>,
    ): Summary {
        val years = calendarData.values.asSequence().flatten().map { it.start.year }.toSet()
        val yearlySummaries =
            years.map { year ->
                YearlySummary(
                    year = year,
                    byType =
                        mapping.associate { (name, _) ->
                            val calData = calendarData[name] ?: listOf()
                            val forThisYear = calData.filter { it.start.year == year }
                            val total = forThisYear.fold(Duration.ZERO) { acc, data -> acc + data.duration }
                            val count = forThisYear.size
                            val first =
                                calData.fold(null as LocalDateTime?) { acc, data ->
                                    if (acc == null || acc.isAfter(data.start)) {
                                        data.start
                                    } else {
                                        acc
                                    }
                                }
                            val last =
                                calData.fold(null as LocalDateTime?) { acc, data ->
                                    if (acc == null || acc.isBefore(data.end)) {
                                        data.end
                                    } else {
                                        acc
                                    }
                                }
                            name to
                                TypeSummary(
                                    type = name,
                                    total = total,
                                    count = count,
                                    firstEvent = first,
                                    lastEvent = last,
                                )
                        },
                )
            }
        return Summary(years = yearlySummaries, keys = mapping.map { it.name })
    }

    data class Summary(val years: List<YearlySummary>, val keys: List<String>) {
        val grandSummary: List<TypeSummary> by lazy {
            val byType = keys.associateWith { TypeSummary(it, Duration.ZERO, 0, null, null) }.toMutableMap()
            years.forEach { yearlySummary ->
                yearlySummary.byType.entries.forEach { (type, data) ->
                    val old = byType[type]!!
                    val newFirst =
                        if (old.firstEvent == null || (data.firstEvent != null && data.firstEvent < old.firstEvent)) {
                            data.firstEvent
                        } else {
                            old.firstEvent
                        }
                    val newLast =
                        if (old.lastEvent == null || (data.lastEvent != null && data.lastEvent > old.lastEvent)) {
                            data.lastEvent
                        } else {
                            old.lastEvent
                        }
                    byType[type] =
                        old.copy(
                            type = type,
                            total = old.total + data.total,
                            count = old.count + data.count,
                            firstEvent = newFirst,
                            lastEvent = newLast,
                        )
                }
            }
            keys.map { byType[it]!! }
        }
    }

    data class YearlySummary(val year: Int, val byType: Map<String, TypeSummary>)

    data class TypeSummary(
        val type: String,
        val total: Duration,
        val count: Int,
        val firstEvent: LocalDateTime?,
        val lastEvent: LocalDateTime?,
    ) {
        val prettyHours =
            String.format("%d h %d min", total.seconds / (TimeUnit.HOURS.toSeconds(1)), total.toSecondsPart())
    }
}
