package app.solarmonitor.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import app.solarmonitor.R
import app.solarmonitor.api.AuthRequiredException
import app.solarmonitor.repo.Cancellable
import app.solarmonitor.repo.Outcome
import app.solarmonitor.repo.SolarRepo
import app.solarmonitor.time.PlantTime
import java.time.LocalTime
import kotlin.math.roundToInt

/** Live plant summary and today's curve. Refreshes on resume and every 60 s while visible. */
class DashboardActivity : Activity() {

    companion object {
        private const val REFRESH_MS = 60_000L
    }

    private lateinit var repo: SolarRepo
    private var pending: Cancellable? = null
    private var hasData = false
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    private lateinit var content: View
    private lateinit var plantName: TextView
    private lateinit var status: TextView
    private lateinit var updated: TextView
    private lateinit var banner: TextView
    private lateinit var progress: ProgressBar
    private lateinit var nowValue: TextView
    private lateinit var nowSub: TextView
    private lateinit var nowBar: ProgressBar
    private lateinit var todayValue: TextView
    private lateinit var todaySub: TextView
    private lateinit var monthValue: TextView
    private lateinit var monthSub: TextView
    private lateinit var yearValue: TextView
    private lateinit var yearSub: TextView
    private lateinit var totalValue: TextView
    private lateinit var totalSub: TextView
    private lateinit var chart: ChartView
    private lateinit var peak: TextView
    private lateinit var emptyState: View
    private lateinit var emptyText: TextView
    private var inspectText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        repo = SolarRepo.get(this)

        content = findViewById(R.id.content)
        plantName = findViewById(R.id.plantName)
        status = findViewById(R.id.status)
        updated = findViewById(R.id.updated)
        banner = findViewById(R.id.banner)
        progress = findViewById(R.id.progress)
        nowValue = findViewById(R.id.nowValue)
        nowSub = findViewById(R.id.nowSub)
        nowBar = findViewById(R.id.nowBar)
        todayValue = findViewById(R.id.todayValue)
        todaySub = findViewById(R.id.todaySub)
        monthValue = findViewById(R.id.monthValue)
        monthSub = findViewById(R.id.monthSub)
        yearValue = findViewById(R.id.yearValue)
        yearSub = findViewById(R.id.yearSub)
        totalValue = findViewById(R.id.totalValue)
        totalSub = findViewById(R.id.totalSub)
        chart = findViewById(R.id.chartToday)
        peak = findViewById(R.id.peak)
        emptyState = findViewById(R.id.emptyState)
        emptyText = findViewById(R.id.emptyText)

        chart.onInspect = { point ->
            inspectText = point?.let { "${Format.hhmm(it.ts)} · ${Format.kw(it.value)}" }
            updateCaption()
        }
        findViewById<View>(R.id.refresh).setOnClickListener { refresh() }
        findViewById<View>(R.id.retry).setOnClickListener { refresh() }
        findViewById<View>(R.id.more).setOnClickListener { showMenu(it) }
        findViewById<View>(R.id.rowHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<View>(R.id.rowDevice).setOnClickListener {
            startActivity(Intent(this, DeviceActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        pending?.cancel()
        pending = null
        super.onPause()
    }

    private fun refresh() {
        pending?.cancel()
        progress.visibility = View.VISIBLE
        pending = repo.dashboard { outcome ->
            when (outcome) {
                is Outcome.Cached -> {
                    show(outcome.value, outcome.at)
                    if (outcome.isFinal) progress.visibility = View.INVISIBLE
                }
                is Outcome.Fresh -> {
                    show(outcome.value, outcome.at)
                    banner.visibility = View.GONE
                    progress.visibility = View.INVISIBLE
                }
                is Outcome.Error -> onError(outcome)
            }
        }
    }

    private fun show(d: SolarRepo.Dashboard, at: Long) {
        hasData = true
        emptyState.visibility = View.GONE
        content.visibility = View.VISIBLE

        val plant = d.plant
        plantName.text = plant.name
        val online = plant.status == 0
        status.text = getString(if (online) R.string.online else R.string.offline)
        status.setTextColor(getColor(if (online) R.color.status_online else R.color.status_offline))
        updated.text = getString(R.string.updated_at, PlantTime.hhmm(at, repo.plantZone()))

        nowValue.text = Format.kw(plant.outputKw)
        nowSub.text = Format.percent(plant.outputKw, plant.nominalKw)
        nowBar.progress = if (plant.nominalKw > 0.0) (plant.outputKw / plant.nominalKw * 100.0).roundToInt().coerceIn(0, 100) else 0
        val today = repo.today()
        todayValue.text = Format.kwh(plant.todayKwh)
        val top = d.curve.maxByOrNull { it.value }
        todaySub.text = if (top != null && top.value > 0.0) getString(R.string.peak_short, Format.kw(top.value), Format.hhmm(top.ts)) else ""
        monthValue.text = Format.kwh(plant.monthKwh)
        monthSub.text = Stats.avgPerCompletedDay(plant.monthKwh, plant.todayKwh, today)?.let { getString(R.string.avg_per_day, Format.kwh(it)) } ?: ""
        yearValue.text = Format.kwh(plant.yearKwh)
        yearSub.text = Stats.avgPerCompletedMonth(plant.yearKwh, plant.monthKwh, today)?.let { getString(R.string.avg_per_month, Format.kwh(it)) } ?: ""
        totalValue.text = Format.kwh(plant.totalKwh, allowMwh = true)
        totalSub.text = Stats.sinceLabel(plant.installDate ?: repo.installDate())?.let { getString(R.string.since, it) } ?: ""

        val now = LocalTime.now(repo.plantZone())
        chart.setLine(d.curve, plant.nominalKw, now.hour * 60 + now.minute) // re-fires onInspect if a point is selected
        updateCaption()
    }

    /** The line under the chart carries only the inspect reading now; the peak lives in the Today tile. */
    private fun updateCaption() {
        peak.text = inspectText ?: ""
    }

    private fun onError(outcome: Outcome.Error<SolarRepo.Dashboard>) {
        progress.visibility = View.INVISIBLE
        if (outcome.error is AuthRequiredException) {
            goToLogin(getString(R.string.session_expired))
            return
        }
        val reason = Errors.reason(outcome.error)
        if (hasData) {
            val since = outcome.cachedAt?.let { PlantTime.hhmm(it, repo.plantZone()) } ?: "earlier"
            banner.text = getString(R.string.banner, reason, since)
            banner.visibility = View.VISIBLE
        } else {
            content.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            emptyText.text = getString(R.string.empty_error, reason)
        }
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menuInflater.inflate(R.menu.dashboard_overflow, menu.menu)
        menu.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.logout) {
                goToLogin(null)
                true
            } else {
                false
            }
        }
        menu.show()
    }

    private fun goToLogin(message: String?) {
        handler.removeCallbacks(tick)
        pending?.cancel()
        Nav.restartAtLogin(this, repo, message)
    }
}
