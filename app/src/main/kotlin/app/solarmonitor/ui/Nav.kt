package app.solarmonitor.ui

import android.app.Activity
import android.content.Intent
import app.solarmonitor.repo.SolarRepo

/** Shared navigation used when the session is gone or the user logs out. */
object Nav {
    const val EXTRA_MESSAGE = "message"

    /**
     * Clears the session and cache, then restarts the app at Login with the whole
     * back stack cleared, so a dead Dashboard cannot be resumed with Back.
     * [message], when given, is shown on the Login screen.
     */
    fun restartAtLogin(activity: Activity, repo: SolarRepo, message: String?) {
        repo.logout()
        val intent = Intent(activity, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (message != null) intent.putExtra(EXTRA_MESSAGE, message)
        activity.startActivity(intent)
        activity.finish()
    }
}
