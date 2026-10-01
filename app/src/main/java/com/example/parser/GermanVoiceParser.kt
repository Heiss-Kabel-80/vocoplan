package com.example.parser

import com.example.model.CalendarEventData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

object GermanVoiceParser {

    private val MONTH_MAP = mapOf(
        "januar" to 1, "jan" to 1,
        "februar" to 2, "feb" to 2,
        "märz" to 3, "maerz" to 3, "mrz" to 3,
        "april" to 4, "apr" to 4,
        "mai" to 5,
        "juni" to 6, "jun" to 6,
        "juli" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "oktober" to 10, "okt" to 10,
        "november" to 11, "nov" to 11,
        "dezember" to 12, "dez" to 12
    )

    private val WEEKDAY_MAP = mapOf(
        "montag" to DayOfWeek.MONDAY,
        "dienstag" to DayOfWeek.TUESDAY,
        "mittwoch" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY,
        "freitag" to DayOfWeek.FRIDAY,
        "samstag" to DayOfWeek.SATURDAY,
        "sonnabend" to DayOfWeek.SATURDAY,
        "sonntag" to DayOfWeek.SUNDAY
    )

    private val NUMBER_WORDS = mapOf(
        "ein" to 1, "eine" to 1, "einer" to 1, "eins" to 1,
        "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5, "fuenf" to 5,
        "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10,
        "elf" to 11, "zwölf" to 12, "zwoelf" to 12,
        "dreizehn" to 13, "vierzehn" to 14, "fünfzehn" to 15, "sechzehn" to 16,
        "siebzehn" to 17, "achtzehn" to 18, "neunzehn" to 19, "zwanzig" to 20
    )

    fun parse(input: String, currentData: CalendarEventData? = null): CalendarEventData {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return currentData ?: CalendarEventData()
        }

        var workingText = trimmed
        var parsedReminderMinutes = currentData?.reminderMinutes ?: 60
        var parsedReminderLabel = currentData?.reminderLabel ?: "1 Std. vorher"

        // 1. Check for reminder phrase (e.g., "erinnere mich 2 stunden vorher")
        val reminderPattern = Pattern.compile(
            """(?i)(?:erinnere mich|erinnerung|mit erinnerung)\s+(\d+|eine|einer|ein|zwei|drei|vier|fünf)\s+(stunden?|std|tagen?|tage|wochen?|minuten?)\s*(?:vorher|zuvor)?""",
            Pattern.CASE_INSENSITIVE
        )
        val remMatcher = reminderPattern.matcher(workingText)
        if (remMatcher.find()) {
            val countStr = remMatcher.group(1)?.lowercase(Locale.GERMAN) ?: "1"
            val count = NUMBER_WORDS[countStr] ?: countStr.toIntOrNull() ?: 1
            val unit = remMatcher.group(2)?.lowercase(Locale.GERMAN) ?: "stunde"

            when {
                unit.startsWith("min") -> {
                    parsedReminderMinutes = count
                    parsedReminderLabel = "$count Min. vorher"
                }
                unit.startsWith("st") -> {
                    parsedReminderMinutes = count * 60
                    parsedReminderLabel = if (count == 1) "1 Std. vorher" else "$count Std. vorher"
                }
                unit.startsWith("tag") -> {
                    parsedReminderMinutes = count * 24 * 60
                    parsedReminderLabel = if (count == 1) "1 Tag vorher" else "$count Tage vorher"
                }
                unit.startsWith("woch") -> {
                    parsedReminderMinutes = count * 7 * 24 * 60
                    parsedReminderLabel = if (count == 1) "1 Woche vorher" else "$count Wochen vorher"
                }
            }
            workingText = workingText.removeRange(remMatcher.start(), remMatcher.end()).trim()
        }

