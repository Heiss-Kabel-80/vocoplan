package com.example.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CalendarEventData(
    val title: String = "",
    val dateTime: LocalDateTime? = null,
    val location: String = "",
    val notes: String = "",
    val reminderMinutes: Int = 60, // default 1 hour
    val reminderLabel: String = "1 Std. vorher"
) {
    /**
     * Formats the event into the structured text format for the central display window.
     */
    fun toDisplayText(): String {
        val dateStr = if (dateTime != null) {
            val formatter = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy 'um' HH:mm 'Uhr'", Locale.GERMAN)
            dateTime.format(formatter)
        } else {
            ""
        }

        val titleStr = title
        val locationStr = location
        val notesStr = notes
        val reminderStr = reminderLabel.ifBlank {
            if (reminderMinutes > 0) "$reminderMinutes Min. vorher" else ""
        }

        return """
            Anlass: $titleStr
            Zeitpunkt: $dateStr
            Ort: $locationStr
            Notizen: $notesStr
            Erinnerungszeit: $reminderStr
        """.trimIndent()
    }

    companion object {
        val DEFAULT_EXAMPLE: CalendarEventData
            get() {
                val now = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0)
                val targetTime = now.withHour(15).let {
                    if (it.isBefore(LocalDateTime.now())) it.plusDays(1) else it
                }
                return CalendarEventData(
                    title = "Termin",
                    dateTime = targetTime,
                    location = "Stadt",
                    notes = "Keine Notizen",
                    reminderMinutes = 60,
                    reminderLabel = "1 Std. vorher"
                )
            }

        /**
         * Parses text manually edited in the central window back into structured data.
         */
        fun fromDisplayText(text: String, currentData: CalendarEventData): CalendarEventData {
            var title = currentData.title
            var location = currentData.location
            var notes = currentData.notes
            var reminderLabel = currentData.reminderLabel
            var reminderMinutes = currentData.reminderMinutes
            var parsedDateTime = currentData.dateTime

            text.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("Anlass:", ignoreCase = true) -> {
                        title = trimmed.substringAfter(":", "").trim()
                    }
                    trimmed.startsWith("Ort:", ignoreCase = true) -> {
                        location = trimmed.substringAfter(":", "").trim()
                    }
                    trimmed.startsWith("Notizen:", ignoreCase = true) -> {
                        notes = trimmed.substringAfter(":", "").trim()
                    }
                    trimmed.startsWith("Erinnerungszeit:", ignoreCase = true) ||
                    trimmed.startsWith("Erinnerung:", ignoreCase = true) -> {
                        val rem = trimmed.substringAfter(":", "").trim()
                        if (rem.isNotBlank()) {
                            reminderLabel = rem
                        }
                    }
                    trimmed.startsWith("Zeitpunkt:", ignoreCase = true) -> {
                        val timeRaw = trimmed.substringAfter(":", "").trim()
                        // Attempt to extract time if modified
                        val parsed = com.example.parser.GermanVoiceParser.parseDateTimeString(timeRaw)
                        if (parsed != null) {
                            parsedDateTime = parsed
                        }
                    }
                }
            }

            return currentData.copy(
                title = title,
                location = location,
                notes = notes,
                reminderLabel = reminderLabel,
                reminderMinutes = reminderMinutes,
                dateTime = parsedDateTime
            )
        }
    }
}
