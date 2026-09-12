package app.solarmonitor.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class PeriodTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun ofNormalizesDateToPeriodStart() {
        assertEquals(LocalDate.of(2026, 9, 8), Period.of(Mode.DAY, today).date)
        assertEquals(LocalDate.of(2026, 9, 1), Period.of(Mode.MONTH, today).date)
        assertEquals(LocalDate.of(2026, 1, 1), Period.of(Mode.YEAR, today).date)
        assertEquals(LocalDate.of(2026, 1, 1), Period.of(Mode.TOTAL, today).date)
    }

    @Test
    fun prevAndNextCrossMonthAndYearBoundaries() {
        assertEquals(LocalDate.of(2026, 8, 31), Period.of(Mode.DAY, LocalDate.of(2026, 9, 1)).prev().date)
        assertEquals(LocalDate.of(2025, 12, 1), Period.of(Mode.MONTH, LocalDate.of(2026, 1, 15)).prev().date)
        assertEquals(LocalDate.of(2026, 10, 1), Period.of(Mode.MONTH, today).next().date)
        assertEquals(LocalDate.of(2027, 1, 1), Period.of(Mode.YEAR, today).next().date)
        val total = Period.of(Mode.TOTAL, today)
        assertEquals(total, total.prev())
        assertEquals(total, total.next())
    }

    @Test
    fun isCurrentAndNavigationGuards() {
        val month = Period.of(Mode.MONTH, today)
        assertTrue(month.isCurrent(today))
        assertFalse(month.canGoNext(today))
        assertTrue(month.canGoPrev())
        val last = month.prev()
        assertFalse(last.isCurrent(today))
        assertTrue(last.canGoNext(today))
        assertTrue(Period.of(Mode.YEAR, today).isCurrent(today))
        assertTrue(Period.of(Mode.DAY, today).isCurrent(today))
        assertFalse(Period.of(Mode.DAY, today).prev().isCurrent(today))
        val total = Period.of(Mode.TOTAL, today)
        assertTrue(total.isCurrent(today))
        assertFalse(total.canGoPrev())
        assertFalse(total.canGoNext(today))
    }

    @Test
    fun labels() {
        assertEquals("Tue 8 Sep 2026", Period.of(Mode.DAY, today).label())
        assertEquals("September 2026", Period.of(Mode.MONTH, today).label())
        assertEquals("2026", Period.of(Mode.YEAR, today).label())
        assertEquals("All years", Period.of(Mode.TOTAL, today).label())
    }

    @Test
    fun cacheKeys() {
        assertEquals("curve:2026-09-08", Period.of(Mode.DAY, today).cacheKey())
        assertEquals("month:2026-09", Period.of(Mode.MONTH, today).cacheKey())
        assertEquals("year:2026", Period.of(Mode.YEAR, today).cacheKey())
        assertEquals("years", Period.of(Mode.TOTAL, today).cacheKey())
    }

    @Test
    fun drillIntoGoesOneLevelDown() {
        val ts = LocalDateTime.of(2026, 9, 7, 0, 0)
        assertEquals(Period(Mode.DAY, LocalDate.of(2026, 9, 7)), Period.of(Mode.MONTH, today).drillInto(ts))
        assertEquals(Period(Mode.MONTH, LocalDate.of(2026, 3, 1)), Period.of(Mode.YEAR, today).drillInto(LocalDateTime.of(2026, 3, 1, 0, 0)))
        assertEquals(Period(Mode.YEAR, LocalDate.of(2025, 1, 1)), Period.of(Mode.TOTAL, today).drillInto(LocalDateTime.of(2025, 1, 1, 0, 0)))
        assertNull(Period.of(Mode.DAY, today).drillInto(ts))
    }

    @Test
    fun switchModeFromPresentPeriodGoesToPresentPeriodOfNewMode() {
        assertEquals(Period(Mode.DAY, today), Period.of(Mode.MONTH, today).switchMode(Mode.DAY, today))
        assertEquals(Period(Mode.MONTH, LocalDate.of(2026, 9, 1)), Period.of(Mode.TOTAL, today).switchMode(Mode.MONTH, today))
        assertEquals(Period(Mode.MONTH, LocalDate.of(2026, 9, 1)), Period.of(Mode.YEAR, today).switchMode(Mode.MONTH, today))
        assertEquals(Period(Mode.DAY, today), Period.of(Mode.YEAR, today).switchMode(Mode.DAY, today))
    }

    @Test
    fun switchModeFromPastPeriodKeepsItsAnchorDate() {
        val day = Period.of(Mode.DAY, LocalDate.of(2026, 3, 15))
        assertEquals(Period(Mode.MONTH, LocalDate.of(2026, 3, 1)), day.switchMode(Mode.MONTH, today))
        assertEquals(Period(Mode.YEAR, LocalDate.of(2026, 1, 1)), day.switchMode(Mode.YEAR, today))
        val august = Period.of(Mode.MONTH, LocalDate.of(2026, 8, 1))
        assertEquals(Period(Mode.DAY, LocalDate.of(2026, 8, 1)), august.switchMode(Mode.DAY, today))
        val future = Period(Mode.MONTH, LocalDate.of(2026, 12, 1))
        assertEquals(Period(Mode.DAY, today), future.switchMode(Mode.DAY, today))
    }

    @Test
    fun plantTimeUsesPlantZoneNotPhoneZone() {
        val nowMillis = Instant.parse("2026-09-08T20:00:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 9, 9), PlantTime.today(PlantTime.zone(19800), nowMillis))
        assertEquals(LocalDate.of(2026, 9, 8), PlantTime.today(PlantTime.zone(-8 * 3600), nowMillis))
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), PlantTime.zone(19800))
        assertEquals("01:30", PlantTime.hhmm(nowMillis, PlantTime.zone(19800)))
        assertEquals("12:00", PlantTime.hhmm(nowMillis, PlantTime.zone(-8 * 3600)))
    }

    @Test
    fun endExclusiveIsTheStartOfTheNextPeriod() {
        assertEquals(LocalDate.of(2026, 9, 9), Period.of(Mode.DAY, today).endExclusive())
        assertEquals(LocalDate.of(2026, 10, 1), Period.of(Mode.MONTH, today).endExclusive())
        assertEquals(LocalDate.of(2027, 1, 1), Period.of(Mode.YEAR, today).endExclusive())
        assertNull(Period.of(Mode.TOTAL, today).endExclusive())
    }

    @Test
    fun cachedDataIsCompleteOnlyWhenSavedAfterThePeriodEndedPlusGrace() {
        val zone = ZoneOffset.ofHoursMinutes(5, 30)
        val day = Period.of(Mode.DAY, LocalDate.of(2026, 9, 8))
        val midnightAfter = LocalDate.of(2026, 9, 9).atStartOfDay().toInstant(zone).toEpochMilli()
        val hour = 3_600_000L
        assertFalse(day.isCompleteAt(midnightAfter - 5 * hour, zone))   // saved at 19:00 on the day itself
        assertFalse(day.isCompleteAt(midnightAfter + 10 * 60_000L, zone)) // ten minutes past midnight: server may still lag
        assertTrue(day.isCompleteAt(midnightAfter + hour, zone))
        assertTrue(day.isCompleteAt(midnightAfter + 30L * 24 * hour, zone))

        val month = Period.of(Mode.MONTH, LocalDate.of(2026, 8, 1))
        val sept1 = LocalDate.of(2026, 9, 1).atStartOfDay().toInstant(zone).toEpochMilli()
        assertFalse(month.isCompleteAt(sept1 - hour, zone))
        assertTrue(month.isCompleteAt(sept1 + hour, zone))

        assertFalse(Period.of(Mode.TOTAL, today).isCompleteAt(Long.MAX_VALUE, zone))
    }
}
