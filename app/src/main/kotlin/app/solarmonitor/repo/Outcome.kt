package app.solarmonitor.repo

/** Result delivered to a screen. Cached arrives first when a cache entry exists, then Fresh or Error. */
sealed class Outcome<T> {
    /** From the on-device cache. When [isFinal] is true nothing else follows, because past periods are never refetched. */
    data class Cached<T>(val value: T, val at: Long, val isFinal: Boolean = false) : Outcome<T>()
    data class Fresh<T>(val value: T, val at: Long) : Outcome<T>()
    data class Error<T>(val error: Throwable, val cachedValue: T?, val cachedAt: Long?) : Outcome<T>()
}

/** Handle a screen keeps so results are dropped after it is paused or destroyed. */
class Cancellable {
    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }
}
