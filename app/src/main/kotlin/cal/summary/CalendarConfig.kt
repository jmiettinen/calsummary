package cal.summary

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

private const val KOTLIN_COMPILER_DISAPPOINTED = "WHY IS THIS NEEDED? CANNOT COMPILER KNOW THAT T CAN BE OF TWO TYPES?"

data class CalendarConfig<T: GeneralEventType>(
    val mapping: Map<String, T>
) {

    fun toSerializable(): CalendarConfig<SerializedEventType> = CalendarConfig(
        mapping = this.mapping.mapValues {
            val stringForm = when (val value = it.value) {
                is SerializedEventType -> value.matcherRegex
                is MaterializedEventType -> value.matcher.toString()
                else -> KOTLIN_COMPILER_DISAPPOINTED
            }
            SerializedEventType(stringForm)
        }
    )

    fun toProper(): CalendarConfig<MaterializedEventType> = CalendarConfig(
        mapping = this.mapping.mapValues {
            val tmp = when(val value = it.value) {
                is SerializedEventType -> Regex(value.matcherRegex)
                is MaterializedEventType -> value.matcher

                else -> Regex(KOTLIN_COMPILER_DISAPPOINTED)
            }
            MaterializedEventType(tmp)
        }
    )

}

sealed interface GeneralEventType
data class SerializedEventType(val matcherRegex: String): GeneralEventType
data class MaterializedEventType(val matcher: Regex): GeneralEventType


data class CalendarEvent(val name: String, val start: LocalDateTime, val end: LocalDateTime) {

    val duration: Duration by lazy {
        Duration.between(start, end)
    }

}