package app.solarmonitor.repo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.solarmonitor.api.Actions
import app.solarmonitor.api.Api
import app.solarmonitor.api.BadResponseException
import app.solarmonitor.api.Parsers
import app.solarmonitor.api.ShineClient
import app.solarmonitor.model.Device
import app.solarmonitor.model.Field
import app.solarmonitor.model.Plant
import app.solarmonitor.model.Point
import app.solarmonitor.model.Session
import app.solarmonitor.store.Cache
import app.solarmonitor.store.Prefs
import app.solarmonitor.time.Mode
import app.solarmonitor.time.Period
import app.solarmonitor.time.PlantTime
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The one object screens talk to. All disk and network work runs on a single
 * background thread; callbacks are posted to the main thread and skipped once
 * the caller's Cancellable is cancelled.
 */
class SolarRepo private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: SolarRepo? = null

        fun get(context: Context): SolarRepo =
            instance ?: synchronized(this) {
                instance ?: SolarRepo(context.applicationContext).also { instance = it }
            }

        private const val KEY_PLANTS = "plants"
        private const val KEY_DEVICES = "devices"
        private const val KEY_LASTDATA = "lastdata"
        private const val KEY_OUTPUT_POWER = "outputPower"
    }

    data class Dashboard(val plant: Plant, val curve: List<Point>)
    data class DeviceReport(val device: Device, val fields: List<Field>)
    data class History(val period: Period, val points: List<Point>, val dayKwh: Double?)

    val prefs = Prefs(context)
    private val cache = Cache(File(context.filesDir, "cache"))
    private val client: Api = ShineClient()
    private val engine = Engine(client, prefs)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** The plant's fixed offset once known, otherwise the phone's current offset. */
    fun plantZone(): ZoneOffset {
        val tz = prefs.plantTimezoneSec
        return if (tz != null) PlantTime.zone(tz) else ZoneId.systemDefault().rules.getOffset(Instant.now())
    }

    fun today(): LocalDate = PlantTime.today(plantZone(), System.currentTimeMillis())

    /** The plant's commissioning date once a plant record has been fetched, else null. */
    fun installDate(): LocalDate? = prefs.plantInstallEpochDay?.let { LocalDate.ofEpochDay(it) }

    fun login(username: String, password: String, cb: (Outcome<Session>) -> Unit): Cancellable {
        val c = Cancellable()
        executor.execute {
            try {
                val session = engine.login(username, password)
                post(c, cb, Outcome.Fresh(session, now()))
            } catch (e: Exception) {
                post(c, cb, Outcome.Error(e, null, null))
            }
        }
        return c
    }

    fun dashboard(cb: (Outcome<Dashboard>) -> Unit): Cancellable = load(
        cb,
        cached = {
            val plantsEntry = cache.get(KEY_PLANTS)
            val plant = plantsEntry?.let { pickPlant(Parsers.parsePlants(it.json)) }
            val curveEntry = if (plant != null) cache.get("curve:${today()}") else null
            if (plantsEntry != null && plant != null && curveEntry != null) {
                Dashboard(plant, Parsers.parseSeries(curveEntry.json, KEY_OUTPUT_POWER)) to minOf(plantsEntry.savedAt, curveEntry.savedAt)
            } else {
                null
            }
        },
        fresh = {
            engine.withSession { s ->
                val plant = fetchPlant(s)
                val date = today()
                val curveJson = client.get(s, Actions.powerCurve(plant.pid, date))
                cache.put("curve:$date", curveJson)
                Dashboard(plant, Parsers.parseSeries(curveJson, KEY_OUTPUT_POWER))
            }
        },
    )

    fun deviceReport(cb: (Outcome<DeviceReport>) -> Unit): Cancellable = load(
        cb,
        cached = {
            val devicesEntry = cache.get(KEY_DEVICES)
            val lastEntry = cache.get(KEY_LASTDATA)
            if (devicesEntry != null && lastEntry != null) {
                DeviceReport(pickDevice(Parsers.parseDevices(devicesEntry.json)), Parsers.parseFields(lastEntry.json)) to lastEntry.savedAt
            } else {
                null
            }
        },
        fresh = {
            engine.withSession { s ->
                val devicesJson = client.get(s, Actions.devices())
                cache.put(KEY_DEVICES, devicesJson)
                val device = pickDevice(Parsers.parseDevices(devicesJson))
                val lastJson = client.get(s, Actions.lastData(device))
                cache.put(KEY_LASTDATA, lastJson)
                DeviceReport(device, Parsers.parseFields(lastJson))
            }
        },
    )

    /**
     * A cached period is final only when it was saved after the period ended
     * (see Period.isCompleteAt); anything cached mid-period, including today's
     * curve written by the dashboard, is shown immediately but refetched.
     */
    fun history(period: Period, cb: (Outcome<History>) -> Unit): Cancellable {
        val today = today()
        val zone = plantZone()
        val entry = cache.get(period.cacheKey())
        val complete = entry != null && period.isCompleteAt(entry.savedAt, zone) &&
            (period.mode != Mode.DAY || monthCacheComplete(period.date, zone))
        return load(
            cb,
            refetch = !complete,
            cached = {
                if (entry == null) {
                    null
                } else {
                    val points = Parsers.parseSeries(entry.json, seriesKey(period.mode))
                    val dayKwh = if (period.mode == Mode.DAY) cachedDayKwh(period.date) else null
                    History(period, points, dayKwh) to entry.savedAt
                }
            },
            fresh = {
                engine.withSession { s ->
                    val pid = requirePlantId(s)
                    val json = client.get(s, actionFor(period, pid))
                    cache.put(period.cacheKey(), json)
                    val points = Parsers.parseSeries(json, seriesKey(period.mode))
                    val dayKwh = if (period.mode == Mode.DAY) fetchDayKwh(s, pid, period.date, today) else null
                    History(period, points, dayKwh)
                }
            },
        )
    }

    /**
     * Clears credentials, session and cached data. Prefs are cleared twice: now,
     * so Login sees no session immediately, and again on the executor so a fetch
     * that was already running cannot write a stale plant id or session afterwards.
     */
    fun logout() {
        prefs.clear()
        executor.execute {
            cache.clear()
            prefs.clear()
        }
    }

    // ---- internals -------------------------------------------------------

    /**
     * Cache-then-network. Runs [cached] first and posts Outcome.Cached when it
     * yields a value; then, unless [refetch] is false and a cached value was
     * found, runs [fresh] and posts Fresh or Error (Error carries the cached value).
     */
    private fun <T> load(
        cb: (Outcome<T>) -> Unit,
        refetch: Boolean = true,
        cached: () -> Pair<T, Long>?,
        fresh: () -> T,
    ): Cancellable {
        val c = Cancellable()
        executor.execute {
            if (c.isCancelled) return@execute
            val old = try { cached() } catch (e: Exception) { null }
            if (old != null) post(c, cb, Outcome.Cached(old.first, old.second, isFinal = !refetch))
            if (old != null && !refetch) return@execute
            if (c.isCancelled) return@execute // a cancelled screen must not tie up the executor with network work
            try {
                post(c, cb, Outcome.Fresh(fresh(), now()))
            } catch (e: Exception) {
                if (e is BadResponseException) Log.w("SolarRepo", "Unexpected response", e)
                post(c, cb, Outcome.Error(e, old?.first, old?.second))
            }
        }
        return c
    }

    private fun <T> post(c: Cancellable, cb: (Outcome<T>) -> Unit, outcome: Outcome<T>) {
        main.post { if (!c.isCancelled) cb(outcome) }
    }

    private fun now(): Long = System.currentTimeMillis()

    /** Fetches the plant list, remembers the chosen plant's id and timezone, returns it. */
    private fun fetchPlant(s: Session): Plant {
        val plantsJson = client.get(s, Actions.plants())
        val plant = pickPlant(Parsers.parsePlants(plantsJson))
        cache.put(KEY_PLANTS, plantsJson)
        prefs.plantId = plant.pid
        prefs.plantTimezoneSec = plant.timezoneOffsetSec
        plant.installDate?.let { prefs.plantInstallEpochDay = it.toEpochDay() }
        return plant
    }

    private fun requirePlantId(s: Session): Long {
        val stored = prefs.plantId
        return if (stored > 0) stored else fetchPlant(s).pid
    }

    private fun pickPlant(plants: List<Plant>): Plant {
        if (plants.isEmpty()) throw BadResponseException("No plants on this account")
        val wanted = prefs.plantId
        return plants.firstOrNull { it.pid == wanted } ?: plants.first()
    }

    private fun pickDevice(devices: List<Device>): Device {
        if (devices.isEmpty()) throw BadResponseException("No devices on this account")
        val pid = prefs.plantId
        return devices.firstOrNull { it.pid == pid } ?: devices.first()
    }

    private fun seriesKey(mode: Mode): String = when (mode) {
        Mode.DAY -> KEY_OUTPUT_POWER
        Mode.MONTH -> "perday"
        Mode.YEAR -> "permonth"
        Mode.TOTAL -> "peryear"
    }

    private fun actionFor(period: Period, pid: Long): String = when (period.mode) {
        Mode.DAY -> Actions.powerCurve(pid, period.date)
        Mode.MONTH -> Actions.energyPerDay(pid, YearMonth.from(period.date))
        Mode.YEAR -> Actions.energyPerMonth(pid, period.date.year)
        Mode.TOTAL -> Actions.energyPerYear(pid)
    }

    /** The day's kWh from the cached month series, if we have it. */
    private fun cachedDayKwh(date: LocalDate): Double? {
        val entry = cache.get(Period.of(Mode.MONTH, date).cacheKey()) ?: return null
        return Parsers.parseSeries(entry.json, "perday").firstOrNull { it.ts.toLocalDate() == date }?.value
    }

    /** True when the month containing [date] is cached and that cache was written after the month ended. */
    private fun monthCacheComplete(date: LocalDate, zone: ZoneOffset): Boolean {
        val month = Period.of(Mode.MONTH, date)
        val entry = cache.get(month.cacheKey()) ?: return false
        return month.isCompleteAt(entry.savedAt, zone)
    }

    /** The day's kWh from the month series, from cache only when that cache is complete, else from the network. */
    private fun fetchDayKwh(s: Session, pid: Long, date: LocalDate, today: LocalDate): Double? {
        val month = Period.of(Mode.MONTH, date)
        val cachedEntry = cache.get(month.cacheKey())
        val json = if (cachedEntry != null && month.isCompleteAt(cachedEntry.savedAt, plantZone())) {
            cachedEntry.json
        } else {
            client.get(s, Actions.energyPerDay(pid, YearMonth.from(date))).also { cache.put(month.cacheKey(), it) }
        }
        return Parsers.parseSeries(json, "perday").firstOrNull { it.ts.toLocalDate() == date }?.value
    }
}
