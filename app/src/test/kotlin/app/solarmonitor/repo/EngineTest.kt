package app.solarmonitor.repo

import app.solarmonitor.api.Api
import app.solarmonitor.api.ApiException
import app.solarmonitor.api.AuthRequiredException
import app.solarmonitor.api.Signer
import app.solarmonitor.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EngineTest {
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private class FakeApi : Api {
        val loginCalls = mutableListOf<String>()
        var loginHandler: (String) -> Session = { host -> Session("tok", "sec", Long.MAX_VALUE, host) }
        override fun login(host: String, username: String, pwdSha1: String): Session {
            loginCalls += host
            return loginHandler(host)
        }
        override fun get(session: Session, action: String): String = """{"err":0,"desc":"ERR_NONE","dat":{}}"""
    }

    private class FakeStore : SessionStore {
        var session: Session? = null
        var creds: Pair<String, String>? = null
        override fun loadSession(): Session? = session
        override fun saveSession(session: Session) { this.session = session }
        override fun loadCredentials(): Pair<String, String>? = creds
        override fun saveCredentials(username: String, pwdSha1: String) { creds = username to pwdSha1 }
    }

    private fun engine(api: FakeApi, store: FakeStore) = Engine(api, store) { now }

    @Test
    fun loginOnDefaultNodeSavesCredentialsAndSession() {
        val api = FakeApi(); val store = FakeStore()
        val s = engine(api, store).login("alice", "password")
        assertEquals(listOf("web.shinemonitor.com"), api.loginCalls)
        assertEquals("web.shinemonitor.com", s.host)
        assertEquals("alice" to Signer.sha1Hex("password"), store.creds)
        assertEquals(s, store.session)
    }

    @Test
    fun loginFallsBackToOverseasNodeWhenUserNotFound() {
        val api = FakeApi(); val store = FakeStore()
        api.loginHandler = { host ->
            if (host == "web.shinemonitor.com") throw ApiException(261, "ERR_NOT_FOUND_USR")
            else Session("tok", "sec", Long.MAX_VALUE, host)
        }
        val s = engine(api, store).login("alice", "password")
        assertEquals(listOf("web.shinemonitor.com", "web1.shinemonitor.com"), api.loginCalls)
        assertEquals("web1.shinemonitor.com", s.host)
        assertEquals("web1.shinemonitor.com", store.session?.host)
    }

    @Test
    fun loginWrongPasswordDoesNotFallBackOrSave() {
        val api = FakeApi(); val store = FakeStore()
        api.loginHandler = { throw ApiException(16, "ERR_PASSWORD_VERIF_FAIL") }
        try {
            engine(api, store).login("alice", "nope")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(16, e.code)
        }
        assertEquals(1, api.loginCalls.size)
        assertNull(store.creds)
        assertNull(store.session)
    }

    @Test
    fun loginUnknownOnBothNodesThrows261() {
        val api = FakeApi(); val store = FakeStore()
        api.loginHandler = { throw ApiException(261, "ERR_NOT_FOUND_USR") }
        try {
            engine(api, store).login("ghost", "password")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(261, e.code)
        }
        assertEquals(2, api.loginCalls.size)
    }

    @Test
    fun withSessionUsesStoredSessionWithoutLoggingIn() {
        val api = FakeApi(); val store = FakeStore()
        store.session = Session("t", "s", now + 2 * hour, "web.shinemonitor.com")
        store.creds = "alice" to "hash"
        val token = engine(api, store).withSession { it.token }
        assertEquals("t", token)
        assertTrue(api.loginCalls.isEmpty())
    }

    @Test
    fun withSessionRenewsWhenWithinAnHourOfExpiry() {
        val api = FakeApi(); val store = FakeStore()
        store.session = Session("old", "s", now + 30 * 60_000L, "web1.shinemonitor.com")
        store.creds = "alice" to "hash"
        api.loginHandler = { host -> Session("new", "s2", now + 5 * 24 * hour, host) }
        val token = engine(api, store).withSession { it.token }
        assertEquals("new", token)
        assertEquals(listOf("web1.shinemonitor.com"), api.loginCalls) // re-login on the stored node
        assertEquals("new", store.session?.token)
    }

    @Test
    fun withSessionLogsInWhenNoSessionStored() {
        val api = FakeApi(); val store = FakeStore()
        store.creds = "alice" to "hash"
        val token = engine(api, store).withSession { it.token }
        assertEquals("tok", token)
        assertEquals(listOf("web.shinemonitor.com"), api.loginCalls)
    }

    @Test
    fun withSessionRetriesOnceAfterErrNoAuth() {
        val api = FakeApi(); val store = FakeStore()
        store.session = Session("old", "s", now + 2 * hour, "web.shinemonitor.com")
        store.creds = "alice" to "hash"
        api.loginHandler = { host -> Session("new", "s2", now + 5 * 24 * hour, host) }
        var calls = 0
        val out = engine(api, store).withSession { s ->
            calls++
            if (s.token == "old") throw ApiException(10, "ERR_NO_AUTH") else "ok"
        }
        assertEquals("ok", out)
        assertEquals(2, calls)
        assertEquals(1, api.loginCalls.size)
    }

    @Test
    fun withSessionSecondErrNoAuthBecomesAuthRequired() {
        val api = FakeApi(); val store = FakeStore()
        store.session = Session("old", "s", now + 2 * hour, "web.shinemonitor.com")
        store.creds = "alice" to "hash"
        try {
            engine(api, store).withSession<String> { throw ApiException(10, "ERR_NO_AUTH") }
            fail("expected AuthRequiredException")
        } catch (e: AuthRequiredException) {
            assertEquals(10, (e.cause as ApiException).code)
        }
    }

    @Test
    fun withSessionThrowsAuthRequiredWithoutCredentials() {
        val api = FakeApi(); val store = FakeStore()
        try {
            engine(api, store).withSession { 1 }
            fail("expected AuthRequiredException")
        } catch (e: AuthRequiredException) {
            // expected
        }
        assertTrue(api.loginCalls.isEmpty())
    }

    @Test
    fun withSessionWrapsFailedReloginAsAuthRequired() {
        val api = FakeApi(); val store = FakeStore()
        store.creds = "alice" to "hash"
        api.loginHandler = { throw ApiException(16, "ERR_PASSWORD_VERIF_FAIL") }
        try {
            engine(api, store).withSession { 1 }
            fail("expected AuthRequiredException")
        } catch (e: AuthRequiredException) {
            assertEquals(16, (e.cause as ApiException).code)
        }
    }

    @Test
    fun withSessionPropagatesOtherApiErrorsWithoutRelogin() {
        val api = FakeApi(); val store = FakeStore()
        store.session = Session("t", "s", now + 2 * hour, "web.shinemonitor.com")
        store.creds = "alice" to "hash"
        try {
            engine(api, store).withSession<Int> { throw ApiException(6, "ERR_FORMAT_ERROR") }
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(6, e.code)
        }
        assertTrue(api.loginCalls.isEmpty())
    }
}
