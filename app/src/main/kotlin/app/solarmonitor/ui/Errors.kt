package app.solarmonitor.ui

import app.solarmonitor.api.ApiException
import app.solarmonitor.api.AuthRequiredException
import app.solarmonitor.api.BadResponseException
import java.io.IOException
import java.net.SocketTimeoutException

/** Maps exceptions to short user-facing text. See spec sections 5.1 and 7. */
object Errors {
    /** Short reason used inside the refresh banner: "Couldn't refresh (<reason>)". */
    fun reason(e: Throwable): String = when (e) {
        is SocketTimeoutException -> "timeout"
        is AuthRequiredException -> "session expired"
        is ApiException -> "${e.desc} (${e.code})"
        is BadResponseException -> e.message ?: "unexpected response"
        is IOException -> "no connection"
        else -> "unexpected error"
    }

    /** Full sentence for the Login screen. */
    fun loginMessage(e: Throwable): String = when {
        e is ApiException && e.code == 16 -> "Wrong password"
        e is ApiException && e.code == 261 -> "User not found"
        e is ApiException -> "${e.desc} (${e.code})"
        e is SocketTimeoutException -> "ShineMonitor is not responding. Try again."
        e is BadResponseException -> "Unexpected response from ShineMonitor."
        e is IOException -> "No connection. Check your network."
        else -> "Something went wrong: ${e.message}"
    }
}
