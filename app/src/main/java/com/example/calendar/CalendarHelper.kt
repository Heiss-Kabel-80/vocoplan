package com.example.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.model.CalendarEventData
import java.time.ZoneId
import java.util.TimeZone

sealed class CalendarOperationResult {
    data class Success(val message: String) : CalendarOperationResult()
    data class LaunchedIntent(val message: String) : CalendarOperationResult()
    data class Error(val message: String) : CalendarOperationResult()
}

object CalendarHelper {

    fun hasCalendarPermission(context: Context): Boolean {
        val write = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val read = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return write && read
    }

    /**
     * Saves to the default device calendar.
     */
    fun saveToDefaultCalendar(context: Context, event: CalendarEventData): CalendarOperationResult {
        if (!hasCalendarPermission(context)) {
            // Fallback to calendar insert intent if permission not granted
            return launchCalendarInsertIntent(context, event, targetGoogle = false)
        }

        return try {
            val calendarId = findDefaultCalendarId(context)
            if (calendarId == null) {
                // If no writable calendar found directly, fallback to Intent
                launchCalendarInsertIntent(context, event, targetGoogle = false)
            } else {
                insertEventIntoCalendar(context, calendarId, event)
                CalendarOperationResult.Success("Termin erfolgreich im Standard-Kalender gespeichert")
            }
        } catch (e: Exception) {
            launchCalendarInsertIntent(context, event, targetGoogle = false)
        }
    }

    /**
     * Saves specifically to the Google Calendar account on the device.
     */
    fun saveToGoogleCalendar(context: Context, event: CalendarEventData): CalendarOperationResult {
        if (!hasCalendarPermission(context)) {
            return launchCalendarInsertIntent(context, event, targetGoogle = true)
        }

        return try {
            val googleCalendarId = findGoogleCalendarId(context)
            if (googleCalendarId != null) {
                insertEventIntoCalendar(context, googleCalendarId, event)
                CalendarOperationResult.Success("Termin im Google Kalender gespeichert")
            } else {
                // Not found directly via ContentResolver, launch Google Calendar app directly
                launchCalendarInsertIntent(context, event, targetGoogle = true)
            }
        } catch (e: Exception) {
            launchCalendarInsertIntent(context, event, targetGoogle = true)
        }
    }

    private fun findDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val uri: Uri = CalendarContract.Calendars.CONTENT_URI
        var primaryId: Long? = null
        var firstWritableId: Long? = null

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null
        )

        cursor?.use {
            val idCol = it.getColumnIndex(CalendarContract.Calendars._ID)
            val primaryCol = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            val accessCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val isPrimary = if (primaryCol >= 0) it.getInt(primaryCol) == 1 else false
                val access = if (accessCol >= 0) it.getInt(accessCol) else 0

                // CALENDAR_ACCESS_LEVEL: 500=CONTRIBUTOR, 700=ROOT, 600=EDITOR, 800=OWNER
                if (access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    if (firstWritableId == null) firstWritableId = id
                    if (isPrimary) {
                        primaryId = id
                        break
                    }
                }
            }
        }

        return primaryId ?: firstWritableId
    }

    private fun findGoogleCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val uri: Uri = CalendarContract.Calendars.CONTENT_URI

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? OR ${CalendarContract.Calendars.ACCOUNT_NAME} LIKE ?",
            arrayOf("com.google", "%@gmail.com"),
            null
        )

        cursor?.use {
            val idCol = it.getColumnIndex(CalendarContract.Calendars._ID)
            val accessCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val access = if (accessCol >= 0) it.getInt(accessCol) else 0
                if (access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    return id
                }
            }
        }

        return null
    }

    private fun insertEventIntoCalendar(
        context: Context,
        calendarId: Long,
        event: CalendarEventData
    ): Long {
        val cr: ContentResolver = context.contentResolver
        val startMillis = event.dateTime
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
            ?: System.currentTimeMillis()
        val endMillis = startMillis + (60 * 60 * 1000) // Default 1 hour duration

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, event.title.ifBlank { "Termin" })
            put(
                CalendarContract.Events.DESCRIPTION,
                if (event.notes.isNotBlank()) "${event.notes}\n\n(Erstellt mit VocoPlan)" else "(Erstellt mit VocoPlan)"
            )
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (event.location.isNotBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
            }
        }

        val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw IllegalStateException("Ereignis konnte nicht eingefügt werden")

        val eventId = ContentUris.parseId(uri)

        // Insert reminder if set
        if (event.reminderMinutes > 0) {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.MINUTES, event.reminderMinutes)
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            cr.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        }

        return eventId
    }

    fun launchCalendarInsertIntent(
        context: Context,
        event: CalendarEventData,
        targetGoogle: Boolean
    ): CalendarOperationResult {
        val startMillis = event.dateTime
            ?.atZone(ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
            ?: (System.currentTimeMillis() + 3600000)
        val endMillis = startMillis + 3600000

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(CalendarContract.Events.TITLE, event.title.ifBlank { "Termin" })
            if (event.location.isNotBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            }
            if (event.notes.isNotBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, event.notes)
            }
            if (targetGoogle) {
                // Explicitly target Google Calendar app if available
                setPackage("com.google.android.calendar")
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            if (targetGoogle) {
                CalendarOperationResult.LaunchedIntent("In Google Kalender geöffnet")
            } else {
                CalendarOperationResult.LaunchedIntent("Im Kalender geöffnet")
            }
        } catch (_: Exception) {
            if (targetGoogle) {
                // If Google Calendar package not found, launch generic
                val fallbackIntent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                    putExtra(CalendarContract.Events.TITLE, event.title.ifBlank { "Termin" })
                    if (event.location.isNotBlank()) {
                        putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
                    }
                    if (event.notes.isNotBlank()) {
                        putExtra(CalendarContract.Events.DESCRIPTION, event.notes)
                    }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(fallbackIntent)
                    CalendarOperationResult.LaunchedIntent("Im Kalender geöffnet (Google Kalender App nicht installiert)")
                } catch (e2: Exception) {
                    CalendarOperationResult.Error("Keine Kalender-App auf dem Gerät gefunden")
                }
            } else {
                CalendarOperationResult.Error("Keine Kalender-App auf dem Gerät gefunden")
            }
        }
    }
}
