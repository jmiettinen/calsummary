package cal.summary

import cal.summary.CalendarReader.prettyHours
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.time.LocalDate

private val objectMapper by lazy {
    jacksonObjectMapper()
        .apply {
        }
}

private val defaultConfig =
    listOf(
        MaterializedEventType(
            "interview",
            Regex("(?!HOLD!).*[iI]nterview.*"),
        ),
        MaterializedEventType(
            "debrief",
            Regex(".*[dD]ebrief.*"),
        ),
    )

private val configHelp =
    """
    Here default config file is
    ${jacksonObjectMapper().writeValueAsString(defaultConfig.toSerializable())}
    """.trimIndent()

class App : CliktCommand(epilog = configHelp) {
    private val now = LocalDate.now()
    private val aLotEarlier = LocalDate.MIN

    private val calendarFile: File by argument().file(mustExist = true, mustBeReadable = true).help("Calendar to read")
    private val listAllEvents: Boolean by option("--show-all").flag().help("Print all matching events")

    private val config: List<MaterializedEventType> by option(
        "-c",
    ).file(mustExist = true, mustBeReadable = true).help("Config file for mapping").transformAll {
            files ->
        files.firstNotNullOfOrNull { file ->
            val read: List<MaterializedEventType>? =
                try {
                    file.inputStream().bufferedReader().use {
                        objectMapper.readValue<List<SerializedEventType>>(it).toMaterialized()
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

    private fun printSummary(calendarData: Map<String, List<CalendarEvent>>) {
        val summary = CalendarReader.calculateSummary(calendarData, config)
        val keyToIndex = config.mapIndexed { i, v -> v.name to i }.toMap()
        summary.years.sortedBy { it.year }.forEach { yearlySummary ->
            println("${yearlySummary.year}:")
            yearlySummary.byType.entries.sortedBy { keyToIndex[it.key] }.forEach { (_, summary) ->
                println("${summary.type}: ${summary.prettyHours} (${summary.count} entries)")
            }
        }
        println("Total:")
        summary.grandSummary.sortedBy { keyToIndex[it.type] }.forEach { typeSummary ->
            println("${typeSummary.type}: ${typeSummary.prettyHours} (${typeSummary.count} entries)")
        }
    }

    private fun printFull(calendarData: Map<String, List<CalendarEvent>>) {
        calendarData.forEach { name, events ->
            val sorted = events.sortedWith(Comparator.comparing(CalendarEvent::start).then(Comparator.comparing(CalendarEvent::duration)))
            println("Events of '$name'")
            sorted.forEach { e ->
                println("${e.start}: ${e.name} (${e.duration.prettyHours()})")
            }
        }
    }

    override fun run() {
        calendarFile.inputStream().buffered().use { calendar ->
            val dateRange = fromDate..toDate
            val eventStream = CalendarReader.readEvents(calendar)
            val calendarData = CalendarReader.read(eventStream, config, dateRange)
            if (listAllEvents) {
                printFull(calendarData)
            } else {
                printSummary(calendarData)
            }
        }
    }
}

fun main(args: Array<String>) {
    App().main(args)
}
