package app.solarmonitor.api

import app.solarmonitor.model.Session
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Adapter for Polycab PV Solar Monitoring's documented-in-app HTTP contract.
 *
 * The rest of Solar Monitor deliberately consumes the same small JSON shape as
 * ShineMonitor. This adapter translates Polycab's responses to that shape, so
 * the existing dashboard, history, cache and details views remain unchanged.
 */
class PolycabClient(
    private val post: (String, String?, Map<String, Any?>) -> String = PolycabHttp::post,
    private val clock: () -> Long = System::currentTimeMillis,
) : Api {
    companion object {
        const val BASE_URL = "https://pv.polycabmonitoring.com/dist/server/api/CodeIgniter/index.php/version3/v2/Inverterapi"
        private val STATUS_MODBUS = listOf("1A18", "1A44", "1A45", "1A46", "1A4E", "214C", "2143")
        private val INFO_MODBUS = STATUS_MODBUS + listOf("1A3D", "2112", "2032", "2033", "2030", "2034", "2035", "2031", "2118", "211A", "2119", "211B")
    }

    override fun login(host: String, username: String, pwdSha1: String): Session {
        val root = response(post("$BASE_URL/UserLogin", null, mapOf("username" to username, "password" to pwdSha1)))
        val token = firstString(root, "token") ?: throw BadResponseException("Polycab login did not return a token")
        // The API returns the numeric account id used by its monitoring routes. Older
        // accounts may omit it, in which case the login name is still accepted.
        val memberId = firstString(root, "id") ?: username
        return Session(token, memberId, clock() + 24 * 60 * 60 * 1000L, BASE_URL)
    }

    override fun get(session: Session, action: String): String {
        val params = query(action)
        val actionName = params["action"] ?: throw BadResponseException("Missing action")
        return when (actionName) {
            "webQueryPlants" -> plants(session)
            "webQueryDeviceEs" -> devices(session)
            "queryDeviceLastData" -> fields(session, params)
            "queryPlantActiveOuputPowerOneDay" -> series(session, "/getDailyEnergyChart", params, "outputPower")
            "queryPlantEnergyMonthPerDay" -> series(session, "/getMonthlyAndAnnualEnergyChart", params, "perday")
            "queryPlantEnergyYearPerMonth" -> series(session, "/getYearAndAnnualEnergyChart", params, "permonth")
            "queryPlantEnergyTotalPerYear" -> series(session, "/getYearAndAnnualEnergyChart", params, "peryear")
            else -> throw BadResponseException("Unsupported Polycab action: $actionName")
        }
    }

    private fun plants(session: Session): String {
        // monitoringOverView is only an aggregate. getAllPlantsInfo is the route the
        // official app uses for its individual plant records and production totals.
        val root = response(post("$BASE_URL/getAllPlantsInfo", session.token, mapOf(
            "memberAutoID" to session.secret, "groupID" to "", "inDate" to LocalDate.now().toString(),
        )))
        val rows = rows(root)
        val plants = JSONArray()
        rows.forEachIndexed { index, row ->
            val stats = row.optJSONObject("statistic")
            val production = stats?.optJSONObject("production")
            val id = number(row, "GroupAutoID", "groupID", "GroupId", "id", "AutoId").toLongOrNull() ?: (index + 1).toLong()
            plants.put(JSONObject().apply {
                put("pid", id)
                put("name", text(row, "GroupName", "groupName", "plantName", "name").ifEmpty { "Polycab plant" })
                put("status", if (number(row, "online", "status", "Status") == "0") 0 else 1)
                put("nominalPower", number(stats ?: row, "capacity", "Capacity", "nominalPower", "ratedPower"))
                put("outputPower", number(stats ?: row, "power", "Power", "outputPower", "currentPower"))
                put("energy", number(production ?: row, "today", "todayEnergy", "TodayEnergy", "energy", "E_today"))
                put("energyMonth", number(row, "monthEnergy", "MonthEnergy", "energyMonth"))
                put("energyYear", number(row, "yearEnergy", "YearEnergy", "energyYear"))
                put("energyTotal", number(production ?: row, "total", "totalEnergy", "TotalEnergy", "energyTotal", "E_total"))
                put("address", JSONObject().put("timezone", 19800))
            })
        }
        if (plants.length() == 0) {
            // Some valid Polycab accounts expose only account-level statistics. Show
            // those real values instead of treating the account as a failed login.
            val stats = root.optJSONObject("statistic")
            val production = stats?.optJSONObject("production")
            plants.put(JSONObject().apply {
                put("pid", 0)
                put("name", "Polycab monitoring summary")
                put("status", 0)
                put("nominalPower", number(stats ?: JSONObject(), "capacity"))
                put("outputPower", number(stats ?: JSONObject(), "power"))
                put("energy", number(production ?: JSONObject(), "today"))
                put("energyMonth", 0)
                put("energyYear", 0)
                put("energyTotal", number(production ?: JSONObject(), "total"))
                put("address", JSONObject().put("timezone", 19800))
            })
        }
        return envelope(JSONObject().put("plant", plants))
    }

    private fun devices(session: Session): String {
        val root = response(post("$BASE_URL/InverterList", session.token, mapOf("MemberID" to session.secret)))
        val devices = JSONArray()
        rows(root).forEachIndexed { index, row ->
            val id = number(row, "AutoId", "autoId", "GoodsAutoID", "id").ifEmpty { (index + 1).toString() }
            devices.put(JSONObject().apply {
                put("pn", id)
                put("sn", text(row, "GoodsID", "SN", "sn", "serialNo").ifEmpty { id })
                put("devcode", 0); put("devaddr", 0)
                put("devalias", text(row, "GoodsName", "name", "alias").ifEmpty { "Polycab inverter $id" })
                put("status", if (number(row, "status", "Status") == "0") 0 else 1)
                put("pid", number(row, "GroupAutoID", "groupID", "GroupId", "plantId").toLongOrNull() ?: -1)
            })
        }
        return envelope(JSONObject().put("device", devices))
    }

    private fun fields(session: Session, params: Map<String, String>): String {
        val root = response(post("$BASE_URL/getInverterInfo_v1", session.token, mapOf(
            "AutoId" to (params["pn"] ?: ""), "memberAutoID" to session.secret, "ModbusArr" to INFO_MODBUS,
        )))
        val data = JSONArray()
        flatten(root).forEach { (key, value) -> if (value !is JSONObject && value !is JSONArray) data.put(JSONObject().put("title", key).put("val", value.toString())) }
        return envelope(data)
    }

    private fun series(session: Session, route: String, params: Map<String, String>, key: String): String {
        // Account-level summaries have no plant/inverter id, so no curve is available.
        if (params["plantid"] == "0") return envelope(JSONObject().put(key, JSONArray()))
        val date = params["date"] ?: LocalDate.now().toString()
        val root = response(post("$BASE_URL$route", session.token, mapOf(
            "AutoId" to (params["plantid"] ?: ""), "inDate" to date, "plantsId" to (params["plantid"] ?: ""), "memberAutoID" to session.secret,
        )))
        val points = JSONArray()
        rows(root).forEach { row ->
            val ts = text(row, "ts", "time", "date", "inDate", "Time")
            val value = number(row, "val", "value", "energy", "power", "Power")
            if (ts.isNotEmpty()) points.put(JSONObject().put("ts", normalTime(ts, date)).put("val", value))
        }
        return envelope(JSONObject().put(key, points))
    }

    private fun response(body: String): JSONObject {
        val root = try { JSONObject(body) } catch (_: Exception) { throw BadResponseException("Unexpected Polycab response") }
        if (root.has("status") && root.optInt("status", 0) != 1) throw ApiException(root.optInt("status", -1), root.optString("msg", root.optString("message", "Polycab request failed")))
        return root
    }

    private fun envelope(data: Any): String = JSONObject().put("err", 0).put("desc", "ERR_NONE").put("dat", data).toString()
    private fun query(action: String): Map<String, String> = action.removePrefix("&").split('&').mapNotNull { it.split('=', limit = 2).let { p -> if (p.size == 2) p[0] to java.net.URLDecoder.decode(p[1], "UTF-8") else null } }.toMap()
    private fun rows(root: JSONObject): List<JSONObject> = findArray(root)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: listOfNotNull(findObject(root))
    private fun findArray(value: Any?): JSONArray? = when (value) { is JSONArray -> value; is JSONObject -> value.keys().asSequence().mapNotNull { findArray(value.opt(it)) }.firstOrNull(); else -> null }
    private fun findObject(root: JSONObject): JSONObject? = listOf("data", "result", "rows", "list").asSequence().mapNotNull { root.optJSONObject(it) }.firstOrNull()
    private fun text(o: JSONObject, vararg keys: String): String = keys.firstNotNullOfOrNull { key -> o.opt(key).takeUnless { it == null || it == JSONObject.NULL }?.toString() } ?: ""
    private fun number(o: JSONObject, vararg keys: String): String = text(o, *keys).ifEmpty { "0" }
    private fun firstString(root: JSONObject, key: String): String? = text(root, key).takeIf { it.isNotEmpty() } ?: root.keys().asSequence().mapNotNull { v -> (root.opt(v) as? JSONObject)?.let { firstString(it, key) } }.firstOrNull()
    private fun flatten(o: JSONObject, prefix: String = ""): List<Pair<String, Any>> = o.keys().asSequence().flatMap { key -> val value = o.opt(key); when (value) { is JSONObject -> flatten(value, "$prefix$key.").asSequence(); else -> sequenceOf("$prefix$key" to value) } }.toList()
    private fun normalTime(value: String, date: String): String = when {
        value.matches(Regex("\\d{2}:\\d{2}")) -> "$date $value:00"
        value.matches(Regex("\\d{2}:\\d{2}:\\d{2}")) -> "$date $value"
        value.length >= 19 -> value.take(19)
        else -> "$date 00:00:00"
    }
}

object PolycabHttp {
    private const val KEY = "05469137076236813460585715952089"
    private const val IV = "5161557162012237"

    fun post(url: String, token: String?, values: Map<String, Any?>): String {
        val signed = values + ("sign" to signature(values))
        val body = signed.entries.joinToString("&") { (key, value) -> "${URLEncoder.encode(key, "UTF-8") }=${URLEncoder.encode(if (value is Collection<*>) JSONArray(value).toString() else value?.toString() ?: "", "UTF-8")}" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000; conn.readTimeout = 20_000; conn.requestMethod = "POST"; conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            if (!token.isNullOrEmpty()) conn.setRequestProperty("Authorization", token)
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: throw IOException("HTTP $code")
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun signature(values: Map<String, Any?>): String {
        val plain = values.filterValues { it != null && it.toString().isNotEmpty() && it !is Boolean }.toSortedMap().entries.joinToString("&") { "${it.key}=${if (it.value is Collection<*>) JSONArray(it.value).toString() else it.value}" } + "&$KEY"
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY.toByteArray(), "AES"), IvParameterSpec(IV.toByteArray()))
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)))
    }
}
