package com.example.tinnitusaiish.data

import android.content.Context
import com.example.tinnitusaiish.createLineChart
import com.example.tinnitusaiish.model.WeeklyReport
import com.example.tinnitusaiish.util.generatePdf
import com.example.tinnitusaiish.util.sendPdfViaEmail
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileInputStream

class ReportExporter(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {

    suspend fun exportAndSendReport(
        context: Context,
        report: WeeklyReport,
        tinnitusData: List<Int?>,
        anxietyData: List<Int?>,
        patientName: String,
        userNote: String,
        doctorEmail: String,
        reportRange: String // 👈 weekly / monthly / since_signup
    ): Result<String> {
        return try {
            // 1️⃣ Generate charts with proper range type
            val tinnitusChart = createLineChart(
                context,
                label = "Tinnitus Trend",
                values = tinnitusData,
                rangeType = reportRange
            )
            val anxietyChart = createLineChart(
                context,
                label = "Anxiety Trend",
                values = anxietyData,
                rangeType = reportRange
            )

            // ✅ Pass correctly named parameters
            val file: File = generatePdf(
                context = context,
                report = report,
                patientName = patientName,
                userNote = userNote,
                reportRange = reportRange
            )


            if (!file.exists()) {
                return Result.failure(Exception("PDF file not created"))
            }

            // 2️⃣ Upload to Firebase Storage
            val userId = auth.currentUser?.uid
                ?: return Result.failure(Exception("No logged-in user"))
            val storageRef = storage.reference.child("reports/$userId/${file.name}")

            FileInputStream(file).use { stream ->
                storageRef.putStream(stream).await()
            }

            val downloadUrl = storageRef.downloadUrl.await().toString()

            // 3️⃣ Call Cloud Function to email doctor
            val data = hashMapOf(
                "email" to doctorEmail,
                "fileUrl" to downloadUrl
            )

            functions.getHttpsCallable("sendDoctorReport").call(data).await()

            // 4️⃣ Optionally also open/send locally
            sendPdfViaEmail(context, file, doctorEmail, reportRange)

            Result.success("Report sent successfully to $doctorEmail")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
