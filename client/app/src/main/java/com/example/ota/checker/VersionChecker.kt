package com.example.ota.checker

import android.content.Context
import android.content.pm.PackageInfo
import android.provider.Settings
import android.util.Log
import com.example.ota.BuildConfig
import com.example.ota.aidl.UpdateInfo
import com.example.ota.model.ErrorCode
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

/**
 * Communicates with the OTA server to check for updates and report results.
 * Uses OkHttp with retry logic.
 */
class VersionChecker(
    private val context: Context,
    private val baseUrl: String = BuildConfig.SERVER_BASE_URL
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    interface Callback {
        fun onUpdateFound(info: UpdateInfo)
        fun onNoUpdate()
        fun onError(code: Int, msg: String)
    }

    fun check(callback: Callback) {
        val currentVersionCode = getAppVersionCode()
        val deviceId = getDeviceId()

        val json = JsonObject().apply {
            addProperty("deviceId", deviceId)
            addProperty("versionCode", currentVersionCode)
        }

        val body = RequestBody.create("application/json".toMediaType(), json.toString())
        val request = Request.Builder()
            .url("$baseUrl/api/v1/update/check")
            .post(body)
            .build()

        executeWithRetry(request, MAX_RETRY, RETRY_DELAY_MS) { success, response, error ->
            if (!success) {
                callback.onError(ErrorCode.NETWORK_ERROR.code, error ?: "Unknown network error")
                return@executeWithRetry
            }

            try {
                val data = response?.getAsJsonObject("data") ?: run {
                    callback.onError(ErrorCode.UNKNOWN_ERROR.code, "Empty response")
                    return@executeWithRetry
                }

                val hasUpdate = data.get("hasUpdate")?.asBoolean ?: false
                if (!hasUpdate) {
                    callback.onNoUpdate()
                    return@executeWithRetry
                }

                val info = UpdateInfo().apply {
                    versionCode = data.get("versionCode")?.asInt ?: 0
                    versionName = data.get("versionName")?.asString ?: ""
                    downloadUrl = data.get("downloadUrl")?.asString ?: ""
                    md5 = data.get("md5")?.asString ?: ""
                    fileSize = data.get("fileSize")?.asLong ?: 0L
                    minSupportVersion = data.get("minSupportVersion")?.asInt ?: 0
                    updateLog = data.get("updateLog")?.asString ?: ""
                    force = data.get("force")?.asBoolean ?: false
                }

                callback.onUpdateFound(info)
            } catch (e: Exception) {
                Log.e(TAG, "parse response failed", e)
                callback.onError(ErrorCode.UNKNOWN_ERROR.code, "Parse error: ${e.message}")
            }
        }
    }

    fun report(versionCode: Int, status: String, errorMsg: String) {
        val json = JsonObject().apply {
            addProperty("deviceId", getDeviceId())
            addProperty("versionCode", versionCode)
            addProperty("status", status)
            addProperty("errorMsg", errorMsg)
        }

        val body = RequestBody.create("application/json".toMediaType(), json.toString())
        val request = Request.Builder()
            .url("$baseUrl/api/v1/update/report")
            .post(body)
            .build()

        // Fire-and-forget, no retries
        executeWithRetry(request, 1, 0) { success, _, error ->
            if (!success) {
                Log.e(TAG, "report update result failed: $error")
            }
        }
    }

    private fun executeWithRetry(
        request: Request,
        maxRetries: Int,
        initialDelayMs: Long,
        onComplete: (success: Boolean, response: JsonObject?, error: String?) -> Unit
    ) {
        var lastError: String? = null
        for (attempt in 0 until maxRetries) {
            if (attempt > 0 && initialDelayMs > 0) {
                val delay = initialDelayMs * (1L shl (attempt - 1))
                Thread.sleep(delay)
            }

            try {
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = gson.fromJson(body, JsonObject::class.java)
                    onComplete(true, json, null)
                    return
                } else {
                    lastError = "HTTP ${response.code}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "request failed (attempt ${attempt + 1})", e)
                lastError = e.message
            }
        }
        onComplete(false, null, lastError)
    }

    private fun getAppVersionCode(): Int {
        return try {
            val info: PackageInfo = context.packageManager
                .getPackageInfo(context.packageName, 0)
            info.longVersionCode.toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    companion object {
        private const val TAG = "VersionChecker"
        private const val MAX_RETRY = 3
        private const val RETRY_DELAY_MS = 2000L
    }
}
