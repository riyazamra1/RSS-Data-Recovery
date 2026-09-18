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
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : FragmentActivity() {
    private var authenticated = false
    private var appLocked by mutableStateOf(false)
    private var authInProgress = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RecoveryAppV3(appLocked = appLocked, onUnlock = { authenticate() }, onForgotPin = { forgotPin() }) }
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("rss_recovery", MODE_PRIVATE)
        if (prefs.getBoolean("app_lock", false) && prefs.getBoolean("registered", false) && prefs.getBoolean("welcome_done", false) && !authenticated) {
            appLocked = true
            authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        authenticated = false
        if (getSharedPreferences("rss_recovery", MODE_PRIVATE).getBoolean("app_lock", false)) appLocked = true
    }

    private fun forgotPin() {
        if (authInProgress) return
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "BIOMETRIC IS REQUIRED TO RESET YOUR PIN", Toast.LENGTH_LONG).show()
            return
        }
        authInProgress = true
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                getSharedPreferences("rss_recovery", MODE_PRIVATE).edit().remove("pin_hash").putBoolean("pin_enabled", false).apply()
                authenticated = true
                appLocked = false
                authInProgress = false
                Toast.makeText(this@MainActivity, "PIN RESET. SET A NEW PIN IN SETTINGS.", Toast.LENGTH_LONG).show()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("RESET APP PIN")
            .setSubtitle("VERIFY BIOMETRIC TO RESET YOUR PIN")
            .setNegativeButtonText("CANCEL")
            .build())
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
                    appLocked = false
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
