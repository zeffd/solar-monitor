package app.solarmonitor.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Derived figures for the dashboard tiles. Pure functions, unit-tested. */
object Stats {
    private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    /**
     * Average kWh per completed day this month. Today's partial total is excluded
     * so a morning reading does not drag the average down. Null on the 1st.
     */
    fun avgPerCompletedDay(monthKwh: Double, todayKwh: Double, today: LocalDate): Double? {
        val completed = today.dayOfMonth - 1
        if (completed <= 0) return null
        return ((monthKwh - todayKwh) / completed).coerceAtLeast(0.0)
    }

    /** Average kWh per completed month this year, excluding the current month. Null in January. */
    fun avgPerCompletedMonth(yearKwh: Double, monthKwh: Double, today: LocalDate): Double? {
        val completed = today.monthValue - 1
        if (completed <= 0) return null
        return ((yearKwh - monthKwh) / completed).coerceAtLeast(0.0)
    }

    /** "Nov 2024" for the lifetime tile, or null when the install date is unknown. */
    fun sinceLabel(installDate: LocalDate?): String? = installDate?.format(MONTH_YEAR)
}
