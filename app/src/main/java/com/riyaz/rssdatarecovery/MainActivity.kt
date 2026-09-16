package com.riyaz.rssdatarecovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RecoveryApp() }
    }
}

fun openExternal(activity: ComponentActivity, uri: String) {
    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
}
