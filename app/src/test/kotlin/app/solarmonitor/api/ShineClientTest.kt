package app.solarmonitor.api

import app.solarmonitor.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.YearMonth

class ShineClientTest {
    private val salt = 1_700_000_000_000L
    private val session = Session("t0ken", "s3cret", salt + 1_000_000L, "web.shinemonitor.com")

    @Test
    fun getBuildsSignedUrlAndReturnsBody() {
        var seen = ""
        val body = """{"err":0,"desc":"ERR_NONE","dat":{"total":0}}"""
        val client = ShineClient(http = { url -> seen = url; body }, clock = { salt })
        val out = client.get(session, Actions.plants())
        assertEquals(body, out)
        assertEquals(
            "https://web.shinemonitor.com/public/?sign=7e484ededf514f709f5b5eff68d4b23af7153d2a" +
                "&salt=1700000000000&token=t0ken&action=webQueryPlants&page=0&pagesize=10&i18n=en_US",
            seen,
        )
    }

    @Test
    fun getThrowsApiExceptionOnErrorEnvelope() {
        val client = ShineClient(http = { """{"err":10,"desc":"ERR_NO_AUTH"}""" }, clock = { salt })
        try {
            client.get(session, Actions.plants())
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(10, e.code)
        }
    }

    @Test
    fun loginBuildsLoginUrlAndParsesSession() {
        var seen = ""
        val body = """{"err":0,"desc":"ERR_NONE","dat":{"secret":"abc","expire":432000,"token":"xyz","role":0,"usr":"alice","uid":1}}"""
        val client = ShineClient(http = { url -> seen = url; body }, clock = { salt })
        val s = client.login("web.shinemonitor.com", "alice", "5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8")
        assertEquals("xyz", s.token)
        assertEquals("abc", s.secret)
        assertEquals("web.shinemonitor.com", s.host)
        assertEquals(salt + 432_000_000L, s.expiresAtMillis)
        assertEquals(
            "https://web.shinemonitor.com/public/?sign=205b174ad1e81b87fad7f9379fab6f243138b194" +
                "&salt=1700000000000&action=auth&usr=alice&company-key=bnrl_frRFjEz8Mkn",
            seen,
        )
    }

    @Test
    fun actionsHaveExpectedShapes() {
        assertEquals(
            "&action=queryPlantActiveOuputPowerOneDay&plantid=100001&date=2026-09-08&i18n=en_US",
            Actions.powerCurve(100001L, LocalDate.of(2026, 9, 8)),
        )
        assertEquals(
            "&action=queryPlantEnergyMonthPerDay&plantid=100001&date=2026-09&i18n=en_US",
            Actions.energyPerDay(100001L, YearMonth.of(2026, 9)),
        )
        assertEquals(
            "&action=queryPlantEnergyYearPerMonth&plantid=100001&date=2026&i18n=en_US",
            Actions.energyPerMonth(100001L, 2026),
        )
        assertEquals("&action=queryPlantEnergyTotalPerYear&plantid=100001&i18n=en_US", Actions.energyPerYear(100001L))
        assertEquals("&action=webQueryDeviceEs&page=0&pagesize=20&i18n=en_US", Actions.devices())
    }

    @Test
    fun retryOnceOnTimeoutRetriesExactlyOnce() {
        var calls = 0
        val out = Http.retryOnceOnTimeout {
            calls++
            if (calls == 1) throw SocketTimeoutException("read timed out") else "ok"
        }
        assertEquals("ok", out)
        assertEquals(2, calls)
    }

    @Test
    fun retryOnceOnTimeoutGivesUpAfterSecondTimeout() {
        var calls = 0
        try {
            Http.retryOnceOnTimeout {
                calls++
                throw SocketTimeoutException("read timed out")
            }
            fail("expected SocketTimeoutException")
        } catch (e: SocketTimeoutException) {
            assertEquals(2, calls)
        }
    }
}
