package app.solarmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    @Test
    fun dailyAverageUsesCompletedDaysOnly() {
        // 10 Sep: nine completed days; today's partial 30.3 kWh is excluded.
        val avg = Stats.avgPerCompletedDay(monthKwh = 426.0, todayKwh = 30.3, today = LocalDate.of(2026, 9, 10))
        assertEquals((426.0 - 30.3) / 9, avg!!, 1e-9)
    }

    @Test
    fun dailyAverageIsHiddenOnTheFirstOfTheMonth() {
        assertNull(Stats.avgPerCompletedDay(monthKwh = 12.0, todayKwh = 12.0, today = LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun dailyAverageNeverGoesNegativeWhenTotalsDisagree() {
        assertEquals(0.0, Stats.avgPerCompletedDay(monthKwh = 20.0, todayKwh = 25.0, today = LocalDate.of(2026, 9, 3))!!, 1e-9)
    }

    @Test
    fun monthlyAverageUsesCompletedMonthsOnly() {
        val avg = Stats.avgPerCompletedMonth(yearKwh = 12617.0, monthKwh = 426.0, today = LocalDate.of(2026, 9, 10))
        assertEquals((12617.0 - 426.0) / 8, avg!!, 1e-9)
    }

    @Test
    fun monthlyAverageIsHiddenInJanuary() {
        assertNull(Stats.avgPerCompletedMonth(yearKwh = 100.0, monthKwh = 100.0, today = LocalDate.of(2026, 1, 15)))
    }

    @Test
    fun sinceLabelUsesMonthAndYear() {
        assertEquals("Nov 2024", Stats.sinceLabel(LocalDate.of(2024, 11, 22)))
        assertNull(Stats.sinceLabel(null))
    }
}
