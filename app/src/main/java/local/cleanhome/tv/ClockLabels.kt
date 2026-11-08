package local.cleanhome.tv

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ClockLabels(val time: String, val date: String) {
    companion object {
        fun at(now: Long, locale: Locale = Locale.getDefault(), zone: TimeZone = TimeZone.getDefault()): ClockLabels {
            fun format(pattern: String) = SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(now))
            return ClockLabels(format("h:mm a"), format("EEEE, d MMMM"))
        }
    }
}
