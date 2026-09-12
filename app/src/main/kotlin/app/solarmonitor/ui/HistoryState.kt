package app.solarmonitor.ui

import app.solarmonitor.model.Point
import app.solarmonitor.time.Mode
import app.solarmonitor.time.Period
import java.time.LocalDate

/**
 * Pure state for the History screen: the requested period, what is currently
 * drawn, and the rules for navigation and bar drill-down. No Android classes,
 * so every rule is unit-tested.
 */
class HistoryState(today: LocalDate) {

    /** The period the screen wants to show. */
    var period: Period = Period.of(Mode.MONTH, today)
        private set

    /** The period whose data is actually drawn right now, or null before the first render. */
    var shownPeriod: Period? = null
        private set

    private var shownPoints: List<Point> = emptyList()
    private var shownToday: LocalDate = today

    /** Switches mode. Returns true when the period changed and a load is needed. */
    fun selectMode(mode: Mode, today: LocalDate): Boolean {
        if (mode == period.mode) return false
        period = period.switchMode(mode, today)
        return true
    }

    /** Jumps straight to the period of [mode] containing [day] (clamped to today). Returns true when it changed. */
    fun jumpTo(mode: Mode, day: LocalDate, today: LocalDate): Boolean {
        val target = Period.of(mode, if (day.isAfter(today)) today else day)
        if (target == period) return false
        period = target
        return true
    }

    fun prev(): Boolean {
        if (!period.canGoPrev()) return false
        period = period.prev()
        return true
    }

    fun next(today: LocalDate): Boolean {
        if (!period.canGoNext(today)) return false
        period = period.next()
        return true
    }

    /** True when a result for [requested] still belongs to the period on screen. */
    fun isCurrentRequest(requested: Period): Boolean = requested == period

    /** Records what the chart now draws. Call with the full series from the API, future entries included. */
    fun rendered(period: Period, points: List<Point>, today: LocalDate) {
        shownPeriod = period
        shownPoints = points
        shownToday = today
    }

    /** One slot per point; null where the date is in the future so the slot stays empty but keeps its width. */
    fun barValues(today: LocalDate): List<Double?> =
        shownPoints.map { if (it.ts.toLocalDate().isAfter(today)) null else it.value }

    fun visibleTotal(today: LocalDate): Double = barValues(today).filterNotNull().sum()

    /** Index of the bar for today's day/month/year when the shown period is the present one, else -1. */
    fun highlightIndex(today: LocalDate): Int {
        val shown = shownPeriod ?: return -1
        if (!shown.isCurrent(today)) return -1
        return when (shown.mode) {
            Mode.MONTH -> shownPoints.indexOfFirst { it.ts.toLocalDate() == today }
            Mode.YEAR -> shownPoints.indexOfFirst { it.ts.year == today.year && it.ts.monthValue == today.monthValue }
            Mode.TOTAL -> shownPoints.indexOfFirst { it.ts.year == today.year }
            Mode.DAY -> -1
        }
    }

    /**
     * The period one level down for a tapped bar, or null when the tap must be
     * ignored: nothing drawn yet, the drawn data belongs to another period (still
     * loading), the bar is in the future, or we are already in Day mode.
     */
    fun drillTarget(index: Int): Period? {
        if (shownPeriod != period || period.mode == Mode.DAY) return null
        val point = shownPoints.getOrNull(index) ?: return null
        if (point.ts.toLocalDate().isAfter(shownToday)) return null
        return period.drillInto(point.ts)
    }

    /** Applies [drillTarget]; returns true when the period changed. */
    fun drill(index: Int): Boolean {
        val target = drillTarget(index) ?: return false
        period = target
        return true
    }
}
