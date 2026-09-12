package app.solarmonitor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import app.solarmonitor.R
import app.solarmonitor.model.Point
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas-drawn chart: a power line over one day, or energy bars. No libraries.
 * Bar mode keeps one slot per value; a null value leaves its slot empty so the
 * axis stays stable while the current month fills in.
 */
class ChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Called with the bar index when the user taps a bar (bar mode only). */
    var onBarTap: ((Int) -> Unit)? = null

    /** Called with the sample under the finger while inspecting a line, or null when the selection is cleared. */
    var onInspect: ((Point?) -> Unit)? = null

    private enum class ChartMode { NONE, LINE, BARS }

    private var mode = ChartMode.NONE
    private var line: List<Point> = emptyList()
    private var nowMinute: Int? = null
    private var bars: List<Double?> = emptyList()
    private var labels: List<String> = emptyList()
    private var highlightIndex = -1
    private var yMax = 1.0
    /** Minute-of-day the user is inspecting; survives data refreshes so the marker does not jump. */
    private var inspectMinute: Int? = null
    private var inspected: Point? = null
    private var downX = 0f
    private var downY = 0f
    private var claimedGesture = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val gridPaint = Paint().apply {
        color = context.getColor(R.color.chart_grid)
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_text)
        textSize = dp(10f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_line)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_fill)
        style = Paint.Style.FILL
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_bar)
        style = Paint.Style.FILL
    }
    private val barHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_bar_highlight)
        style = Paint.Style.FILL
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_marker)
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val inspectLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_marker)
        strokeWidth = dp(1.5f)
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_line)
        style = Paint.Style.FILL
    }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.tile)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_primary)
        textSize = dp(12f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.bg)
        style = Paint.Style.FILL
    }
    private val labelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.chart_grid)
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }
    private val path = Path()
    private val rect = RectF()

    private var plotLeft = 0f
    private var plotRight = 0f
    private var plotTop = 0f
    private var plotBottom = 0f

    /**
     * Shows a day's power curve. The Y axis is at least [minYMax] high (pass the
     * plant's nominal kW, or 0.0). [nowMinute] draws a dashed "now" marker when set.
     */
    fun setLine(points: List<Point>, minYMax: Double, nowMinute: Int? = null) {
        line = points
        this.nowMinute = nowMinute
        bars = emptyList()
        labels = emptyList()
        mode = ChartMode.LINE
        yMax = ChartMath.niceMax(max(minYMax, points.maxOfOrNull { it.value } ?: 0.0))
        // Keep the inspected time across refreshes; re-resolve it against the new samples.
        inspected = inspectMinute?.let { m -> ChartMath.nearestSampleIndex(m, sampleMinutes())?.let { points[it] } }
        invalidate()
        if (inspectMinute != null) onInspect?.invoke(inspected)
    }

    /** Removes the inspection marker. */
    fun clearInspect() {
        if (inspectMinute == null && inspected == null) return
        inspectMinute = null
        inspected = null
        invalidate()
        onInspect?.invoke(null)
    }

    /** Shows energy bars, one slot per entry; null entries stay empty. [highlightIndex] is drawn in the accent colour. */
    fun setBars(values: List<Double?>, barLabels: List<String>, highlightIndex: Int = -1) {
        bars = values
        labels = barLabels
        this.highlightIndex = highlightIndex
        line = emptyList()
        nowMinute = null
        inspected = null
        inspectMinute = null
        mode = ChartMode.BARS
        yMax = ChartMath.niceMax(values.filterNotNull().maxOrNull() ?: 0.0)
        invalidate()
    }

    fun clear() {
        mode = ChartMode.NONE
        line = emptyList()
        bars = emptyList()
        labels = emptyList()
        nowMinute = null
        inspected = null
        inspectMinute = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        plotLeft = dp(40f)
        plotRight = width - dp(6f)
        plotTop = dp(12f)
        plotBottom = height - dp(20f)
        if (plotRight <= plotLeft || plotBottom <= plotTop) return

        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..ChartMath.STEPS) {
            val value = yMax * i / ChartMath.STEPS
            val y = ChartMath.yForValue(value, plotTop, plotBottom, yMax)
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
            canvas.drawText(ChartMath.gridLabel(value), plotLeft - dp(6f), y + textPaint.textSize / 3f, textPaint)
        }

        when (mode) {
            ChartMode.LINE -> drawLine(canvas)
            ChartMode.BARS -> drawBars(canvas)
            ChartMode.NONE -> Unit
        }
    }

    private fun drawLine(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        val labelY = height - dp(4f)
        for (hour in 0..24 step 4) {
            val x = ChartMath.xForMinute(hour * 60, plotLeft, plotRight)
            canvas.drawText(String.format(Locale.US, "%02d", hour), x, labelY, textPaint)
        }
        nowMinute?.let { minute ->
            if (minute in 0..1440) {
                val x = ChartMath.xForMinute(minute, plotLeft, plotRight)
                canvas.drawLine(x, plotTop, x, plotBottom, markerPaint)
            }
        }
        if (line.isEmpty()) return

        path.reset()
        var started = false
        for (p in line) {
            val x = ChartMath.xForMinute(p.ts.hour * 60 + p.ts.minute, plotLeft, plotRight)
            val y = ChartMath.yForValue(p.value, plotTop, plotBottom, yMax)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, linePaint)

        val first = line.first()
        val last = line.last()
        path.lineTo(ChartMath.xForMinute(last.ts.hour * 60 + last.ts.minute, plotLeft, plotRight), plotBottom)
        path.lineTo(ChartMath.xForMinute(first.ts.hour * 60 + first.ts.minute, plotLeft, plotRight), plotBottom)
        path.close()
        canvas.drawPath(path, fillPaint)

        inspected?.let { drawInspect(canvas, it) }
    }

    private fun drawInspect(canvas: Canvas, p: Point) {
        val x = ChartMath.xForMinute(p.ts.hour * 60 + p.ts.minute, plotLeft, plotRight)
        val y = ChartMath.yForValue(p.value, plotTop, plotBottom, yMax)
        canvas.drawLine(x, plotTop, x, plotBottom, inspectLinePaint)
        canvas.drawCircle(x, y, dp(5.5f), dotRingPaint)
        canvas.drawCircle(x, y, dp(3.5f), dotPaint)

        val text = String.format(Locale.US, "%02d:%02d · %.1f kW", p.ts.hour, p.ts.minute, p.value)
        val padX = dp(8f)
        val padY = dp(4f)
        val textW = labelPaint.measureText(text)
        val boxW = textW + padX * 2
        val boxH = labelPaint.textSize + padY * 2
        val left = (x - boxW / 2f).coerceIn(plotLeft, max(plotLeft, plotRight - boxW))
        rect.set(left, plotTop, left + boxW, plotTop + boxH)
        canvas.drawRoundRect(rect, boxH / 2f, boxH / 2f, labelBgPaint)
        canvas.drawRoundRect(rect, boxH / 2f, boxH / 2f, labelBorderPaint)
        canvas.drawText(text, rect.centerX(), rect.bottom - padY - labelPaint.descent() / 2f, labelPaint)
    }

    private fun sampleMinutes(): List<Int> = line.map { it.ts.hour * 60 + it.ts.minute }

    private fun inspectAt(x: Float) {
        val idx = ChartMath.nearestSampleIndex(ChartMath.minuteForX(x, plotLeft, plotRight), sampleMinutes()) ?: return
        val p = line[idx]
        inspectMinute = p.ts.hour * 60 + p.ts.minute
        inspected = p
        invalidate()
        onInspect?.invoke(p)
    }

    private fun drawBars(canvas: Canvas) {
        if (bars.isEmpty()) return
        val slot = (plotRight - plotLeft) / bars.size
        val barWidth = max(slot * 0.66f, dp(1.5f))
        val radius = min(barWidth / 2f, dp(3f))
        val labelEvery = when {
            bars.size > 16 -> 5
            bars.size > 12 -> 2
            else -> 1
        }
        val labelY = height - dp(4f)
        textPaint.textAlign = Paint.Align.CENTER

        canvas.save()
        canvas.clipRect(plotLeft, plotTop, plotRight, plotBottom + dp(0.5f))
        for ((i, value) in bars.withIndex()) {
            if (value == null || value <= 0.0) continue
            val x0 = plotLeft + slot * i + (slot - barWidth) / 2f
            val y = ChartMath.yForValue(value, plotTop, plotBottom, yMax)
            rect.set(x0, y, x0 + barWidth, plotBottom + radius)
            canvas.drawRoundRect(rect, radius, radius, if (i == highlightIndex) barHighlightPaint else barPaint)
        }
        canvas.restore()

        for (i in bars.indices) {
            if (i % labelEvery == 0 && i < labels.size) {
                val cx = plotLeft + slot * i + slot / 2f
                canvas.drawText(labels[i], cx, labelY, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (mode) {
            ChartMode.BARS -> {
                if (onBarTap == null) return super.onTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val idx = ChartMath.barIndexAt(event.x, plotLeft, plotRight, bars.size)
                    if (idx != null) {
                        performClick()
                        onBarTap?.invoke(idx)
                    }
                }
                return true
            }
            ChartMode.LINE -> {
                if (line.isEmpty()) return super.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        claimedGesture = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Claim the gesture only once it is clearly horizontal, so a vertical swipe
                        // that starts on the chart still scrolls the page around it.
                        if (!claimedGesture) {
                            val dx = kotlin.math.abs(event.x - downX)
                            val dy = kotlin.math.abs(event.y - downY)
                            if (dx > touchSlop && dx > dy) {
                                claimedGesture = true
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                        if (claimedGesture) inspectAt(event.x)
                    }
                    MotionEvent.ACTION_UP -> {
                        performClick()
                        val moved = kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop
                        if (!claimedGesture && !moved) {
                            // A plain tap: on the existing marker it clears, anywhere else it places.
                            val markerX = inspected?.let { ChartMath.xForMinute(it.ts.hour * 60 + it.ts.minute, plotLeft, plotRight) }
                            if (markerX != null && kotlin.math.abs(event.x - markerX) <= dp(16f)) clearInspect() else inspectAt(event.x)
                        }
                    }
                }
                return true
            }
            ChartMode.NONE -> return super.onTouchEvent(event)
        }
    }

    override fun performClick(): Boolean = super.performClick()
}
