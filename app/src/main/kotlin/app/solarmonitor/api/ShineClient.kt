package app.solarmonitor.api

import app.solarmonitor.model.Session
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Stateless Api implementation. [http] performs a GET and returns the body;
 * it is injectable so tests never touch the network.
 */
class ShineClient(
    private val http: (String) -> String = Http::get,
    private val clock: () -> Long = System::currentTimeMillis,
) : Api {

    override fun login(host: String, username: String, pwdSha1: String): Session {
        val body = http(Signer.loginUrl(host, username, pwdSha1, clock()))
        return Parsers.parseSession(body, host, clock())
    }

    override fun get(session: Session, action: String): String {
        val body = http(Signer.signedUrl(session.host, session.token, session.secret, action, clock()))
        Parsers.envelope(body) // throws ApiException / BadResponseException
        return body
    }
}

/** Plain HttpURLConnection GET with hard timeouts and one retry on timeout. */
object Http {
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 20_000

    fun get(url: String): String = retryOnceOnTimeout { once(url) }

    fun retryOnceOnTimeout(block: () -> String): String = try {
        block()
    } catch (e: SocketTimeoutException) {
        block()
    }

    private fun once(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: throw IOException("HTTP $code"))
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
