package app.solarmonitor.api

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Builds signed ShineMonitor URLs. The action string that is signed must be
 * byte-identical to the one appended to the URL; these helpers guarantee that
 * by building both from the same value.
 */
object Signer {
    /** Public constant from the ShineMonitor web portal's JavaScript. Not a user secret. */
    const val COMPANY_KEY = "bnrl_frRFjEz8Mkn"

    fun sha1Hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(40)
        for (b in digest) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    /** URL-encodes like JavaScript's encodeURIComponent for the characters that matter (space, +, '). */
    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun loginAction(username: String): String =
        "&action=auth&usr=${encode(username)}&company-key=$COMPANY_KEY"

    /** Login: sign = sha1(salt + sha1(password) + action). */
    fun loginUrl(host: String, username: String, pwdSha1: String, saltMillis: Long): String {
        val action = loginAction(username)
        val sign = sha1Hex("$saltMillis$pwdSha1$action")
        return "https://$host/public/?sign=$sign&salt=$saltMillis$action"
    }

    /** Every other call: sign = sha1(salt + secret + token + action). */
    fun signedUrl(host: String, token: String, secret: String, action: String, saltMillis: Long): String {
        val sign = sha1Hex("$saltMillis$secret$token$action")
        return "https://$host/public/?sign=$sign&salt=$saltMillis&token=$token$action"
    }
}
