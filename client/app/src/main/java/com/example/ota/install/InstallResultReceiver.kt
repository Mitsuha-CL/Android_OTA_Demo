package com.example.ota.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.example.ota.model.ErrorCode
import com.example.ota.OtaApplication

/**
 * BroadcastReceiver that receives PackageInstaller session commit results.
 * Must run in the :ota process (same process that created the session).
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_SUCCESS) {
            Log.i(TAG, "Install succeeded")
            // Notify UpdateController via local broadcast or EventBus
            val notifyIntent = Intent(ACTION_INSTALL_RESULT).apply {
                putExtra(EXTRA_SUCCESS, true)
                setPackage(context.packageName)
            }
            context.sendBroadcast(notifyIntent)
        } else {
            Log.e(TAG, "Install failed: $message")
            val notifyIntent = Intent(ACTION_INSTALL_RESULT).apply {
                putExtra(EXTRA_SUCCESS, false)
                putExtra(EXTRA_ERROR_MSG, message)
                setPackage(context.packageName)
            }
            context.sendBroadcast(notifyIntent)
        }
    }

    companion object {
        private const val TAG = "InstallResultReceiver"
        const val ACTION_INSTALL_RESULT = "com.example.ota.INSTALL_RESULT"
        const val EXTRA_SUCCESS = "extra_success"
        const val EXTRA_ERROR_MSG = "extra_error_msg"
    }
}
