package app.solarmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class FormatTest {
    @Test
    fun kilowattsOneDecimal() {
        assertEquals("6.2 kW", Format.kw(6.204))
        assertEquals("0.0 kW", Format.kw(0.0))
    }

    @Test
    fun kilowattHoursScaleWithMagnitude() {
        assertEquals("52.0 kWh", Format.kwh(52.0))
        assertEquals("324 kWh", Format.kwh(324.0))
        assertEquals("12515 kWh", Format.kwh(12515.0))
        assertEquals("29838 kWh", Format.kwh(29838.0))
        assertEquals("29.8 MWh", Format.kwh(29838.0, allowMwh = true))
        assertEquals("324 kWh", Format.kwh(324.0, allowMwh = true))
    }

    @Test
    fun percentOfNominal() {
        assertEquals("60% of 10.3 kW", Format.percent(6.204, 10.26))
        assertEquals("", Format.percent(6.204, 0.0))
    }

    @Test
    fun hhmmFromLocalDateTime() {
        assertEquals("12:05", Format.hhmm(LocalDateTime.of(2026, 9, 8, 12, 5)))
        assertEquals("00:00", Format.hhmm(LocalDateTime.of(2026, 9, 8, 0, 0)))
    }
}
