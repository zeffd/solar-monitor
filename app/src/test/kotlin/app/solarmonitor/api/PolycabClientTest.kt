package app.solarmonitor.api

import app.solarmonitor.model.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class PolycabClientTest {
    @Test fun `login keeps the returned token and account member id`() {
        val client = PolycabClient(post = { url, token, fields ->
            assertEquals("${PolycabClient.BASE_URL}/UserLogin", url)
            assertEquals(null, token)
            assertEquals("owner@example.com", fields["username"])
            assertEquals("password", fields["password"])
            "{\"status\":1,\"token\":\"session-token\"}"
        }, clock = { 1000L })

        val session = client.login("ignored", "owner@example.com", "password")

        assertEquals("session-token", session.token)
        assertEquals("owner@example.com", session.secret)
        assertEquals(86_401_000L, session.expiresAtMillis)
    }

    @Test fun `inverter list is adapted to the existing device envelope`() {
        val session = Session("token", "owner@example.com", Long.MAX_VALUE, PolycabClient.BASE_URL)
        val client = PolycabClient(post = { url, token, fields ->
            assertEquals("${PolycabClient.BASE_URL}/InverterList", url)
            assertEquals("token", token)
            assertEquals("owner@example.com", fields["MemberID"])
            "{\"status\":1,\"data\":[{\"AutoId\":42,\"GoodsID\":\"PC-42\",\"GoodsName\":\"Roof inverter\",\"GroupAutoID\":7,\"status\":0}]}"
        })

        val devices = Parsers.parseDevices(client.get(session, Actions.devices()))

        assertEquals(1, devices.size)
        assertEquals("42", devices.single().pn)
        assertEquals("PC-42", devices.single().sn)
        assertEquals("Roof inverter", devices.single().alias)
        assertEquals(7L, devices.single().pid)
    }
}
