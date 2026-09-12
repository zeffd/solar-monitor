package app.solarmonitor.ui

import app.solarmonitor.model.Point
import app.solarmonitor.time.Mode
import app.solarmonitor.time.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HistoryStateTest {
    private val today = LocalDate.of(2026, 9, 8)

    private fun days(year: Int, month: Int, count: Int) =
        (1..count).map { Point(LocalDateTime.of(year, month, it, 0, 0), it.toDouble()) }

    private fun months(year: Int) =
        (1..12).map { Point(LocalDateTime.of(year, it, 1, 0, 0), it * 100.0) }

    private val years = listOf(2024, 2025, 2026).map { Point(LocalDateTime.of(it, 1, 1, 0, 0), 1000.0) }

    @Test
    fun startsOnCurrentMonth() {
        assertEquals(Period(Mode.MONTH, LocalDate.of(2026, 9, 1)), HistoryState(today).period)
    }

    @Test
    fun selectModeReportsWhetherAnythingChanged() {
        val s = HistoryState(today)
        assertFalse(s.selectMode(Mode.MONTH, today))
        assertTrue(s.selectMode(Mode.DAY, today))
        assertEquals(Period(Mode.DAY, today), s.period)
    }

    @Test
    fun prevAndNextRespectGuards() {
        val s = HistoryState(today)
        assertFalse(s.next(today))
        assertTrue(s.prev())
        assertEquals(Period(Mode.MONTH, LocalDate.of(2026, 8, 1)), s.period)
        assertTrue(s.next(today))
        assertEquals(Period(Mode.MONTH, LocalDate.of(2026, 9, 1)), s.period)
        s.selectMode(Mode.TOTAL, today)
        assertFalse(s.prev())
        assertFalse(s.next(today))
    }

    @Test
    fun barValuesHideFutureDatesButKeepSlots() {
        val s = HistoryState(today)
        s.rendered(s.period, days(2026, 9, 30), today)
        val values = s.barValues(today)
        assertEquals(30, values.size)
        assertEquals(8.0, values[7]!!, 1e-9)
        assertNull(values[8])
        assertEquals(36.0, s.visibleTotal(today), 1e-9)
    }

    @Test
    fun highlightIndexMarksTodayOnlyInCurrentPeriods() {
        val s = HistoryState(today)
        s.rendered(s.period, days(2026, 9, 30), today)
        assertEquals(7, s.highlightIndex(today))
        s.prev()
        s.rendered(s.period, days(2026, 8, 31), today)
        assertEquals(-1, s.highlightIndex(today))
        s.selectMode(Mode.YEAR, today)
        s.rendered(s.period, months(2026), today)
        assertEquals(8, s.highlightIndex(today))
        s.selectMode(Mode.TOTAL, today)
        s.rendered(s.period, years, today)
        assertEquals(2, s.highlightIndex(today))
    }

    @Test
    fun drillDownUsesTheDrawnBar() {
        val s = HistoryState(today)
        s.rendered(s.period, days(2026, 9, 30), today)
        assertTrue(s.drill(6))
        assertEquals(Period(Mode.DAY, LocalDate.of(2026, 9, 7)), s.period)
    }

    @Test
    fun drillDownIgnoresFutureBarsAndDayMode() {
        val s = HistoryState(today)
        s.rendered(s.period, days(2026, 9, 30), today)
        assertFalse(s.drill(20))
        s.selectMode(Mode.DAY, today)
        s.rendered(s.period, emptyList(), today)
        assertFalse(s.drill(0))
    }

    @Test
    fun drillDownIsIgnoredWhileAnotherPeriodIsLoading() {
        // Reproduces the emulator bug: Total was drawn, user switched to Month, tapped before Month arrived.
        val s = HistoryState(today)
        s.selectMode(Mode.TOTAL, today)
        s.rendered(s.period, years, today)
        assertTrue(s.selectMode(Mode.MONTH, today))
        assertFalse(s.drill(2))
        assertEquals(Period(Mode.MONTH, LocalDate.of(2026, 9, 1)), s.period)
    }

    @Test
    fun staleResultsForAnotherPeriodAreDetectable() {
        val s = HistoryState(today)
        val requested = s.period
        s.prev()
        assertFalse(s.isCurrentRequest(requested))
        assertTrue(s.isCurrentRequest(s.period))
    }

    @Test
    fun jumpToNormalizesAndClampsToToday() {
        val s = HistoryState(today)
        assertTrue(s.jumpTo(Mode.DAY, LocalDate.of(2026, 8, 14), today))
        assertEquals(Period(Mode.DAY, LocalDate.of(2026, 8, 14)), s.period)
        assertTrue(s.jumpTo(Mode.MONTH, LocalDate.of(2026, 3, 20), today))
        assertEquals(Period(Mode.MONTH, LocalDate.of(2026, 3, 1)), s.period)
        assertTrue(s.jumpTo(Mode.YEAR, LocalDate.of(2025, 6, 6), today))
        assertEquals(Period(Mode.YEAR, LocalDate.of(2025, 1, 1)), s.period)
        assertTrue(s.jumpTo(Mode.DAY, LocalDate.of(2027, 1, 1), today))
        assertEquals(Period(Mode.DAY, today), s.period)
    }

    @Test
    fun jumpToSamePeriodReportsNoChange() {
        val s = HistoryState(today)
        assertFalse(s.jumpTo(Mode.MONTH, LocalDate.of(2026, 9, 25), today))
    }
}
