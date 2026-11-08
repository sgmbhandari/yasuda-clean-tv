package local.cleanhome.tv

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ClockLabelsTest {
    @Test fun usesTwelveHoursAndExplicitPeriod() {
        val zone = TimeZone.getTimeZone("Asia/Kathmandu")
        val calendar = Calendar.getInstance(zone).apply { set(2026, Calendar.NOVEMBER, 2, 19, 5, 0) }
        val labels = ClockLabels.at(calendar.timeInMillis, Locale.US, zone)
        assertEquals("7:05 PM", labels.time)
        assertEquals("Monday, 2 November", labels.date)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        assertEquals("12:05 AM", ClockLabels.at(calendar.timeInMillis, Locale.US, zone).time)
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        assertEquals("12:05 PM", ClockLabels.at(calendar.timeInMillis, Locale.US, zone).time)
    }
}
