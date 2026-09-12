package app.solarmonitor.repo

import app.solarmonitor.api.Api
import app.solarmonitor.api.ApiException
import app.solarmonitor.api.AuthRequiredException
import app.solarmonitor.api.Signer
import app.solarmonitor.model.Session

/**
 * Pure session logic, no Android: login with node fallback, silent renewal,
 * one retry after the server rejects a token. Must be called off the main thread.
 */
class Engine(
    private val api: Api,
    private val store: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val RENEW_MARGIN_MS = 3_600_000L
        val HOSTS = listOf("web.shinemonitor.com", "web1.shinemonitor.com")
        private const val ERR_NO_AUTH = 10
        private const val ERR_NOT_FOUND_USR = 261
    }

    /** Hashes the password, tries each node in order on "user not found", stores credentials and session on success. */
    fun login(username: String, password: String): Session {
        val pwdSha1 = Signer.sha1Hex(password)
        var notFound: ApiException? = null
        for (host in HOSTS) {
            try {
                val session = api.login(host, username, pwdSha1)
                store.saveCredentials(username, pwdSha1)
                store.saveSession(session)
                return session
            } catch (e: ApiException) {
                if (e.code == ERR_NOT_FOUND_USR) notFound = e else throw e
            }
        }
        throw notFound ?: IllegalStateException("no hosts")
    }

    /** Runs [block] with a valid session, renewing or re-logging-in as needed. Throws AuthRequiredException when that is impossible. */
    fun <T> withSession(block: (Session) -> T): T {
        val stored = store.loadSession()
        val session = if (stored != null && stored.expiresAtMillis - clock() >= RENEW_MARGIN_MS) stored else relogin()
        return try {
            block(session)
        } catch (e: ApiException) {
            if (e.code != ERR_NO_AUTH) throw e
            val renewed = relogin()
            try {
                block(renewed)
            } catch (again: ApiException) {
                if (again.code == ERR_NO_AUTH) throw AuthRequiredException(again) else throw again
            }
        }
    }

    private fun relogin(): Session {
        val (username, pwdSha1) = store.loadCredentials() ?: throw AuthRequiredException()
        val host = store.loadSession()?.host ?: HOSTS[0]
        val session = try {
            api.login(host, username, pwdSha1)
        } catch (e: ApiException) {
            throw AuthRequiredException(e)
        }
        store.saveSession(session)
        return session
    }
}
