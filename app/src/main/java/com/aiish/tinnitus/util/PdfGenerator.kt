package com.aiish.tinnitus.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.aiish.tinnitus.captureLineChartAsBitmap
import com.aiish.tinnitus.createLineChart
import com.aiish.tinnitus.model.WeeklyReport
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.io.source.ByteArrayOutputStream
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun openPdf(context: Context, file: File) {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    context.startActivity(intent)
}

fun generatePdfWithChartData(
    context: Context,
    report: WeeklyReport,
    tinnitusData: List<Int?>,
    anxietyData: List<Int?>,
    patientName: String,
    userNote: String,
    reportRange: String,
    startDateIso: String,
    endDateIso: String
): File {
    val reportLabel = when (reportRange) {
        "weekly" -> "Weekly"
        "monthly" -> "Monthly"
        "since_signup" -> "Full History"
        else -> "Report"
    }

    val fileName = "Tinnitus_${reportLabel}_Report.pdf"
    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val file = File(downloadsDir, fileName)

    val outputStream = FileOutputStream(file)
    val writer = PdfWriter(outputStream)
    val pdfDoc = PdfDocument(writer)
    val document = Document(pdfDoc)

    // Title
    document.add(
        Paragraph("Tinnitus $reportLabel Report")
            .setFontSize(20f)
            .setBold()
            .setMarginBottom(10f)
    )

    // Patient name + date range
    document.add(Paragraph("Patient: $patientName").setFontSize(13f).setBold())

    val dateRangeText = when (reportRange) {
        "weekly" -> "Report Period: $startDateIso to $endDateIso (Last 7 days)"
        "monthly" -> "Report Period: $startDateIso to $endDateIso (Current month)"
        "since_signup" -> "Report Period: $startDateIso to $endDateIso (Full history)"
        else -> "Report Period: $startDateIso to $endDateIso"
    }

    document.add(
        Paragraph(dateRangeText)
            .setItalic()
            .setFontSize(12f)
            .setMarginBottom(15f)
    )

    fun addSection(label: String, value: String) {
        document.add(Paragraph(label).setBold().setFontSize(13f))
        document.add(Paragraph(value).setFontSize(12f).setMarginBottom(10f))
    }

    val weeks = when (reportRange) {
        "since_signup" -> (report.tinnitusLevels.size / 7).coerceAtLeast(1)
        "monthly" -> 4
        else -> 1
    }

    val totalDays = if (reportRange == "since_signup") {
        weeks * 7
    } else {
        tinnitusData.size  // ✅ Use padded data size
    }

    val relaxPercent = if (totalDays > 0) {
        (report.relaxationDays * 100 / totalDays)
    } else 0

    val soundPercent = if (totalDays > 0) {
        (report.soundTherapyDays * 100 / totalDays)
    } else 0

    addSection("Total Weeks", weeks.toString())
    addSection(
        "Relaxation Exercises Done",
        "$relaxPercent% of days (${report.relaxationDays}/$totalDays days)"
    )
    addSection(
        "Sound Therapy Used",
        "$soundPercent% of days (${report.soundTherapyDays}/$totalDays days)"
    )

    // ✅ Generate charts with PADDED data (includes nulls for missing days)
    val tinnitusChart = createLineChart(
        context = context,
        label = "Tinnitus",
        values = tinnitusData,  // ✅ Use padded data!
        rangeType = reportRange,
        startDateIso = startDateIso,
        endDateIso = endDateIso
    )
    val tinnitusBmp: Bitmap = captureLineChartAsBitmap(tinnitusChart)
    val tinStream = ByteArrayOutputStream()
    tinnitusBmp.compress(Bitmap.CompressFormat.PNG, 100, tinStream)
    document.add(Paragraph("Tinnitus Level Trend").setBold().setMarginTop(10f))
    document.add(Image(ImageDataFactory.create(tinStream.toByteArray())).apply { setAutoScale(true) })

    val tinnitusAvg = report.tinnitusLevels.filterNotNull().average().let {
        if (it.isNaN()) 0.0 else it
    }
    document.add(
        Paragraph("Average Tinnitus Level: ${String.format("%.2f", tinnitusAvg)}")
            .setFontSize(12f)
            .setMarginBottom(10f)
    )

    val anxietyChart = createLineChart(
        context = context,
        label = "Anxiety",
        values = anxietyData,  // ✅ Use padded data!
        rangeType = reportRange,
        startDateIso = startDateIso,
        endDateIso = endDateIso
    )
    val anxietyBmp: Bitmap = captureLineChartAsBitmap(anxietyChart)
    val anxStream = ByteArrayOutputStream()
    anxietyBmp.compress(Bitmap.CompressFormat.PNG, 100, anxStream)
    document.add(Paragraph("Anxiety Level Trend").setBold().setMarginTop(10f))
    document.add(Image(ImageDataFactory.create(anxStream.toByteArray())).apply { setAutoScale(true) })

    val anxietyAvg = report.anxietyLevels.filterNotNull().average().let {
        if (it.isNaN()) 0.0 else it
    }
    document.add(
        Paragraph("Average Anxiety Level: ${String.format("%.2f", anxietyAvg)}")
            .setFontSize(12f)
            .setMarginBottom(10f)
    )

    document.add(Paragraph("\nAdditional Notes:").setBold())
    document.add(
        Paragraph(userNote)
            .setItalic()
            .setFontSize(11f)
            .setMarginBottom(20f)
    )

    document.add(
        Paragraph("Generated by Tinnitus App")
            .setFontSize(10f)
            .setItalic()
            .setMarginTop(30f)
            .setTextAlignment(TextAlignment.CENTER)
    )

    document.close()
    return file
}

