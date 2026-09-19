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
            "{\"status\":1,\"token\":\"session-token\",\"id\":\"42\"}"
        }, clock = { 1000L })

        val session = client.login("ignored", "owner@example.com", "password")

        assertEquals("session-token", session.token)
        assertEquals("42", session.secret)
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

    @Test fun `account statistics become a dashboard summary when no plants are listed`() {
        val session = Session("token", "42", Long.MAX_VALUE, PolycabClient.BASE_URL)
        val client = PolycabClient(post = { url, token, fields -> when (url) {
            "${PolycabClient.BASE_URL}/getAllPlantsInfo" -> {
                assertEquals("token", token)
                assertEquals("42", fields["memberAutoID"])
                "{\"plants\":[],\"statistic\":{\"capacity\":5.5,\"power\":2.1,\"production\":{\"today\":8.2,\"total\":1234.5}}}"
            }
            "${PolycabClient.BASE_URL}/monitoringOverView" ->
                "{\"powerStatus\":{\"currPac\":2.1,\"EToday\":8.2,\"Month\":21.4,\"Year\":156.7,\"ETotal\":1234.5}}"
            else -> error("Unexpected URL: $url")
        }
        })

        val plants = Parsers.parsePlants(client.get(session, Actions.plants()))

        assertEquals(1, plants.size)
        assertEquals("Polycab monitoring summary", plants.single().name)
        assertEquals(0L, plants.single().pid)
        assertEquals(2.1, plants.single().outputKw, 0.0)
        assertEquals(8.2, plants.single().todayKwh, 0.0)
        assertEquals(21.4, plants.single().monthKwh, 0.0)
        assertEquals(156.7, plants.single().yearKwh, 0.0)
        assertEquals(1234.5, plants.single().totalKwh, 0.0)
    }
}
