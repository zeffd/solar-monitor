package app.solarmonitor.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Mode { DAY, MONTH, YEAR, TOTAL }

/**
 * A history period. [date] is the day for DAY, the first of the month for
 * MONTH, January 1st for YEAR, and January 1st of some year for TOTAL (unused).
 * Build with [Period.of] so the date is normalized.
 */
data class Period(val mode: Mode, val date: LocalDate) {

    fun prev(): Period = when (mode) {
        Mode.DAY -> copy(date = date.minusDays(1))
        Mode.MONTH -> copy(date = date.minusMonths(1))
        Mode.YEAR -> copy(date = date.minusYears(1))
        Mode.TOTAL -> this
    }

    fun next(): Period = when (mode) {
        Mode.DAY -> copy(date = date.plusDays(1))
        Mode.MONTH -> copy(date = date.plusMonths(1))
        Mode.YEAR -> copy(date = date.plusYears(1))
        Mode.TOTAL -> this
    }

    fun isCurrent(today: LocalDate): Boolean = when (mode) {
        Mode.DAY -> date == today
        Mode.MONTH -> YearMonth.from(date) == YearMonth.from(today)
        Mode.YEAR -> date.year == today.year
        Mode.TOTAL -> true
    }

    fun canGoPrev(): Boolean = mode != Mode.TOTAL

    fun canGoNext(today: LocalDate): Boolean = mode != Mode.TOTAL && !isCurrent(today) && date.isBefore(today)

    fun label(): String = when (mode) {
        Mode.DAY -> date.format(DAY_FMT)
        Mode.MONTH -> date.format(MONTH_FMT)
        Mode.YEAR -> date.year.toString()
        Mode.TOTAL -> "All years"
    }

    /** First day after this period, or null for TOTAL which never ends. */
    fun endExclusive(): LocalDate? = when (mode) {
        Mode.DAY -> date.plusDays(1)
        Mode.MONTH -> date.plusMonths(1)
        Mode.YEAR -> date.plusYears(1)
        Mode.TOTAL -> null
    }

    /**
     * True when data saved at [savedAtMillis] can be trusted to cover the whole
     * period: the period had ended in the plant's zone, plus a grace hour for the
     * server to publish its last samples. Data cached mid-period is incomplete.
     */
    fun isCompleteAt(savedAtMillis: Long, zone: ZoneOffset, graceMillis: Long = COMPLETE_GRACE_MS): Boolean {
        val end = endExclusive() ?: return false
        val endMillis = end.atStartOfDay().toInstant(zone).toEpochMilli()
        return savedAtMillis >= endMillis + graceMillis
    }

    fun cacheKey(): String = when (mode) {
        Mode.DAY -> "curve:$date"
        Mode.MONTH -> "month:${YearMonth.from(date)}"
        Mode.YEAR -> "year:${date.year}"
        Mode.TOTAL -> "years"
    }

    /** The period one level down that contains a tapped bar's timestamp, or null in DAY mode. */
    fun drillInto(ts: LocalDateTime): Period? = when (mode) {
        Mode.MONTH -> of(Mode.DAY, ts.toLocalDate())
        Mode.YEAR -> of(Mode.MONTH, ts.toLocalDate())
        Mode.TOTAL -> of(Mode.YEAR, ts.toLocalDate())
        Mode.DAY -> null
    }

    /**
     * The same point in time viewed in another mode. From the present period
     * (or a future one) this jumps to the present period of the new mode, so
     * "this month" -> Day shows today rather than the 1st.
     */
    fun switchMode(newMode: Mode, today: LocalDate): Period =
        if (isCurrent(today) || date.isAfter(today)) of(newMode, today) else of(newMode, date)

    companion object {
        const val COMPLETE_GRACE_MS = 3_600_000L
        private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
        private val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

        fun of(mode: Mode, day: LocalDate): Period = when (mode) {
            Mode.DAY -> Period(Mode.DAY, day)
            Mode.MONTH -> Period(Mode.MONTH, day.withDayOfMonth(1))
            Mode.YEAR -> Period(Mode.YEAR, day.withDayOfYear(1))
            Mode.TOTAL -> Period(Mode.TOTAL, day.withDayOfYear(1))
        }
    }
}

/** All plant-related dates use the plant's fixed UTC offset from the plant record, never the phone's zone. */
object PlantTime {
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun zone(offsetSec: Int): ZoneOffset = ZoneOffset.ofTotalSeconds(offsetSec)

    fun today(zone: ZoneOffset, nowMillis: Long): LocalDate =
        Instant.ofEpochMilli(nowMillis).atOffset(zone).toLocalDate()

    fun hhmm(epochMillis: Long, zone: ZoneOffset): String =
        Instant.ofEpochMilli(epochMillis).atOffset(zone).format(HHMM)
}
