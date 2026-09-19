package com.riyaz.rssdatarecovery

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val RSS_CORE_BASE_URL = "https://rsscore.cv"
private const val RSS_APP_ID = "rss-data-recovery"

internal data class RssCoreRegistration(
    val ok: Boolean,
    val customerEmail: String?,
    val displayName: String?,
    val controlPlane: String = "RSS-Core",
    val execution: String = "RAY"
)

internal data class RssCoreStatus(
    val connected: Boolean,
    val service: String?,
    val version: String?,
    val runtime: String?,
    val storage: String?,
    val ai: String?,
    val error: String? = null
)

internal object RssCoreClient {
    suspend fun register(context: Context, name: String, email: String): RssCoreRegistration =
        withContext(Dispatchers.IO) {
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ).orEmpty()

            val json = JSONObject()
                .put("email", email)
                .put("display_name", name)

            request(
                path = "/api/v1/recovery/register",
                method = "POST",
                body = json.toString()
            ).let { response ->
                if (response.code !in 200..299) {
                    throw IllegalStateException("RSS Core registration failed (HTTP ${response.code})")
                }
                val body = JSONObject(response.body)
                RssCoreRegistration(
                    ok = body.optBoolean("ok", false),
                    customerEmail = body.optJSONObject("user")?.optString("email")?.takeIf { it.isNotBlank() },
                    displayName = body.optJSONObject("user")?.optString("display_name")?.takeIf { it.isNotBlank() }
                )
            }
        }

    suspend fun status(): RssCoreStatus = withContext(Dispatchers.IO) {
        runCatching {
            val response = request("/api/v1/status", "GET")
            if (response.code !in 200..299) {
                return@runCatching RssCoreStatus(false, null, null, null, null, null, "HTTP ${response.code}")
            }
            val body = JSONObject(response.body)
            RssCoreStatus(
                connected = body.optBoolean("ok", false) && body.optString("service") == "rss-core",
                service = body.optString("service").takeIf { it.isNotBlank() },
                version = body.optString("version").takeIf { it.isNotBlank() },
                runtime = body.optString("runtime").takeIf { it.isNotBlank() },
                storage = body.optString("storage").takeIf { it.isNotBlank() },
                ai = body.optString("ai").takeIf { it.isNotBlank() }
            )
        }.getOrElse {
            RssCoreStatus(false, null, null, null, null, null, it.javaClass.simpleName)
        }
    }

    private fun request(path: String, method: String, body: String? = null): Response {
        val connection = (URL(RSS_CORE_BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-RSS-App-Id", RSS_APP_ID)

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Response(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private data class Response(val code: Int, val body: String)
}
