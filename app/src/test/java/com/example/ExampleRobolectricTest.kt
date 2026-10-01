package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CalendarEventData
import com.example.parser.GermanVoiceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VocoPlan", appName)
    }

    @Test
    fun `test german parser extracting event time and location`() {
        val input = "Morgen um 15 Uhr Treffen mit Max im Café Central"
        val parsed = GermanVoiceParser.parse(input)

        assertEquals("Treffen mit Max", parsed.title)
        assertEquals("Café Central", parsed.location)
        assertNotNull(parsed.dateTime)
        assertEquals(15, parsed.dateTime?.hour)
    }

    @Test
    fun `test default example values`() {
        val example = CalendarEventData.DEFAULT_EXAMPLE
        assertEquals("Termin", example.title)
        assertEquals("Stadt", example.location)
        assertEquals(15, example.dateTime?.hour)
        assertEquals("1 Std. vorher", example.reminderLabel)
    }
}
