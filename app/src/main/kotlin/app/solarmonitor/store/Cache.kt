package app.solarmonitor.store

import java.io.File

/**
 * Stores the raw JSON of the last successful response per key, plus when it
 * was saved. Pure java.io so it is unit-testable. Keys may contain ':'.
 */
class Cache(private val dir: File) {
    data class Entry(val json: String, val savedAt: Long)

    fun put(key: String, json: String, now: Long = System.currentTimeMillis()) {
        dir.mkdirs()
        val tmp = File(dir, "${fileName(key)}.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        val dest = File(dir, "${fileName(key)}.json")
        if (!tmp.renameTo(dest)) {
            dest.delete()
            tmp.renameTo(dest)
        }
        File(dir, "${fileName(key)}.ts").writeText(now.toString(), Charsets.UTF_8)
    }

    fun get(key: String): Entry? {
        val json = File(dir, "${fileName(key)}.json")
        val ts = File(dir, "${fileName(key)}.ts")
        if (!json.exists() || !ts.exists()) return null
        return try {
            Entry(json.readText(Charsets.UTF_8), ts.readText(Charsets.UTF_8).trim().toLong())
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileName(key: String): String = key.replace(':', '_')
}
