package cal.summary

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private val objectMapper by lazy {
    jacksonObjectMapper()
        .apply {
        }
}

private val defaultConfig =
    CalendarConfig(
        mapping =
            mapOf(
                "interview" to
                    MaterializedEventType(
                        Regex("(?!HOLD!).*[iI]nterview.*"),
                    ),
                "debrief" to
                    MaterializedEventType(
                        Regex(".*[dD]ebrief.*"),
                    ),
            ),
    )

private val configHelp =
    """
    Here default config file is
    ${jacksonObjectMapper().writeValueAsString(defaultConfig.toSerializable())}
    """.trimIndent()

class App : CliktCommand(epilog = configHelp) {
    private val now = LocalDate.now()
    private val aLotEarlier = now.minusYears(10)

    private val calendarFile: File by argument().file(mustExist = true, mustBeReadable = true).help("Calendar to read")
    private val configFile: CalendarConfig<MaterializedEventType> by option(
        "-c",
    ).file(mustExist = true, mustBeReadable = true).help("Config file for mapping").transformAll {
            files ->
        files.firstNotNullOfOrNull { file ->
            val read: CalendarConfig<MaterializedEventType>? =
                try {
                    file.inputStream().bufferedReader().use {
                        objectMapper.readValue<CalendarConfig<SerializedEventType>>(it).toProper()
                    }
                } catch (e: JacksonException) {
                    null
                }
            read
        } ?: defaultConfig
    }
    private val toDate: LocalDate by option("-f").help("From date").transformAll(
        defaultForHelp = now.toString(),
    ) {
        it.firstNotNullOfOrNull { maybeDate ->
            LocalDate.parse(maybeDate)
        } ?: now
    }

    private val fromDate: LocalDate by option("-t").help("To date").transformAll(
        defaultForHelp = aLotEarlier.toString(),
    ) {
        it.firstNotNullOfOrNull { maybeDate ->
            LocalDate.parse(maybeDate)
        } ?: aLotEarlier
    }

    override fun run() {
        calendarFile.inputStream().buffered().use { calendar ->
            val dateRange = fromDate..toDate
            val calendarData = CalendarReader.readCalendar(calendar, configFile, dateRange)
            configFile.mapping.forEach { (name, _) ->
                val calData = calendarData[name]
                val total =
                    calData?.fold(Duration.ZERO) { acc, data ->
                        acc.plus(data.duration)
                    } ?: Duration.ZERO
                val count = calData?.size ?: 0
                val first =
                    calData?.fold(null as LocalDateTime?) { acc, data ->
                        if (acc == null || acc.isAfter(data.start)) {
                            data.start
                        } else {
                            acc
                        }
                    }
                val last =
                    calData?.fold(null as LocalDateTime?) { acc, data ->
                        if (acc == null || acc.isBefore(data.end)) {
                            data.end
                        } else {
                            acc
                        }
                    }
                val prettyHours = String.format("%d h %d min", total.seconds / (TimeUnit.HOURS.toSeconds(1)), total.toSecondsPart())

                println("$name: $prettyHours ($count entries between $first ... $last)")
            }
        }
    }
}

fun main(args: Array<String>) {
    App().main(args)
}
