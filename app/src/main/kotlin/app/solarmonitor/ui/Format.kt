package app.solarmonitor.ui

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display formatting. Locale.US keeps the decimal point stable regardless of phone locale. */
object Format {
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun kw(value: Double): String = String.format(Locale.US, "%.1f kW", value)

    /** kWh with one decimal under 100, none above; MWh with one decimal from 10,000 kWh when [allowMwh]. */
    fun kwh(value: Double, allowMwh: Boolean = false): String = when {
        allowMwh && value >= 10_000.0 -> String.format(Locale.US, "%.1f MWh", value / 1000.0)
        value >= 100.0 -> String.format(Locale.US, "%.0f kWh", value)
        else -> String.format(Locale.US, "%.1f kWh", value)
    }

    /** "60% of 10.3 kW", or empty when the nominal power is unknown. */
    fun percent(part: Double, whole: Double): String =
        if (whole <= 0.0) "" else String.format(Locale.US, "%.0f%% of %.1f kW", part / whole * 100.0, whole)

    fun hhmm(ts: LocalDateTime): String = ts.format(HHMM)
}
