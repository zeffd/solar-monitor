package app.solarmonitor.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import app.solarmonitor.R
import app.solarmonitor.api.AuthRequiredException
import app.solarmonitor.repo.Cancellable
import app.solarmonitor.repo.Outcome
import app.solarmonitor.repo.SolarRepo
import app.solarmonitor.time.Mode
import app.solarmonitor.time.Period
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Day / Month / Year / Total energy history. All navigation rules live in HistoryState. */
class HistoryActivity : Activity() {

    companion object {
        private val SEGMENTS = mapOf(
            R.id.modeDay to Mode.DAY,
            R.id.modeMonth to Mode.MONTH,
            R.id.modeYear to Mode.YEAR,
            R.id.modeTotal to Mode.TOTAL,
        )
    }

    private lateinit var repo: SolarRepo
    private lateinit var state: HistoryState
    private var pending: Cancellable? = null

    private lateinit var modes: RadioGroup
    private lateinit var prev: ImageButton
    private lateinit var next: ImageButton
    private lateinit var periodLabel: TextView
    private lateinit var total: TextView
    private lateinit var banner: TextView
    private lateinit var progress: ProgressBar
    private lateinit var chart: ChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        repo = SolarRepo.get(this)
        state = HistoryState(repo.today())

        modes = findViewById(R.id.modes)
        prev = findViewById(R.id.prev)
        next = findViewById(R.id.next)
        periodLabel = findViewById(R.id.periodLabel)
        total = findViewById(R.id.total)
        banner = findViewById(R.id.banner)
        progress = findViewById(R.id.progress)
        chart = findViewById(R.id.chart)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        // Click listeners, not RadioGroup.setOnCheckedChangeListener: the group fires that
        // listener for the OLD id as well when check() is called programmatically (see
        // RadioGroup.CheckedStateTracker), which re-routed drill-downs to the wrong period.
        for ((id, mode) in SEGMENTS) {
            findViewById<View>(id).setOnClickListener {
                if (state.selectMode(mode, repo.today())) load()
            }
        }
        prev.setOnClickListener { if (state.prev()) load() }
        next.setOnClickListener { if (state.next(repo.today())) load() }
        chart.onBarTap = { index ->
            if (state.drill(index)) {
                selectRadio(state.period.mode)
                load()
            }
        }
        periodLabel.setOnClickListener { showPicker() }

