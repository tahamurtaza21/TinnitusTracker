package com.aiish.tinnitus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

private val DF_LABEL = SimpleDateFormat("MMM dd", Locale.getDefault())
private val DF_ISO = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

fun createLineChart(
    context: Context,
    label: String,
    values: List<Int?>,
    rangeType: String,
    startDateIso: String? = null,
    endDateIso: String? = null
): LineChart {
    val chart = LineChart(context)
    val lineData = LineData()

    // ✅ If there's no data at all (weekly / monthly / since_signup), draw grey placeholders
    if (values.isEmpty()) {
        val (startIso, endIso) = computeRange(rangeType, startDateIso, endDateIso)
        val (labels, expectedCount) = buildDateLabels(
            startIso,
            endIso,
            weekly = (rangeType == "since_signup")
        )

        val placeholderData = LineDataSet(
            (0 until expectedCount).map { Entry(it.toFloat(), 0f) },
            ""
        ).apply {
            color = Color.TRANSPARENT
            setDrawCircles(true)
            setCircleColor(Color.LTGRAY)
            circleRadius = 5f
            setDrawValues(false)
            setDrawHighlightIndicators(false)
        }

        lineData.addDataSet(placeholderData)
        chart.data = lineData
        setupChartBase(chart, labels, expectedCount)
        return chart
    }

    // ---- build contiguous segments for non-null values ----
    var current = mutableListOf<Entry>()
    values.forEachIndexed { i, v ->
        if (v != null) current.add(Entry(i.toFloat(), v.toFloat()))
        else if (current.isNotEmpty()) {
            lineData.addDataSet(styledSet(current, ""))
            current = mutableListOf()
        }
    }
    if (current.isNotEmpty()) lineData.addDataSet(styledSet(current, ""))

    // ---- ghost grey dots for missing days ----
    values.forEachIndexed { i, v ->
        if (v == null) {
            val ghost = LineDataSet(listOf(Entry(i.toFloat(), 0f)), "").apply {
                color = Color.TRANSPARENT
                setDrawCircles(true)
                setCircleColor(Color.GRAY)
                circleRadius = 5f
                setDrawValues(false)
                setDrawHighlightIndicators(false)
                formLineWidth = 0f
                formSize = 0f
            }
            lineData.addDataSet(ghost)
        }
    }

    chart.data = lineData
    val (startIso, endIso) = computeRange(rangeType, startDateIso, endDateIso)
    val (labels, expectedCount) = buildDateLabels(
        startIso,
        endIso,
        weekly = (rangeType == "since_signup")
    )

    setupChartBase(chart, labels, expectedCount)
    return chart
}

/**
 * Apply common styling to all charts
 */
private fun setupChartBase(chart: LineChart, labels: List<String>, expectedCount: Int) {
    chart.description.isEnabled = false
    chart.setDrawGridBackground(false)
    chart.setBackgroundColor(Color.WHITE)
    chart.axisRight.isEnabled = false
    chart.axisLeft.axisMinimum = 0f
    chart.axisLeft.granularity = 1f
    chart.axisLeft.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float) = value.toInt().toString()
    }

    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        granularity = 1f
        setDrawGridLines(false)
        labelRotationAngle = -45f
        axisMinimum = 0f
        axisMaximum = max(0, expectedCount - 1).toFloat()
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in labels.indices) labels[i] else ""
            }
        }
    }

    chart.legend.isEnabled = false
    chart.setTouchEnabled(false)

    chart.layout(0, 0, 800, 400)
    chart.measure(
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
    )
    chart.layout(0, 0, chart.measuredWidth, chart.measuredHeight)
}

private fun styledSet(entries: List<Entry>, label: String) =
    LineDataSet(entries, label).apply {
        color = Color.BLUE
        lineWidth = 3f
        setDrawCircles(true)
        circleRadius = 4f
        setDrawCircleHole(false)
        setDrawValues(true)
        valueTextColor = Color.BLACK
        valueTextSize = 12f
        mode = LineDataSet.Mode.LINEAR
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) = value.toInt().toString()
        }
    }

private fun computeRange(
    rangeType: String,
    startOverride: String?,
    endOverride: String?
): Pair<String, String> {
    val today = Calendar.getInstance()
    val DF_ISO = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ✅ FIXED: End date is always TODAY unless overridden
    val endIso = endOverride ?: DF_ISO.format(today.time)

    val startIso = when (rangeType) {
        "weekly" -> {
            if (startOverride != null) {
                startOverride
            } else {
                val startCal = Calendar.getInstance()
                startCal.add(Calendar.DAY_OF_YEAR, -6) // 7 days ending today
                DF_ISO.format(startCal.time)
            }
        }
        "monthly" -> {
            if (startOverride != null) {
                startOverride
            } else {
                val startCal = Calendar.getInstance()
                startCal.add(Calendar.DAY_OF_YEAR, -29) // 30 days ending today
                DF_ISO.format(startCal.time)
            }
        }
        "since_signup" -> {
            startOverride ?: run {
                val startCal = Calendar.getInstance()
                startCal.add(Calendar.MONTH, -6)
                DF_ISO.format(startCal.time)
            }
        }
        else -> {
            val startCal = Calendar.getInstance()
            startCal.add(Calendar.DAY_OF_YEAR, -6)
            DF_ISO.format(startCal.time)
        }
    }

    return startIso to endIso
}


private fun buildDateLabels(
    startIso: String,
    endIso: String,
    weekly: Boolean = false
): Pair<List<String>, Int> {
    val start = Calendar.getInstance().apply { time = DF_ISO.parse(startIso)!! }
    val end = Calendar.getInstance().apply { time = DF_ISO.parse(endIso)!! }

    val labels = mutableListOf<String>()
    val c = start.clone() as Calendar
    while (!c.after(end)) {
        labels.add(DF_LABEL.format(c.time))
        if (weekly) c.add(Calendar.WEEK_OF_YEAR, 1)
        else c.add(Calendar.DAY_OF_YEAR, 1)
    }
    return labels to labels.size
}

fun captureLineChartAsBitmap(chart: LineChart): Bitmap =
    Bitmap.createBitmap(chart.width, chart.height, Bitmap.Config.ARGB_8888).also { bmp ->
        val canvas = android.graphics.Canvas(bmp)
        chart.draw(canvas)
    }