        // 2. Parse Location (Ort) e.g., "im Büro", "in Berlin", "bei Dr. Müller", "im Café Central"
        var location = ""
        val locationPattern = Pattern.compile(
            """(?i)\b(?:in|im|bei|am|auf)\s+(?:der\s+|dem\s+|einem\s+|einer\s+)?([\p{L}0-9\.\-\s]+?)(?=(?:\s+\b(?:um|am|abends|morgens|nicht|bitte|erinnere)\b)|$)""",
            Pattern.CASE_INSENSITIVE
        )
        val locMatcher = locationPattern.matcher(workingText)
        if (locMatcher.find()) {
            val potentialLoc = locMatcher.group(1)?.trim() ?: ""
            // Filter out weekday names or date words captured by mistake
            val locLower = potentialLoc.lowercase(Locale.GERMAN)
            val isWeekdayOrDate = WEEKDAY_MAP.keys.any { locLower.startsWith(it) } ||
                    locLower in listOf("morgen", "übermorgen", "heute", "uhr", "mittag", "abend")

            if (!isWeekdayOrDate && potentialLoc.length in 2..40) {
                location = potentialLoc
                workingText = workingText.removeRange(locMatcher.start(), locMatcher.end()).trim()
            }
        }

        // 3. Parse Notes / Hints (Notizen) e.g., "nicht vergessen...", "Notiz: ..."
        var notes = ""
        val notesPattern = Pattern.compile(
            """(?i)(?:nicht vergessen|notiz:|hinweis:|mitbringen:|bitte beachten:)\s*(.+)""",
            Pattern.CASE_INSENSITIVE
        )
        val notesMatcher = notesPattern.matcher(workingText)
        if (notesMatcher.find()) {
            notes = notesMatcher.group(1)?.trim() ?: ""
            workingText = workingText.substring(0, notesMatcher.start()).trim()
        }

        // 4. Parse Date & Time
        val parsedDateTime = parseDateTimeFromText(workingText) ?: currentData?.dateTime

