package com.example.ota.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.example.ota.model.ErrorCode
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Silent install using PackageInstaller API.
 * Requires system app with INSTALL_PACKAGES permission.
 */
class Installer(private val context: Context) {

    interface Callback {
        fun onSuccess()
        fun onFailure(code: Int, msg: String)
    }

    fun install(apkPath: String, callback: Callback) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )

        val sessionId: Int
        try {
            sessionId = packageInstaller.createSession(params)
        } catch (e: IOException) {
            callback.onFailure(ErrorCode.INSTALL_FAILED.code, "create session failed: ${e.message}")
            return
        }

        var session: PackageInstaller.Session? = null
        try {
            session = packageInstaller.openSession(sessionId)

            // Write APK to session
            FileInputStream(apkPath).use { input ->
                session.openWrite("ota_install", 0, -1).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                    session.fsync(output)
                }
            }

            // Submit install with PendingIntent to receive result
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            session.commit(pendingIntent.intentSender)
            // Result delivered via InstallResultReceiver

        } catch (e: IOException) {
            session?.abort()
            callback.onFailure(ErrorCode.INSTALL_FAILED.code, e.message ?: "Install error")
        } finally {
            session?.close()
        }
    }

    companion object {
        private const val TAG = "Installer"
        private const val BUFFER_SIZE = 64 * 1024 // 64KB
    }
}