        selectRadio(state.period.mode)
        load()
    }

    override fun onDestroy() {
        pending?.cancel()
        super.onDestroy()
    }

    private fun selectRadio(mode: Mode) {
        val id = when (mode) {
            Mode.DAY -> R.id.modeDay
            Mode.MONTH -> R.id.modeMonth
            Mode.YEAR -> R.id.modeYear
            Mode.TOTAL -> R.id.modeTotal
        }
        if (modes.checkedRadioButtonId != id) modes.check(id)
    }

    private fun load() {
        pending?.cancel()
        val today = repo.today()
        val period = state.period
        val isTotal = period.mode == Mode.TOTAL
        periodLabel.text = if (isTotal) period.label() else period.label() + "  \u25BE"
        periodLabel.isClickable = !isTotal
        // Dim the previous period's chart until this period's data arrives.
        chart.alpha = if (state.shownPeriod == period) 1f else 0.35f
        prev.visibility = if (isTotal) View.INVISIBLE else View.VISIBLE
        next.visibility = if (isTotal) View.INVISIBLE else View.VISIBLE
        prev.isEnabled = period.canGoPrev()
        next.isEnabled = period.canGoNext(today)
        next.alpha = if (next.isEnabled) 1f else 0.3f
        banner.visibility = View.GONE
        progress.visibility = View.VISIBLE

        pending = repo.history(period) { outcome ->
            if (!state.isCurrentRequest(period)) return@history
            when (outcome) {
                is Outcome.Cached -> {
                    render(outcome.value)
                    if (outcome.isFinal) progress.visibility = View.INVISIBLE
                }
                is Outcome.Fresh -> {
                    render(outcome.value)
                    progress.visibility = View.INVISIBLE
                }
                is Outcome.Error -> {
                    progress.visibility = View.INVISIBLE
                    if (outcome.error is AuthRequiredException) {
                        Nav.restartAtLogin(this, repo, getString(R.string.session_expired))
                    } else {
                        banner.text = getString(R.string.banner_short, Errors.reason(outcome.error))
                        banner.visibility = View.VISIBLE
                        if (outcome.cachedValue == null) {
                            chart.clear()
                            total.text = getString(R.string.no_data)
                        }
                    }
                }
            }
        }
    }

    private fun render(h: SolarRepo.History) {
        val today = repo.today()
        state.rendered(h.period, h.points, today)
        chart.alpha = 1f
        when (h.period.mode) {
            Mode.DAY -> {
                val nowMinute = if (h.period.isCurrent(today)) LocalTime.now(repo.plantZone()).let { it.hour * 60 + it.minute } else null
                chart.setLine(h.points, 0.0, nowMinute)
                val top = h.points.maxByOrNull { it.value }
                val parts = mutableListOf<String>()
                h.dayKwh?.let { parts += Format.kwh(it) }
                if (top != null && top.value > 0.0) parts += getString(R.string.peak, Format.kw(top.value), Format.hhmm(top.ts))
                total.text = if (parts.isEmpty()) getString(R.string.no_data) else parts.joinToString(" · ")
            }
            Mode.MONTH, Mode.YEAR, Mode.TOTAL -> {
                val values = state.barValues(today)
                chart.setBars(values, h.points.map { barLabel(h.period.mode, it.ts) }, state.highlightIndex(today))
                total.text = if (values.all { it == null }) getString(R.string.no_data)
                else getString(R.string.period_total, Format.kwh(state.visibleTotal(today), allowMwh = true))
            }
        }
    }

    // ---- pickers ----------------------------------------------------------

    private fun jump(mode: Mode, day: LocalDate) {
        if (state.jumpTo(mode, day, repo.today())) {
            selectRadio(state.period.mode)
            load()
        }
    }

    /** Earliest selectable day: the plant's install date, else five years back. */
    private fun earliest(today: LocalDate): LocalDate = repo.installDate() ?: today.minusYears(5)

    private fun showPicker() {
        val today = repo.today()
        when (state.period.mode) {
            Mode.DAY -> showDayPicker(today)
            Mode.MONTH -> showMonthPicker(today)
            Mode.YEAR -> showYearPicker(today)
            Mode.TOTAL -> Unit
        }
    }

    private fun showDayPicker(today: LocalDate) {
        val current = state.period.date
        val dialog = DatePickerDialog(
            this,
            { _, year, monthZeroBased, day -> jump(Mode.DAY, LocalDate.of(year, monthZeroBased + 1, day)) },
            current.year, current.monthValue - 1, current.dayOfMonth,
        )
        dialog.datePicker.minDate = pickerMillis(earliest(today))
        dialog.datePicker.maxDate = pickerMillis(today)
        dialog.show()
    }

    private fun showMonthPicker(today: LocalDate) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_month_picker, null)
        val yearText = view.findViewById<TextView>(R.id.pickYear)
        val prevYear = view.findViewById<ImageButton>(R.id.pickPrevYear)
        val nextYear = view.findViewById<ImageButton>(R.id.pickNextYear)
        val grid = view.findViewById<LinearLayout>(R.id.monthGrid)
        val minMonth = YearMonth.from(earliest(today))
        val maxMonth = YearMonth.from(today)
        var year = state.period.date.year.coerceIn(minMonth.year, maxMonth.year)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.pick_month_title).setView(view).create()

        fun rebuild() {
            yearText.text = year.toString()
            prevYear.isEnabled = year > minMonth.year
            prevYear.alpha = if (prevYear.isEnabled) 1f else 0.3f
            nextYear.isEnabled = year < maxMonth.year
            nextYear.alpha = if (nextYear.isEnabled) 1f else 0.3f
            grid.removeAllViews()
            for (row in 0 until 3) {
                val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (col in 0 until 4) {
                    val m = row * 4 + col + 1
                    val ym = YearMonth.of(year, m)
                    val button = Button(this, null, android.R.attr.borderlessButtonStyle)
                    button.text = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    button.isAllCaps = false
                    button.isEnabled = !ym.isBefore(minMonth) && !ym.isAfter(maxMonth)
                    button.alpha = if (button.isEnabled) 1f else 0.3f
                    button.setTextColor(getColor(if (ym == YearMonth.from(state.period.date)) R.color.primary else R.color.text_primary))
                    button.setOnClickListener {
                        dialog.dismiss()
                        jump(Mode.MONTH, ym.atDay(1))
                    }
                    rowLayout.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
                grid.addView(rowLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }
        prevYear.setOnClickListener { year--; rebuild() }
        nextYear.setOnClickListener { year++; rebuild() }
        rebuild()
        dialog.show()
    }

    private fun showYearPicker(today: LocalDate) {
        val years = (earliest(today).year..today.year).toList().reversed()
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_year_title)
            .setItems(years.map { it.toString() }.toTypedArray()) { _, which ->
                jump(Mode.YEAR, LocalDate.of(years[which], 1, 1))
            }
            .show()
    }

    /** DatePicker interprets min/max millis in the phone's default zone, so build them there, not in UTC. */
    private fun pickerMillis(day: LocalDate): Long = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun barLabel(mode: Mode, ts: LocalDateTime): String = when (mode) {
        Mode.MONTH -> ts.dayOfMonth.toString()
        Mode.YEAR -> ts.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        Mode.TOTAL -> ts.year.toString()
        Mode.DAY -> ""
    }
}
