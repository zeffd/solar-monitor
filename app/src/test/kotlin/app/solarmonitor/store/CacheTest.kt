package app.solarmonitor.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun putThenGetReturnsJsonAndTimestamp() {
        val cache = Cache(tmp.newFolder("cache"))
        cache.put("plants", """{"a":1}""", now = 1234L)
        val e = cache.get("plants")!!
        assertEquals("""{"a":1}""", e.json)
        assertEquals(1234L, e.savedAt)
    }

    @Test
    fun missingKeyReturnsNull() {
        val cache = Cache(tmp.newFolder("cache"))
        assertNull(cache.get("nope"))
    }

    @Test
    fun overwriteKeepsLatest() {
        val cache = Cache(tmp.newFolder("cache"))
        cache.put("k", "one", now = 1L)
        cache.put("k", "two", now = 2L)
        assertEquals("two", cache.get("k")!!.json)
        assertEquals(2L, cache.get("k")!!.savedAt)
    }

    @Test
    fun keysWithColonsAreSafeFileNames() {
        val cache = Cache(tmp.newFolder("cache"))
        cache.put("curve:2026-09-08", "x", now = 1L)
        cache.put("month:2026-09", "y", now = 1L)
        assertEquals("x", cache.get("curve:2026-09-08")!!.json)
        assertEquals("y", cache.get("month:2026-09")!!.json)
    }

    @Test
    fun directoryIsCreatedOnFirstPut() {
        val dir = java.io.File(tmp.root, "does/not/exist/yet")
        val cache = Cache(dir)
        cache.put("k", "v", now = 1L)
        assertEquals("v", cache.get("k")!!.json)
    }

    @Test
    fun clearRemovesEverything() {
        val cache = Cache(tmp.newFolder("cache"))
        cache.put("a", "1", now = 1L)
        cache.put("b", "2", now = 1L)
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
