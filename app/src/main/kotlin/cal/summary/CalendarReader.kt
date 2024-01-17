package cal.summary

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.component.VEvent
import java.io.InputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object CalendarReader {
    fun readEvents(
        inputStream: InputStream,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Sequence<CalendarEvent> {
        val cal = CalendarBuilder().build(inputStream)
        val zoneId = timeZone.toZoneId()
        return cal.components.asSequence()
            .filterIsInstance<VEvent>()
            .map { event ->
                val start = event.startDate.date.toInstant().atZone(zoneId).toLocalDateTime()
                val end = event.endDate.date.toInstant().atZone(zoneId).toLocalDateTime()
                CalendarEvent(event.summary?.value ?: "", start, end)
            }
    }

    fun read(
        events: Sequence<CalendarEvent>,
        mapping: List<MaterializedEventType>,
        dateRange: ClosedRange<LocalDate>,
    ): Map<String, List<CalendarEvent>> {
        val eventsByType =
            mapping.associate {
                it.name to mutableMapOf<ClosedRange<LocalDateTime>, CalendarEvent>()
            }.toMutableMap()
        val dateTimeRange = dateRange.start.atStartOfDay()..dateRange.endInclusive.plusDays(1).atStartOfDay().minusNanos(1)

        events.forEach { event ->
            val maybeMatch =
                mapping.asSequence().mapNotNull {
                    val title = event.name
                    if (it.matcher.matches(title)) {
                        if (event.start in dateTimeRange && event.end in dateTimeRange) {
                            it.name to event
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }.firstOrNull()
            if (maybeMatch != null) {
                val (key, match) = maybeMatch
                val range = match.start..match.end
                // Only one per range
                eventsByType[key]!![range] = match
            }
        }
        return eventsByType.mapValues { (_, map) ->
            map.values.toList()
        }
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