        // Clean up temporal words from title
        var title = workingText
            .replace(Regex("""(?i)\b(?:am\s+)?(?:montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)\b"""), "")
            .replace(Regex("""(?i)\b(?:heute|morgen|übermorgen|uebermorgen|nächste woche)\b"""), "")
            .replace(Regex("""(?i)\bum\s+\d{1,2}(?::\d{2})?\s*(?:uhr)?\b"""), "")
            .replace(Regex("""(?i)\b\d{1,2}(?::\d{2})?\s*uhr\b"""), "")
            .replace(Regex("""(?i)\b(?:halb|viertel\s+nach|viertel\s+vor)\s+(?:\d{1,2}|eins|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf)\b"""), "")
            .replace(Regex("""(?i)\b(?:vormittags|nachmittags|abends|morgens|mittags)\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', ';', '-')

        if (title.isBlank()) {
            title = currentData?.title?.ifBlank { "Termin" } ?: "Termin"
        }

        return CalendarEventData(
            title = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString() },
            dateTime = parsedDateTime,
            location = location,
            notes = notes,
            reminderMinutes = parsedReminderMinutes,
            reminderLabel = parsedReminderLabel
        )
    }

    private fun parseDateTimeFromText(text: String): LocalDateTime? {
        val lower = text.lowercase(Locale.GERMAN)
        val today = LocalDate.now()
        var targetDate: LocalDate = today

        // Relative days
        when {
            "übermorgen" in lower || "uebermorgen" in lower -> {
                targetDate = today.plusDays(2)
            }
            "morgen" in lower && "morgens" !in lower -> {
                targetDate = today.plusDays(1)
            }
            "heute" in lower || "heut" in lower -> {
                targetDate = today
            }
            else -> {
                // Check weekday
                var foundDay: DayOfWeek? = null
                for ((word, day) in WEEKDAY_MAP) {
                    if (word in lower) {
                        foundDay = day
                        break
                    }
                }
                if (foundDay != null) {
                    var date = today
                    while (date.dayOfWeek != foundDay) {
                        date = date.plusDays(1)
                    }
                    if (date == today && "nächsten" in lower) {
                        date = date.plusDays(7)
                    }
                    targetDate = date
                } else {
                    // Check absolute date pattern: e.g. "15. Oktober", "24.11.", "12.12.2026"
                    val dateRegex = Regex("""\b(\d{1,2})\.(?:\s*([A-Za-zäöüÄÖÜ]+)|\s*(\d{1,2}))(?:\.?\s*(\d{4}))?\b""")
                    val match = dateRegex.find(text)
                    if (match != null) {
                        val day = match.groupValues[1].toIntOrNull() ?: 1
                        val monthWord = match.groupValues[2].lowercase(Locale.GERMAN)
                        val monthNum = match.groupValues[3].toIntOrNull()
                        val yearNum = match.groupValues[4].toIntOrNull() ?: today.year

                        val month = if (monthNum != null) {
                            monthNum.coerceIn(1, 12)
                        } else {
                            MONTH_MAP[monthWord] ?: today.monthValue
                        }
                        try {
                            targetDate = LocalDate.of(yearNum, month, day.coerceIn(1, 31))
                        } catch (_: Exception) {
                            targetDate = today
                        }
                    }
                }
            }
        }

        // Time parsing
        var targetTime: LocalTime? = null

        // Pattern: "um 15:30", "um 15 Uhr", "15:00 Uhr", "15:30"
        val timePattern = Pattern.compile("""(?i)(?:um\s+)?(\d{1,2})(?::(\d{2}))?\s*(?:uhr)?""")
        val matcher = timePattern.matcher(text)
        var lastHour = -1
        var lastMinute = 0
        while (matcher.find()) {
            val h = matcher.group(1)?.toIntOrNull() ?: continue
            val m = matcher.group(2)?.toIntOrNull() ?: 0
            if (h in 0..23 && m in 0..59) {
                // If it matched a bare single digit without "um" or "uhr", check context
                val fullMatch = matcher.group(0)?.lowercase(Locale.GERMAN) ?: ""
                if ("uhr" in fullMatch || "um" in fullMatch || matcher.group(2) != null) {
                    lastHour = h
                    lastMinute = m
                }
            }
        }

        // Pattern for "halb 4" / "halb vier"
        val halbPattern = Pattern.compile("""(?i)\bhalb\s+(\d{1,2}|eins|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf)\b""")
        val halbMatcher = halbPattern.matcher(text)
        if (halbMatcher.find()) {
            val numStr = halbMatcher.group(1)?.lowercase(Locale.GERMAN) ?: ""
            val nextHour = NUMBER_WORDS[numStr] ?: numStr.toIntOrNull() ?: 4
            var hour = (nextHour - 1).coerceIn(0, 23)
            if (hour < 8 && ("nachmittag" in lower || "abend" in lower)) {
                hour += 12
            }
            lastHour = hour
            lastMinute = 30
        }

        // Pattern for "viertel nach / vor"
        val viertelPattern = Pattern.compile("""(?i)\bviertel\s+(nach|vor)\s+(\d{1,2}|eins|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf)\b""")
        val viertelMatcher = viertelPattern.matcher(text)
        if (viertelMatcher.find()) {
            val direction = viertelMatcher.group(1)?.lowercase(Locale.GERMAN)
            val numStr = viertelMatcher.group(2)?.lowercase(Locale.GERMAN) ?: ""
            val baseHour = NUMBER_WORDS[numStr] ?: numStr.toIntOrNull() ?: 12

            if (direction == "nach") {
                lastHour = baseHour
                lastMinute = 15
            } else {
                lastHour = (baseHour - 1).coerceIn(0, 23)
                lastMinute = 45
            }
        }

        // Adjust for "abends", "nachmittags" if hour < 12
        if (lastHour in 1..11) {
            if ("abends" in lower || "nachmittags" in lower || "nachmittag" in lower || "abend" in lower) {
                lastHour += 12
            }
        }

        if (lastHour >= 0) {
            targetTime = LocalTime.of(lastHour.coerceIn(0, 23), lastMinute.coerceIn(0, 59))
        } else {
            // General time of day defaults
            targetTime = when {
                "morgens" in lower || "früh" in lower -> LocalTime.of(9, 0)
                "mittags" in lower -> LocalTime.of(12, 0)
                "nachmittags" in lower -> LocalTime.of(15, 0)
                "abends" in lower -> LocalTime.of(18, 0)
                else -> LocalTime.of(15, 0) // Default 15:00 Uhr as specified in brief
            }
        }

        return LocalDateTime.of(targetDate, targetTime)
    }

    fun parseDateTimeString(input: String): LocalDateTime? {
        val trimmed = input.trim()
        if (trimmed.isBlank() || trimmed.equals("Nicht angegeben", ignoreCase = true)) {
            return null
        }
        return try {
            // Attempt standard German pattern: dd.MM.yyyy um HH:mm Uhr
            val clean = trimmed.replace(Regex("""^[A-Za-zäöüÄÖÜ]+,\s*"""), "") // Remove weekday prefix
            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'", Locale.GERMAN)
            LocalDateTime.parse(clean, formatter)
        } catch (_: Exception) {
            parseDateTimeFromText(trimmed)
        }
    }
}
