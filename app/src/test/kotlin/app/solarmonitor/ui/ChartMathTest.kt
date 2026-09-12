package app.solarmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartMathTest {
    @Test
    fun niceMaxRoundsUpToCleanGridlines() {
        assertEquals(10.0, ChartMath.niceMax(9.065), 1e-9)
        assertEquals(12.0, ChartMath.niceMax(10.26), 1e-9)
        assertEquals(80.0, ChartMath.niceMax(62.0), 1e-9)
        assertEquals(2000.0, ChartMath.niceMax(1682.0), 1e-9)
        assertEquals(16000.0, ChartMath.niceMax(15939.0), 1e-9)
        assertEquals(4.0, ChartMath.niceMax(4.0), 1e-9)
        assertEquals(0.6, ChartMath.niceMax(0.5), 1e-9)
    }

    @Test
    fun niceMaxOfZeroOrNegativeIsOne() {
        assertEquals(1.0, ChartMath.niceMax(0.0), 1e-9)
        assertEquals(1.0, ChartMath.niceMax(-3.0), 1e-9)
        assertEquals(1.0, ChartMath.niceMax(Double.NaN), 1e-9)
    }

    @Test
    fun barIndexMapsXToSlot() {
        assertEquals(0, ChartMath.barIndexAt(0f, 0f, 100f, 10))
        assertEquals(5, ChartMath.barIndexAt(55f, 0f, 100f, 10))
        assertEquals(9, ChartMath.barIndexAt(99.9f, 0f, 100f, 10))
        assertNull(ChartMath.barIndexAt(100f, 0f, 100f, 10))
        assertNull(ChartMath.barIndexAt(-1f, 0f, 100f, 10))
        assertNull(ChartMath.barIndexAt(50f, 0f, 100f, 0))
    }

    @Test
    fun coordinateMapping() {
        assertEquals(50f, ChartMath.xForMinute(720, 0f, 100f), 1e-4f)
        assertEquals(0f, ChartMath.xForMinute(0, 0f, 100f), 1e-4f)
        assertEquals(100f, ChartMath.xForMinute(1440, 0f, 100f), 1e-4f)
        assertEquals(50f, ChartMath.yForValue(5.0, 0f, 100f, 10.0), 1e-4f)
        assertEquals(100f, ChartMath.yForValue(0.0, 0f, 100f, 10.0), 1e-4f)
        assertEquals(0f, ChartMath.yForValue(10.0, 0f, 100f, 10.0), 1e-4f)
    }

    @Test
    fun gridLabelsDropUselessDecimals() {
        assertEquals("10", ChartMath.gridLabel(10.0))
        assertEquals("2.5", ChartMath.gridLabel(2.5))
        assertEquals("2000", ChartMath.gridLabel(2000.0))
        assertEquals("0", ChartMath.gridLabel(0.0))
    }

    @Test
    fun minuteForXIsInverseOfXForMinute() {
        assertEquals(720, ChartMath.minuteForX(50f, 0f, 100f))
        assertEquals(0, ChartMath.minuteForX(-10f, 0f, 100f))
        assertEquals(1440, ChartMath.minuteForX(150f, 0f, 100f))
        assertEquals(360, ChartMath.minuteForX(ChartMath.xForMinute(360, 20f, 380f), 20f, 380f))
    }

    @Test
    fun nearestSampleIndexPicksClosestMinute() {
        val minutes = listOf(0, 300, 600, 900)
        assertEquals(2, ChartMath.nearestSampleIndex(700, minutes))
        assertEquals(3, ChartMath.nearestSampleIndex(760, minutes))
        assertEquals(0, ChartMath.nearestSampleIndex(0, minutes))
        assertEquals(3, ChartMath.nearestSampleIndex(1440, minutes))
        assertNull(ChartMath.nearestSampleIndex(100, emptyList()))
    }
}
