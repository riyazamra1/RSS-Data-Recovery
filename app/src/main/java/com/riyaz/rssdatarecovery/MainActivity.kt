package com.riyaz.rssdatarecovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : FragmentActivity() {
    private var authenticated = false
    private var showStartupSplash by mutableStateOf(true)
    private var appLocked by mutableStateOf(false)
    private var authInProgress = false
    private var resetPinAfterBiometric = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(850)
                showStartupSplash = false
            }
            if (showStartupSplash) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.rss_data_recovery_logo),
                        contentDescription = "RSS Data Recovery",
                        modifier = Modifier.size(96.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            } else RecoveryAppV3(
                appLocked = appLocked,
                onUnlock = { authenticate() },
                onPinUnlock = { pin -> unlockWithPin(pin) },
                onForgotPin = { forgotPin() }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("rss_recovery", MODE_PRIVATE)
        if (prefs.getBoolean("app_lock", false) && prefs.getBoolean("registered", false) && prefs.getBoolean("welcome_done", false) && prefs.getBoolean("features_done", false) && !authenticated) {
            appLocked = true
            if (prefs.getBoolean("biometric_enabled", false)) {
                window.decorView.post { authenticate() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        authenticated = false
        if (getSharedPreferences("rss_recovery", MODE_PRIVATE).getBoolean("app_lock", false)) appLocked = true
    }

    private fun unlockWithPin(pin: String) {
        val prefs = getSharedPreferences("rss_recovery", MODE_PRIVATE)
        val expected = prefs.getString("pin_hash", "") ?: ""
        if (expected.isNotEmpty() && hashPin(pin) == expected) {
            authenticated = true
            appLocked = false
        } else {
            Toast.makeText(this, "INCORRECT PIN", Toast.LENGTH_SHORT).show()
        }
    }

    private fun forgotPin() {
        if (authInProgress) return
        val prefs = getSharedPreferences("rss_recovery", MODE_PRIVATE)
        val email = prefs.getString("email", "")?.trim().orEmpty()
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            resetPinAfterBiometric = true
            authenticate()
            return
        }
        if (email.isEmpty()) {
            Toast.makeText(this, "REGISTERED EMAIL NOT FOUND", Toast.LENGTH_LONG).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("RESET PIN BY EMAIL")
            .setMessage("A 6-digit verification code will be sent to:\n\n$email\n\nThe code expires in 10 minutes.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SEND CODE") { _, _ -> requestResetCode(email) }
            .show()
    }

    private fun requestResetCode(email: String) {
        authInProgress = true
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                postJson("https://rsscore.cv/api/v1/recovery/request-reset", JSONObject().put("email", email))
            }
            authInProgress = false
            if (result.first) {
                showCodeDialog(email)
                Toast.makeText(this@MainActivity, "RESET CODE SENT TO YOUR EMAIL", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, result.second, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCodeDialog(email: String) {
        val input = android.widget.EditText(this).apply {
            hint = "6-DIGIT CODE"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            maxLines = 1
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("ENTER RESET CODE")
            .setMessage("Check your registered email for the 6-digit code.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("VERIFY") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length == 6) verifyResetCode(email, code) else Toast.makeText(this, "ENTER THE 6-DIGIT CODE", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun verifyResetCode(email: String, code: String) {
        authInProgress = true
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                postJson("https://rsscore.cv/api/v1/recovery/verify-reset", JSONObject().put("email", email).put("code", code))
            }
            authInProgress = false
            if (result.first) {
                val token = runCatching { JSONObject(result.second).getString("reset_token") }.getOrNull()
                if (!token.isNullOrEmpty()) showNewPinDialog(email, token, false)
                else Toast.makeText(this@MainActivity, "RESET AUTHORIZATION FAILED", Toast.LENGTH_LONG).show()
            } else Toast.makeText(this@MainActivity, result.second, Toast.LENGTH_LONG).show()
        }
    }

    private fun showNewPinDialog(email: String, resetToken: String, localBiometric: Boolean) {
        val input = android.widget.EditText(this).apply {
            hint = "NEW 6-DIGIT PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("CREATE NEW PIN")
            .setMessage("Set a new 6-digit PIN for RSS Data Recovery.")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE PIN") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length == 6 && pin.all(Char::isDigit)) {
                    if (localBiometric) saveLocalPin(pin) else consumeResetAndSavePin(email, resetToken, pin)
                } else Toast.makeText(this, "PIN MUST CONTAIN 6 DIGITS", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun saveLocalPin(pin: String) {
        getSharedPreferences("rss_recovery", MODE_PRIVATE).edit()
            .putString("pin_hash", hashPin(pin))
            .putBoolean("pin_enabled", true)
            .putBoolean("app_lock", true)
            .apply()
        authenticated = true
        appLocked = false
        Toast.makeText(this, "PIN RESET SUCCESSFULLY", Toast.LENGTH_LONG).show()
    }

    private fun consumeResetAndSavePin(email: String, token: String, pin: String) {
        authInProgress = true
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                postJson("https://rsscore.cv/api/v1/recovery/consume-reset", JSONObject().put("email", email).put("reset_token", token))
            }
            authInProgress = false
            if (result.first) saveLocalPin(pin)
            else Toast.makeText(this@MainActivity, result.second, Toast.LENGTH_LONG).show()
        }
    }

    private fun authenticate() {
        if (authInProgress) return
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            authInProgress = true
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticated = true
                    appLocked = false
                    authInProgress = false
                    if (resetPinAfterBiometric) {
                        resetPinAfterBiometric = false
                        showNewPinDialog("", "", true)
                    }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                    resetPinAfterBiometric = false
                    Toast.makeText(this@MainActivity, "APP LOCKED", Toast.LENGTH_SHORT).show()
                }
            })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (resetPinAfterBiometric) "RESET APP PIN" else "RSS DATA RECOVERY")
                .setSubtitle(if (resetPinAfterBiometric) "VERIFY YOUR BIOMETRIC TO CREATE A NEW PIN" else "UNLOCK WITH BIOMETRIC")
                .setNegativeButtonText("CANCEL")
                .build()
            prompt.authenticate(promptInfo)
        } else if (!resetPinAfterBiometric) {
            if (getSharedPreferences("rss_recovery", MODE_PRIVATE).getBoolean("pin_enabled", false)) {
                Toast.makeText(this, "USE YOUR PIN TO UNLOCK", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "SET A PIN OR BIOMETRIC TO USE APP LOCK", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun postJson(endpoint: String, body: JSONObject): Pair<Boolean, String> {
        return try {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val message = runCatching { JSONObject(text).optString("error").ifEmpty { text } }.getOrDefault(text)
            Pair(status in 200..299, if (status in 200..299) text else message.ifEmpty { "REQUEST FAILED ($status)" })
        } catch (_: Exception) {
            Pair(false, "EMAIL RESET SERVICE UNAVAILABLE")
        }
    }

    private fun hashPin(pin: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray(Charsets.UTF_8))
        .joinToString("") { it.toString(16).padStart(2, '0') }
}

fun openExternal(activity: androidx.activity.ComponentActivity, uri: String) {
    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
}
