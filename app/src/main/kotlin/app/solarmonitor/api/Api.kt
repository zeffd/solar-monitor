package app.solarmonitor.api

import app.solarmonitor.model.Session

/** The two operations the rest of the app needs from ShineMonitor. Implemented by ShineClient; faked in tests. */
interface Api {
    /** Signs in on [host]. Throws ApiException (16 wrong password, 261 unknown user on this node) or IOException. */
    fun login(host: String, username: String, pwdSha1: String): Session

    /** Performs a signed call and returns the raw JSON body after validating the envelope. Throws ApiException when err != 0. */
    fun get(session: Session, action: String): String
}
