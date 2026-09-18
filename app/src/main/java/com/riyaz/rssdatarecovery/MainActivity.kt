package com.riyaz.rssdatarecovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent

class MainActivity : FragmentActivity() {
    private var authenticated = false
    private var authInProgress = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RecoveryAppV3() }
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("rss_recovery", MODE_PRIVATE)
        if (prefs.getBoolean("app_lock", false) && prefs.getBoolean("registered", false) && prefs.getBoolean("welcome_done", false) && !authenticated) {
            authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        authenticated = false
    }

    private fun authenticate() {
        if (authInProgress) return
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            authInProgress = true
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticated = true
                    authInProgress = false
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                    Toast.makeText(this@MainActivity, "APP LOCKED", Toast.LENGTH_SHORT).show()
                }
            })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("RSS DATA RECOVERY")
                .setSubtitle("UNLOCK WITH BIOMETRIC")
                .setNegativeButtonText("CANCEL")
                .build())
        } else {
            authenticated = true
            Toast.makeText(this, "BIOMETRIC IS NOT AVAILABLE ON THIS DEVICE", Toast.LENGTH_SHORT).show()
        }
    }
}

fun openExternal(activity: ComponentActivity, uri: String) {
    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
}
