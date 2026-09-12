package app.solarmonitor.model

import java.time.LocalDate
import java.time.LocalDateTime

/** A logged-in ShineMonitor session. [host] is the API node it was issued by. */
data class Session(
    val token: String,
    val secret: String,
    val expiresAtMillis: Long,
    val host: String,
)

/** Plant summary from webQueryPlants. Energies are kWh, powers are kW. */
data class Plant(
    val pid: Long,
    val name: String,
    val status: Int,
    val timezoneOffsetSec: Int,
    val nominalKw: Double,
    val outputKw: Double,
    val todayKwh: Double,
    val monthKwh: Double,
    val yearKwh: Double,
    val totalKwh: Double,
    /** Commissioning date from the plant record, or null when absent or unparseable. */
    val installDate: LocalDate? = null,
)

/** Inverter identity from webQueryDeviceEs; the four ids are what queryDeviceLastData needs. */
data class Device(
    val pn: String,
    val sn: String,
    val devcode: Int,
    val devaddr: Int,
    val alias: String,
    val status: Int,
    val pid: Long,
)

/** One row of the inverter's last report. */
data class Field(
    val title: String,
    val unit: String?,
    val value: String,
)

/** One sample of a time series. [ts] is plant-local wall-clock time. */
data class Point(
    val ts: LocalDateTime,
    val value: Double,
)
