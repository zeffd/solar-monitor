package app.solarmonitor.store

import android.content.Context
import android.content.SharedPreferences
import app.solarmonitor.model.InverterCompany
import app.solarmonitor.model.Session
import app.solarmonitor.repo.SessionStore

/** App-private SharedPreferences. Holds the password hash (never the plaintext), the session, and plant selection. */
class Prefs(context: Context) : SessionStore {
    private val sp: SharedPreferences = context.getSharedPreferences("solar", Context.MODE_PRIVATE)

    override fun loadSession(): Session? {
        val token = sp.getString(KEY_TOKEN, null) ?: return null
        val secret = sp.getString(KEY_SECRET, null) ?: return null
        val host = sp.getString(KEY_HOST, null) ?: return null
        return Session(token, secret, sp.getLong(KEY_EXPIRES_AT, 0L), host)
    }

    override fun saveSession(session: Session) {
        sp.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_SECRET, session.secret)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
            .putString(KEY_HOST, session.host)
            .apply()
    }

    override fun loadCredentials(): Pair<String, String>? {
        val username = sp.getString(KEY_USERNAME, null) ?: return null
        val pwdSha1 = sp.getString(KEY_PWD_SHA1, null) ?: return null
        return username to pwdSha1
    }

    override fun saveCredentials(username: String, pwdSha1: String) {
        sp.edit().putString(KEY_USERNAME, username).putString(KEY_PWD_SHA1, pwdSha1).apply()
    }

    /** True when both a session and the credentials to renew it are stored. */
    fun hasSession(): Boolean = loadSession() != null && loadCredentials() != null

    /** Service selected at sign-in. Old installations default to their original KSolare flow. */
    var company: InverterCompany
        get() = runCatching {
            InverterCompany.valueOf(sp.getString(KEY_COMPANY, InverterCompany.KSOLARE.name)!!)
        }.getOrDefault(InverterCompany.KSOLARE)
        set(value) { sp.edit().putString(KEY_COMPANY, value.name).apply() }

    /** Selected plant id, or -1 when none has been chosen yet. */
    var plantId: Long
        get() = sp.getLong(KEY_PLANT_ID, -1L)
        set(value) { sp.edit().putLong(KEY_PLANT_ID, value).apply() }

    /** Plant timezone offset in seconds east of UTC, or null until the first plant fetch. */
    var plantTimezoneSec: Int?
        get() = if (sp.contains(KEY_PLANT_TZ)) sp.getInt(KEY_PLANT_TZ, 0) else null
        set(value) {
            if (value == null) sp.edit().remove(KEY_PLANT_TZ).apply()
            else sp.edit().putInt(KEY_PLANT_TZ, value).apply()
        }

    /** Plant install date as epoch day, or null until known. */
    var plantInstallEpochDay: Long?
        get() = if (sp.contains(KEY_PLANT_INSTALL)) sp.getLong(KEY_PLANT_INSTALL, 0L) else null
        set(value) {
            if (value == null) sp.edit().remove(KEY_PLANT_INSTALL).apply()
            else sp.edit().putLong(KEY_PLANT_INSTALL, value).apply()
        }

    fun clear() {
        sp.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_SECRET = "secret"
        const val KEY_EXPIRES_AT = "expiresAt"
        const val KEY_HOST = "host"
        const val KEY_USERNAME = "username"
        const val KEY_PWD_SHA1 = "pwdSha1"
        const val KEY_PLANT_ID = "plantId"
        const val KEY_PLANT_TZ = "plantTz"
        const val KEY_PLANT_INSTALL = "plantInstall"
        const val KEY_COMPANY = "company"
    }
}
