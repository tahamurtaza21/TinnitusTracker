package com.aiish.tinnitus.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /**
     * Signs up a new user with Firebase Authentication
     * and also stores user info in Firestore `users` collection.
     */
    suspend fun signUpUser(context: Context, email: String, password: String): Result<Unit> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                val signupDate = SimpleDateFormat("yyyy-MM-dd").format(Date())
                val userDoc = mapOf(
                    "uid" to user.uid,
                    "email" to email,
                    "name" to email.substringBefore("@"), // quick default name
                    "signupDate" to signupDate
                )
                db.collection("users").document(user.uid).set(userDoc).await()


                // Save user info in Firestore
                db.collection("users").document(user.uid).set(userDoc).await()

                // Save locally
                val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("logged_in_email", email)
                    .putString("signup_date", signupDate)
                    .apply()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logs in a user with Firebase Authentication
     * and retrieves the role from custom claims.
     *
     * @return Pair<email, role>
     *   - email: User's email (nullable if something failed)
     *   - role: "admin" if admin, null if normal user
     */
    suspend fun loginUser(
        context: Context,
        email: String,
        password: String
    ): Result<Pair<String?, String?>> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            val tokenResult = user?.getIdToken(true)?.await()
            val role = tokenResult?.claims?.get("role") as? String

            // Save locally
            val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("logged_in_email", user?.email).apply()

            Result.success(user?.email to role) // role might be null if no claim
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
