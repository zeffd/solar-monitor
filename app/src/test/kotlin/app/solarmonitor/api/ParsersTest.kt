package app.solarmonitor.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ParsersTest {
    private fun fixture(name: String): String =
        javaClass.getResource("/fixtures/$name.json")!!.readText()

    @Test
    fun envelopeReturnsObjectOnSuccess() {
        val o = Parsers.envelope("""{"err":0,"desc":"ERR_NONE","dat":{"x":1}}""")
        assertEquals(1, o.getJSONObject("dat").getInt("x"))
    }

    @Test
    fun envelopeThrowsApiExceptionWithCodeAndDesc() {
        try {
            Parsers.envelope("""{"err":10,"desc":"ERR_NO_AUTH"}""")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(10, e.code)
            assertEquals("ERR_NO_AUTH", e.desc)
            assertEquals("ERR_NO_AUTH (10)", e.message)
        }
    }

    @Test
    fun envelopeThrowsBadResponseOnHtmlOrMissingErr() {
        try {
            Parsers.envelope("<html>maintenance</html>")
            fail("expected BadResponseException")
        } catch (e: BadResponseException) {
            assertEquals("Unexpected response", e.message)
        }
        try {
            Parsers.envelope("""{"hello":"world"}""")
            fail("expected BadResponseException")
        } catch (e: BadResponseException) {
            assertEquals("Unexpected response", e.message)
        }
    }

    @Test
    fun parseSessionComputesExpiry() {
        val body = """{"err":0,"desc":"ERR_NONE","dat":{"secret":"abc","expire":432000,"token":"xyz","role":0,"usr":"alice","uid":1}}"""
        val s = Parsers.parseSession(body, "web.shinemonitor.com", 1_700_000_000_000L)
        assertEquals("xyz", s.token)
        assertEquals("abc", s.secret)
        assertEquals("web.shinemonitor.com", s.host)
        assertEquals(1_700_000_000_000L + 432_000L * 1000L, s.expiresAtMillis)
    }

    @Test
    fun parseSessionWithoutTokenIsBadResponse() {
        try {
            Parsers.parseSession("""{"err":0,"desc":"ERR_NONE","dat":{"secret":"abc"}}""", "h", 0L)
            fail("expected BadResponseException")
        } catch (e: BadResponseException) {
            // expected
        }
    }

    @Test
    fun parsePlantsReadsNumbersFromStrings() {
        val plants = Parsers.parsePlants(fixture("plants"))
        assertEquals(1, plants.size)
        val p = plants[0]
        assertEquals(100001L, p.pid)
        assertEquals("Test Plant", p.name)
        assertEquals(0, p.status)
        assertEquals(19800, p.timezoneOffsetSec)
        assertEquals(10.26, p.nominalKw, 1e-9)
        assertEquals(6.204, p.outputKw, 1e-9)
        assertEquals(52.0, p.todayKwh, 1e-9)
        assertEquals(324.0, p.monthKwh, 1e-9)
        assertEquals(12515.0, p.yearKwh, 1e-9)
        assertEquals(29838.0, p.totalKwh, 1e-9)
        assertEquals(LocalDate.of(2024, 11, 22), p.installDate)
    }

    @Test
    fun parsePlantsWithoutInstallDateGivesNull() {
        val body = """{"err":0,"desc":"ERR_NONE","dat":{"plant":[{"pid":1,"name":"x","status":0,"install":"garbage"}]}}"""
        assertNull(Parsers.parsePlants(body).single().installDate)
        val body2 = """{"err":0,"desc":"ERR_NONE","dat":{"plant":[{"pid":1,"name":"x","status":0}]}}"""
        assertNull(Parsers.parsePlants(body2).single().installDate)
    }

    @Test
    fun parsePlantsWithEmptyListReturnsEmpty() {
        assertTrue(Parsers.parsePlants("""{"err":0,"desc":"ERR_NONE","dat":{"total":0,"plant":[]}}""").isEmpty())
    }

    @Test
    fun parseDevicesReadsIdentifiers() {
        val d = Parsers.parseDevices(fixture("devices")).single()
        assertEquals("I3000000000000000001", d.pn)
        assertEquals("SN0000000001", d.sn)
        assertEquals(632, d.devcode)
        assertEquals(1, d.devaddr)
        assertEquals("SN0000000001", d.alias)
        assertEquals(0, d.status)
        assertEquals(100001L, d.pid)
    }

    @Test
    fun parseFieldsKeepsOrderUnitsAndDuplicates() {
        val fields = Parsers.parseFields(fixture("lastdata"))
        assertEquals(14, fields.size)
        assertEquals("Timestamp", fields[0].title)
        assertEquals("2026-09-08 15:17:11", fields[0].value)
        assertNull(fields[0].unit)
        val power = fields.first { it.title == "Output Power" }
        assertEquals("W", power.unit)
        assertEquals("6204", power.value)
        assertEquals(2, fields.count { it.title == "Equipment type" })
    }

    @Test
    fun parseSeriesReadsCurve() {
        val pts = Parsers.parseSeries(fixture("curve"), "outputPower")
        assertEquals(6, pts.size)
        assertEquals(LocalDateTime.of(2026, 9, 8, 12, 0), pts[2].ts)
        assertEquals(8.511, pts[2].value, 1e-9)
    }

    @Test
    fun parseSeriesReadsMonthYearAndYears() {
        val month = Parsers.parseSeries(fixture("month"), "perday")
        assertEquals(5, month.size)
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), month[0].ts)
        assertEquals(50.0, month[0].value, 1e-9)

        val year = Parsers.parseSeries(fixture("year"), "permonth")
        assertEquals(12, year.size)
        assertEquals(1682.0, year[3].value, 1e-9)

        val years = Parsers.parseSeries(fixture("years"), "peryear")
        assertEquals(3, years.size)
        assertEquals(2024, years[0].ts.year)
    }

    @Test
    fun parseSeriesWithMissingKeyReturnsEmpty() {
        assertTrue(Parsers.parseSeries("""{"err":0,"desc":"ERR_NONE","dat":{}}""", "perday").isEmpty())
    }

    @Test
    fun parseSeriesWithBadTimestampIsBadResponse() {
        try {
            Parsers.parseSeries("""{"err":0,"desc":"ERR_NONE","dat":{"perday":[{"val":"1","ts":"yesterday"}]}}""", "perday")
            fail("expected BadResponseException")
        } catch (e: BadResponseException) {
            // expected
        }
    }
}
