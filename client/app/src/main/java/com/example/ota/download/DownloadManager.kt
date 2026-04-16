package com.example.ota.download

import android.content.Context
import android.util.Log
import com.example.ota.model.ErrorCode
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * APK download manager with resume support (Range header).
 */
class DownloadManager(
    private val context: Context,
    private val downloadDir: File = context.cacheDir
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    @Volatile
    var cancelled: Boolean = false

    interface Callback {
        fun onProgress(current: Long, total: Long)
        fun onComplete(filePath: String)
        fun onError(code: Int, msg: String)
    }

    fun download(url: String, callback: Callback) {
        cancelled = false
        val filePath = File(downloadDir, OTA_APK_FILE).absolutePath
        val existingFile = File(filePath)
        val resumeOffset = if (existingFile.exists()) existingFile.length() else 0L

        downloadWithResume(url, filePath, callback, resumeOffset)
    }

    private fun downloadWithResume(
        url: String,
        filePath: String,
        callback: Callback,
        resumeOffset: Long
    ) {
        val requestBuilder = Request.Builder().url(url)
        if (resumeOffset > 0) {
            requestBuilder.header("Range", "bytes=$resumeOffset-")
        }
        val request = requestBuilder.build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                callback.onError(ErrorCode.DOWNLOAD_FAILED.code, "HTTP ${response.code()}")
                return
            }

            val totalBytes: Long
            val downloaded: Long

            if (resumeOffset > 0) {
                // Server supports Range: Content-Range contains full size
                totalBytes = parseContentRange(response.header("Content-Range"))
                downloaded = resumeOffset
                if (totalBytes <= 0) {
                    // Server doesn't support range properly, restart download
                    File(filePath).delete()
                    downloadWithResume(url, filePath, callback, 0)
                    return
                }
            } else {
                totalBytes = response.body?.contentLength() ?: -1L
                downloaded = 0L
            }

            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(filePath, resumeOffset > 0).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var currentDownloaded = downloaded
                    var read: Int

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        if (cancelled) {
                            callback.onError(ErrorCode.DOWNLOAD_CANCELLED.code, "Download cancelled")
                            return
                        }
                        fos.write(buffer, 0, read)
                        currentDownloaded += read
                        callback.onProgress(currentDownloaded, totalBytes)
                    }
                }
            }

            callback.onComplete(filePath)

        } catch (e: IOException) {
            // Partial file kept for resume on next attempt
            Log.e(TAG, "download failed", e)
            callback.onError(ErrorCode.DOWNLOAD_FAILED.code, e.message ?: "Download error")
        } catch (e: Exception) {
            Log.e(TAG, "download unexpected error", e)
            callback.onError(ErrorCode.DOWNLOAD_FAILED.code, e.message ?: "Download error")
        }
    }

    private fun parseContentRange(contentRange: String?): Long {
        if (contentRange.isNullOrEmpty()) return -1L
        // Format: "bytes start-end/total"
        val parts = contentRange.split("/")
        if (parts.size < 2) return -1L
        return parts[1].toLongOrNull() ?: -1L
    }

    fun cancel() {
        cancelled = true
    }

    companion object {
        private const val TAG = "DownloadManager"
        private const val BUFFER_SIZE = 8 * 1024 // 8KB
        private const val OTA_APK_FILE = "ota_update.apk"
    }
}
