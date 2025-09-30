package com.example.tinnitusaiish

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

fun createLineChart(
    context: Context,
    label: String,
    values: List<Int?>,
    rangeType: String // 👈 new param
): LineChart {
    val chart = LineChart(context)
    val lineData = LineData()

    var currentSegment = mutableListOf<Entry>()

    values.forEachIndexed { index, value ->
        if (value != null) {
            currentSegment.add(Entry(index.toFloat(), value.toFloat()))
        } else {
            if (currentSegment.isNotEmpty()) {
                val segmentSet = createStyledDataSet(currentSegment, label)
                lineData.addDataSet(segmentSet)
                currentSegment = mutableListOf()
            }
        }
    }

    // Ghost points
    values.forEachIndexed { index, value ->
        if (value == null) {
            val ghostEntry = Entry(index.toFloat(), 0f)
            val ghostSet = LineDataSet(listOf(ghostEntry), "").apply {
                color = Color.TRANSPARENT
                setDrawCircles(true)
                setCircleColor(Color.GRAY)
                circleRadius = 5f
                setDrawValues(false)
                setDrawHighlightIndicators(false)
            }
            lineData.addDataSet(ghostSet)
        }
    }

    if (currentSegment.isNotEmpty()) {
        val segmentSet = createStyledDataSet(currentSegment, label)
        lineData.addDataSet(segmentSet)
    }

    chart.data = lineData
    chart.setDrawGridBackground(false)
    chart.setBackgroundColor(Color.WHITE)
    chart.description.isEnabled = false
    chart.setNoDataText("No data available")
    chart.axisRight.isEnabled = false
    chart.axisLeft.granularity = 1f
    chart.axisLeft.axisMinimum = 0f

    chart.axisLeft.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String = value.toInt().toString()
    }

    // ✅ Fix date labels depending on rangeType
    // inside createLineChart(...)
    val formatter = SimpleDateFormat("MMM dd", Locale.getDefault())
    val calendar = Calendar.getInstance()

    val dateLabels: List<String> = when (rangeType) {
        "weekly" -> {
            // last N days up to today
            calendar.add(Calendar.DAY_OF_YEAR, -(values.size - 1))
            List(values.size) {
                val formatted = formatter.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                formatted
            }
        }
        "monthly" -> {
            // start from 1st day of current month → today
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            List(values.size) {
                val formatted = formatter.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                formatted
            }
        }
        "since_signup" -> {
            // use actual signup date
            val startDate = com.example.tinnitusaiish.model.getStartDate()
            val startCal = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(startDate)!!
            }
            List(values.size) {
                val formatted = formatter.format(startCal.time)
                startCal.add(Calendar.DAY_OF_YEAR, 1)
                formatted
            }
        }
        else -> emptyList()
    }


    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        granularity = 1f
        setDrawGridLines(false)
        labelRotationAngle = -45f
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return dateLabels.getOrNull(index) ?: ""
            }

            override fun getPointLabel(entry: Entry?): String {
                val index = entry?.x?.toInt() ?: return ""
                return values.getOrNull(index)?.toString() ?: "N/A"
            }
        }
    }

    chart.setTouchEnabled(false)

    chart.layout(0, 0, 800, 400)
    chart.measure(
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
    )
    chart.layout(0, 0, chart.measuredWidth, chart.measuredHeight)

    return chart
}


private fun createStyledDataSet(entries: List<Entry>, label: String): LineDataSet {
    return LineDataSet(entries, label).apply {
        color = Color.BLUE
        valueTextColor = Color.BLACK
        valueTextSize = 12f
        setDrawCircles(true)
        setDrawValues(true)
        circleRadius = 4f
        lineWidth = 3f
        mode = LineDataSet.Mode.LINEAR
        setDrawCircleHole(false)

        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }
    }
}


fun captureLineChartAsBitmap(chart: LineChart): Bitmap {
    return Bitmap.createBitmap(chart.width, chart.height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        chart.draw(canvas)
    }
}
