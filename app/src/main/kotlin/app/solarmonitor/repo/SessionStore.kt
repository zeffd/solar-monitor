package app.solarmonitor.repo

import app.solarmonitor.model.Session

/** What the session engine needs to persist. Implemented by store.Prefs; faked in tests. */
interface SessionStore {
    fun loadSession(): Session?
    fun saveSession(session: Session)

    /** Username and SHA-1 hex of the password, or null when nobody is signed in. */
    fun loadCredentials(): Pair<String, String>?
    fun saveCredentials(username: String, pwdSha1: String)
}
