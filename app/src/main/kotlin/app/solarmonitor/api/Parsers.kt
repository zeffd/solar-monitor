package app.solarmonitor.api

import app.solarmonitor.model.Device
import app.solarmonitor.model.Field
import app.solarmonitor.model.Plant
import app.solarmonitor.model.Point
import app.solarmonitor.model.Session
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Parses ShineMonitor JSON bodies into models. Every function throws ApiException for err != 0 and BadResponseException for malformed bodies. */
object Parsers {
    private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Validates the `{err, desc, dat}` envelope and returns it. */
    fun envelope(body: String): JSONObject {
        val o = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw BadResponseException("Unexpected response")
        }
        if (!o.has("err")) throw BadResponseException("Unexpected response")
        val err = o.optInt("err", -1)
        if (err != 0) throw ApiException(err, o.optString("desc", "ERR_UNKNOWN"))
        return o
    }

    fun parseSession(body: String, host: String, nowMillis: Long): Session = guard {
        val d = datObject(body)
        Session(
            token = d.getString("token"),
            secret = d.getString("secret"),
            expiresAtMillis = nowMillis + d.optLong("expire", 432_000L) * 1000L,
            host = host,
        )
    }

    fun parsePlants(body: String): List<Plant> = guard {
        val arr = datObject(body).optJSONArray("plant") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            val addr = p.optJSONObject("address")
            Plant(
                pid = p.getLong("pid"),
                name = p.optString("name", ""),
                status = p.optInt("status", -1),
                timezoneOffsetSec = addr?.optInt("timezone", 0) ?: 0,
                nominalKw = num(p, "nominalPower"),
                outputKw = num(p, "outputPower"),
                todayKwh = num(p, "energy"),
                monthKwh = num(p, "energyMonth"),
                yearKwh = num(p, "energyYear"),
                totalKwh = num(p, "energyTotal"),
                installDate = p.optString("install", "").takeIf { it.length >= 10 }?.let { text ->
                    try { LocalDate.parse(text.substring(0, 10)) } catch (e: DateTimeParseException) { null }
                },
            )
        }
    }

    fun parseDevices(body: String): List<Device> = guard {
        val arr = datObject(body).optJSONArray("device") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            Device(
                pn = d.getString("pn"),
                sn = d.getString("sn"),
                devcode = d.getInt("devcode"),
                devaddr = d.getInt("devaddr"),
                alias = d.optString("devalias", d.getString("sn")),
                status = d.optInt("status", -1),
                pid = d.optLong("pid", -1L),
            )
        }
    }

    fun parseFields(body: String): List<Field> = guard {
        val arr = envelope(body).optJSONArray("dat") ?: throw BadResponseException("Missing dat")
        (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            Field(
                title = f.optString("title", ""),
                unit = if (f.has("unit")) f.getString("unit") else null,
                value = f.optString("val", ""),
            )
        }
    }

    /** [key] is one of outputPower, perday, permonth, peryear. A missing key yields an empty list. */
    fun parseSeries(body: String, key: String): List<Point> = guard {
        val arr = datObject(body).optJSONArray(key) ?: JSONArray()
        (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            Point(
                ts = LocalDateTime.parse(p.getString("ts"), TS),
                value = p.optString("val", "0").toDoubleOrNull() ?: 0.0,
            )
        }
    }

    private fun datObject(body: String): JSONObject =
        envelope(body).optJSONObject("dat") ?: throw BadResponseException("Missing dat")

    private fun num(o: JSONObject, key: String): Double = o.optString(key, "0").toDoubleOrNull() ?: 0.0

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw BadResponseException("Unexpected response")
    } catch (e: DateTimeParseException) {
        throw BadResponseException("Unexpected response")
    }
}
