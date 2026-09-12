package app.solarmonitor.api

import app.solarmonitor.model.Device
import java.time.LocalDate
import java.time.YearMonth

/** Action strings for each endpoint. See spec section 3.3. Parameter order matches what was verified against the live service. */
object Actions {
    private const val I18N = "&i18n=en_US"

    fun plants(): String = "&action=webQueryPlants&page=0&pagesize=10$I18N"

    fun devices(): String = "&action=webQueryDeviceEs&page=0&pagesize=20$I18N"

    fun lastData(d: Device): String =
        "&action=queryDeviceLastData$I18N&pn=${d.pn}&devcode=${d.devcode}&devaddr=${d.devaddr}&sn=${d.sn}"

    /** Vendor spelling "Ouput" is intentional. 288 five-minute samples in kW. */
    fun powerCurve(pid: Long, date: LocalDate): String =
        "&action=queryPlantActiveOuputPowerOneDay&plantid=$pid&date=$date$I18N"

    fun energyPerDay(pid: Long, month: YearMonth): String =
        "&action=queryPlantEnergyMonthPerDay&plantid=$pid&date=$month$I18N"

    fun energyPerMonth(pid: Long, year: Int): String =
        "&action=queryPlantEnergyYearPerMonth&plantid=$pid&date=$year$I18N"

    fun energyPerYear(pid: Long): String =
        "&action=queryPlantEnergyTotalPerYear&plantid=$pid$I18N"
}