fun generatePdf(
    context: Context,
    report: WeeklyReport,
    patientName: String,
    userNote: String,
    reportRange: String
): File {
    val reportLabel = when (reportRange) {
        "weekly" -> "Weekly"
        "monthly" -> "Monthly"
        "since_signup" -> "Full History"
        else -> "Report"
    }

    // ✅ MATCH ReportScreen.kt EXACTLY
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = Calendar.getInstance()
    val endIso = df.format(today.time) // TODAY

    val startIso: String = when (reportRange) {
        "weekly" -> {
            // ✅ EXACTLY like ReportScreen: today minus 6 days
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
            df.format(cal.time)
        }
        "monthly" -> {
            // ✅ EXACTLY like ReportScreen: first day of current month
            val cStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
            df.format(cStart.time)
        }
        "since_signup" -> {
            // ✅ Use first check-in date from report data
            report.tinnitusLevels.indices.firstOrNull()?.let {
                val cal = Calendar.getInstance()
                cal.add(Calendar.WEEK_OF_YEAR, -report.tinnitusLevels.size)
                df.format(cal.time)
            } ?: df.format(today.time)
        }
        else -> df.format(today.time)
    }

    val fileName = "Tinnitus_${reportLabel}_Report.pdf"
    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val file = File(downloadsDir, fileName)

    val outputStream = FileOutputStream(file)
    val writer = PdfWriter(outputStream)
    val pdfDoc = PdfDocument(writer)
    val document = Document(pdfDoc)

    // Title
    document.add(
        Paragraph("Tinnitus $reportLabel Report")
            .setFontSize(20f)
            .setBold()
            .setMarginBottom(10f)
    )

    // Patient name + date range
    document.add(Paragraph("Patient: $patientName").setFontSize(13f).setBold())

    // ✅ Format date range exactly like ReportScreen
    val dateRangeText = when (reportRange) {
        "weekly" -> {
            "Report Period: $startIso to $endIso (Last 7 days)"
        }
        "monthly" -> {
            val monthEnd = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            val endOfMonth = df.format(monthEnd.time)
            "Report Period: $startIso to $endOfMonth (Current month)"
        }
        "since_signup" -> {
            "Report Period: $startIso to $endIso (Full history)"
        }
        else -> "Report Period: $startIso to $endIso"
    }

    document.add(
        Paragraph(dateRangeText)
            .setItalic()
            .setFontSize(12f)
            .setMarginBottom(15f)
    )

    fun addSection(label: String, value: String) {
        document.add(Paragraph(label).setBold().setFontSize(13f))
        document.add(Paragraph(value).setFontSize(12f).setMarginBottom(10f))
    }

    // ✅ Calculate metrics exactly like ReportScreen
    val weeks = when (reportRange) {
        "since_signup" -> (report.tinnitusLevels.size / 7).coerceAtLeast(1)
        "monthly" -> 4
        else -> 1
    }

    val totalDays = if (reportRange == "since_signup") {
        weeks * 7
    } else {
        report.tinnitusLevels.size
    }

    val relaxPercent = if (totalDays > 0) {
        (report.relaxationDays * 100 / totalDays)
    } else 0

    val soundPercent = if (totalDays > 0) {
        (report.soundTherapyDays * 100 / totalDays)
    } else 0

    addSection("Total Weeks", weeks.toString())
    addSection(
        "Relaxation Exercises Done",
        "$relaxPercent% of days (${report.relaxationDays}/$totalDays days)"
    )
    addSection(
        "Sound Therapy Used",
        "$soundPercent% of days (${report.soundTherapyDays}/$totalDays days)"
    )

    // ✅ Generate charts with SAME data as ReportScreen
    val tinnitusChart = createLineChart(
        context = context,
        label = "Tinnitus",
        values = report.tinnitusLevels, // ✅ Use the SAME data from report
        rangeType = reportRange,
        startDateIso = startIso,
        endDateIso = if (reportRange == "monthly") {
            val monthEnd = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            df.format(monthEnd.time)
        } else endIso
    )
    val tinnitusBmp: Bitmap = captureLineChartAsBitmap(tinnitusChart)
    val tinStream = ByteArrayOutputStream()
    tinnitusBmp.compress(Bitmap.CompressFormat.PNG, 100, tinStream)
    document.add(Paragraph("Tinnitus Level Trend").setBold().setMarginTop(10f))
    document.add(Image(ImageDataFactory.create(tinStream.toByteArray())).apply { setAutoScale(true) })

    val tinnitusAvg = report.tinnitusLevels.filterNotNull().average().let {
        if (it.isNaN()) 0.0 else it
    }
    document.add(
        Paragraph("Average Tinnitus Level: ${String.format("%.2f", tinnitusAvg)}")
            .setFontSize(12f)
            .setMarginBottom(10f)
    )

    val anxietyChart = createLineChart(
        context = context,
        label = "Anxiety",
        values = report.anxietyLevels, // ✅ Use the SAME data from report
        rangeType = reportRange,
        startDateIso = startIso,
        endDateIso = if (reportRange == "monthly") {
            val monthEnd = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            df.format(monthEnd.time)
        } else endIso
    )
    val anxietyBmp: Bitmap = captureLineChartAsBitmap(anxietyChart)
    val anxStream = ByteArrayOutputStream()
    anxietyBmp.compress(Bitmap.CompressFormat.PNG, 100, anxStream)
    document.add(Paragraph("Anxiety Level Trend").setBold().setMarginTop(10f))
    document.add(Image(ImageDataFactory.create(anxStream.toByteArray())).apply { setAutoScale(true) })

    val anxietyAvg = report.anxietyLevels.filterNotNull().average().let {
        if (it.isNaN()) 0.0 else it
    }
    document.add(
        Paragraph("Average Anxiety Level: ${String.format("%.2f", anxietyAvg)}")
            .setFontSize(12f)
            .setMarginBottom(10f)
    )

    document.add(Paragraph("\nAdditional Notes:").setBold())
    document.add(
        Paragraph(userNote)
            .setItalic()
            .setFontSize(11f)
            .setMarginBottom(20f)
    )

    document.add(
        Paragraph("Generated by Tinnitus App")
            .setFontSize(10f)
            .setItalic()
            .setMarginTop(30f)
            .setTextAlignment(TextAlignment.CENTER)
    )

    document.close()
    return file
}

fun sendPdfViaEmail(
    context: Context,
    file: File,
    toEmail: String,
    reportRange: String
) {
    val label = when (reportRange) {
        "weekly" -> "Weekly"
        "monthly" -> "Monthly"
        "since_signup" -> "Full History"
        else -> "Report"
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
        putExtra(Intent.EXTRA_SUBJECT, "Tinnitus $label Report")
        putExtra(Intent.EXTRA_TEXT, "Attached is the latest $label report.")
    }

    context.packageManager.queryIntentActivities(intent, 0).forEach { ri ->
        context.grantUriPermission(
            ri.activityInfo.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    context.startActivity(Intent.createChooser(intent, "Send report via email"))
}