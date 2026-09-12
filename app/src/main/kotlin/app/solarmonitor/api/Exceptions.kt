package app.solarmonitor.api

import java.io.IOException

/** The server answered with a non-zero `err`. */
class ApiException(val code: Int, val desc: String) : Exception("$desc ($code)")

/** The body was not the JSON envelope we expect. */
class BadResponseException(message: String) : IOException(message)

/** The stored session is unusable and re-login failed; the user must sign in again. */
class AuthRequiredException(cause: Throwable? = null) : Exception("Session expired, sign in again", cause)
