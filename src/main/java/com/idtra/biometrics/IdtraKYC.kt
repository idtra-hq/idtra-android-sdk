package com.idtra.biometrics

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher

object IdtraKYC {

    const val EXTRA_API_KEY = "com.idtra.biometrics.API_KEY"
    const val EXTRA_SERVICE_TYPE = "com.idtra.biometrics.SERVICE_TYPE"
    const val EXTRA_NUMBER = "com.idtra.biometrics.NUMBER"
    const val EXTRA_BASE_URL = "com.idtra.biometrics.BASE_URL"
    
    const val RESULT_SUCCESS = "com.idtra.biometrics.RESULT_SUCCESS"
    const val RESULT_JSON = "com.idtra.biometrics.RESULT_JSON"
    const val RESULT_ERROR = "com.idtra.biometrics.RESULT_ERROR"

    var defaultBaseUrl: String = "https://idtra.com"

    /**
     * Start the Biometric Face Verification Activity.
     *
     * @param launcher ActivityResultLauncher configured to receive the verification result intent.
     * @param apiKey Your Idtra secret or public API Key.
     * @param serviceType "NIN" or "BVN"
     * @param number 11-digit NIN or BVN number
     */
    fun startVerification(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        apiKey: String,
        serviceType: String = "NIN",
        number: String,
        baseUrl: String = defaultBaseUrl
    ) {
        val intent = Intent(context, IdtraVerificationActivity::class.java).apply {
            putExtra(EXTRA_API_KEY, apiKey)
            putExtra(EXTRA_SERVICE_TYPE, serviceType.uppercase())
            putExtra(EXTRA_NUMBER, number)
            putExtra(EXTRA_BASE_URL, baseUrl)
        }
        launcher.launch(intent)
    }
}
