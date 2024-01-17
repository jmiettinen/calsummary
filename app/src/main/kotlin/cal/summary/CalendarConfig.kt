package cal.summary

import java.time.Duration
import java.time.LocalDateTime

fun List<MaterializedEventType>.toSerializable() =
    this.map {
        SerializedEventType(it.name, it.matcher.toString())
    }

fun List<SerializedEventType>.toMaterialized() =
    this.map {
        MaterializedEventType(it.name, Regex(it.matcherRegex))
    }

sealed class GeneralEventType(open val name: String)

data class SerializedEventType(override val name: String, val matcherRegex: String) : GeneralEventType(name)

data class MaterializedEventType(override val name: String, val matcher: Regex) : GeneralEventType(name)

data class CalendarEvent(val name: String, val start: LocalDateTime, val end: LocalDateTime, val attendees: List<String>) {
    val duration: Duration by lazy {
        Duration.between(start, end)
    }
}
