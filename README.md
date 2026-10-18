# Idtra Android SDK (Kotlin)

Official Android SDK for embedding **Idtra Biometric Face Verification** and **Active Liveness Detection** into native Android applications.

---

## 1. Installation

### Gradle (Settings / build.gradle)

Add the dependency to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.idtra:biometrics-sdk:1.0.0")
}
```

---

## 2. Permissions

Add camera and internet permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
```

---

## 3. Usage Example

In your `Activity` or `Fragment`:

```kotlin
import com.idtra.biometrics.IdtraKYC

class MainActivity : AppCompatActivity() {

    private val idtraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val jsonResponse = result.data?.getStringExtra(IdtraKYC.RESULT_JSON)
            Log.d("Idtra", "Verification Succeeded: $jsonResponse")
            Toast.makeText(this, "Biometrics Verified!", Toast.LENGTH_SHORT).show()
        } else {
            val error = result.data?.getStringExtra(IdtraKYC.RESULT_ERROR)
            Log.e("Idtra", "Verification Failed: $error")
            Toast.makeText(this, "Verification Failed: $error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchIdtraVerification() {
        IdtraKYC.startVerification(
            context = this,
            launcher = idtraLauncher,
            apiKey = "sk_live_...",
            serviceType = "NIN", // or "BVN"
            number = "12345678901"
        )
    }
}
```

---

## License

MIT © [Idtra Technologies Ltd](https://idtra.com)
