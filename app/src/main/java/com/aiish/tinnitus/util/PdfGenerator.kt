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
import com.aiish.tinnitus.model.getStartDate
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

    // ✅ FIXED: Always end on TODAY and go backwards
    val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    val today = java.util.Calendar.getInstance()
    val endIso = df.format(today.time) // TODAY

    val startCal = java.util.Calendar.getInstance()
    val startIso: String = when (reportRange) {
        "weekly" -> {
            startCal.add(java.util.Calendar.DAY_OF_YEAR, -6) // 7 days ending today
            df.format(startCal.time)
        }
        "monthly" -> {
            startCal.add(java.util.Calendar.DAY_OF_YEAR, -29) // 30 days ending today
            df.format(startCal.time)
        }
        "since_signup" -> {
            getStartDate() // user's signup date
        }
        else -> {
            startCal.add(java.util.Calendar.DAY_OF_YEAR, -6)
            df.format(startCal.time)
        }
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
    document.add(
        Paragraph("Report Period: $startIso to $endIso")
            .setItalic()
            .setFontSize(12f)
            .setMarginBottom(15f)
    )

    fun addSection(label: String, value: String) {
        document.add(Paragraph(label).setBold().setFontSize(13f))
        document.add(Paragraph(value).setFontSize(12f).setMarginBottom(10f))
    }

    val totalDays = report.tinnitusLevels.size
    val relaxPercent =
        if (totalDays > 0) report.relaxationDays.toDouble() * 100 / totalDays else 0.0
    val soundPercent =
        if (totalDays > 0) report.soundTherapyDays.toDouble() * 100 / totalDays else 0.0

    addSection(
        "Relaxation Exercises Done",
        String.format("%.1f%% (%d/%d days)", relaxPercent, report.relaxationDays, totalDays)
    )
    addSection(
        "Sound Therapy Used",
        String.format("%.1f%% (%d/%d days)", soundPercent, report.soundTherapyDays, totalDays)
    )

    // Charts
    val tinnitusChart = createLineChart(
        context = context,
        label = "Tinnitus",
        values = report.tinnitusLevels,
        rangeType = reportRange,
        startDateIso = startIso,
        endDateIso = endIso
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
        values = report.anxietyLevels,
        rangeType = reportRange,
        startDateIso = startIso,
        endDateIso = endIso
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