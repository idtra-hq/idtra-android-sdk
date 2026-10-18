package com.idtra.biometrics

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class IdtraVerificationActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private lateinit var apiKey: String
    private lateinit var serviceType: String
    private lateinit var number: String
    private lateinit var baseUrl: String

    private val httpClient = OkHttpClient()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required for face verification", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiKey = intent.getStringExtra(IdtraKYC.EXTRA_API_KEY) ?: ""
        serviceType = intent.getStringExtra(IdtraKYC.EXTRA_SERVICE_TYPE) ?: "NIN"
        number = intent.getStringExtra(IdtraKYC.EXTRA_NUMBER) ?: ""
        baseUrl = intent.getStringExtra(IdtraKYC.EXTRA_BASE_URL) ?: IdtraKYC.defaultBaseUrl

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)

                // Trigger liveness capture after brief alignment delay
                scope.launch {
                    delay(2500)
                    takeSelfieAndVerify()
                }
            } catch (exc: Exception) {
                returnResult(false, null, exc.localizedMessage ?: "Camera init failed")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeSelfieAndVerify() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(cacheDir, "idtra_selfie_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val base64Image = encodeBitmapToBase64(bitmap)
                    photoFile.delete()

                    scope.launch {
                        submitBiometricVerification(base64Image)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    scope.launch {
                        returnResult(false, null, exc.localizedMessage ?: "Photo capture failed")
                    }
                }
            }
        )
    }

    private suspend fun submitBiometricVerification(base64Image: String) = withContext(Dispatchers.IO) {
        val endpoint = if (serviceType == "BVN") {
            "$baseUrl/api/biometrics/bvn-verify"
        } else {
            "$baseUrl/api/biometrics/nin-verify"
        }

        val jsonPayload = gson.toJson(
            mapOf(
                serviceType.lowercase() to number,
                "image" to "data:image/jpeg;base64,$base64Image"
            )
        )

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    returnResult(true, bodyString, null)
                } else {
                    returnResult(false, bodyString, "Verification rejected by server (${response.code})")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                returnResult(false, null, e.localizedMessage ?: "Network connection failed")
            }
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun returnResult(success: Boolean, jsonResponse: String?, error: String?) {
        val resultIntent = Intent().apply {
            putExtra(IdtraKYC.RESULT_SUCCESS, success)
            putExtra(IdtraKYC.RESULT_JSON, jsonResponse)
            putExtra(IdtraKYC.RESULT_ERROR, error)
        }
        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scope.cancel()
    }
}
