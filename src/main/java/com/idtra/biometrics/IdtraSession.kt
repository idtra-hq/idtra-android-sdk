package com.idtra.biometrics

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.CustomTabsIntent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Outcome of a hosted verification session (state only: no personal data on the device).
 * status: completed, failed, expired, or cancelled. Read the full result on your server
 * (GET /api/biometrics/session/{id}/result with your API key) or receive it on your webhook.
 */
data class IdtraSessionResult(
    val sessionId: String,
    val status: String,
    val isMatch: Boolean?,
    val confidence: Double?,
    val message: String?,
    val rawJson: String,
) {
    val isVerified: Boolean get() = status == "completed" && isMatch == true
}

/**
 * v2 (recommended): your SERVER creates the session with your secret key
 * (POST https://idtra.com/api/biometrics/session/create) and passes only the returned verification_url to the app.
 * This opens Idtra's hosted live check in a Chrome Custom Tab (camera works natively) and calls [onResult] on the
 * main thread when the session finishes, or with status "expired" after [timeoutMs].
 */
object IdtraSession {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val finalStates = setOf("completed", "failed", "expired")

    fun start(context: Context, verificationUrl: String, timeoutMs: Long = 15 * 60 * 1000L, onResult: (IdtraSessionResult) -> Unit) {
        val uri = Uri.parse(verificationUrl)
        val match = Regex("/verify/s/([A-Za-z0-9_]+)").find(uri.path ?: "")
            ?: throw IllegalArgumentException("verificationUrl is not an Idtra verification link")
        val sessionId = match.groupValues[1]
        val statusUrl = uri.buildUpon().path("/api/biometrics/session/$sessionId/status").clearQuery().build().toString()

        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)

        val main = Handler(Looper.getMainLooper())
        val worker = Executors.newSingleThreadExecutor()
        val deadline = System.currentTimeMillis() + timeoutMs
        worker.execute {
            var last = JSONObject().put("status", "in_progress")
            while (System.currentTimeMillis() < deadline) {
                try {
                    http.newCall(Request.Builder().url(statusUrl).header("Accept", "application/json").build()).execute().use { res ->
                        last = JSONObject(res.body?.string() ?: "{}")
                    }
                    if (last.optString("status") in finalStates) break
                } catch (_: Exception) { /* keep polling */ }
                Thread.sleep(2000)
            }
            val status = last.optString("status").let { if (it in finalStates) it else "expired" }
            val result = IdtraSessionResult(
                sessionId = sessionId,
                status = status,
                isMatch = if (last.isNull("is_match")) null else last.optBoolean("is_match"),
                confidence = if (last.isNull("confidence")) null else last.optDouble("confidence"),
                message = last.optString("match_message").ifEmpty { last.optString("error_message") }.ifEmpty { null },
                rawJson = last.toString(),
            )
            main.post { onResult(result) }
            worker.shutdown()
        }
    }
}
