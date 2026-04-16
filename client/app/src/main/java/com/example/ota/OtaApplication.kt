package com.example.ota

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class OtaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (isMainProcess()) {
            createNotificationChannel()
        }
    }

    private fun isMainProcess(): Boolean {
        val processName = getCurrentProcessName(this)
        return processName == null || processName == packageName
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationManager.IMPORTANCE_LOW
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ota_update_channel"
        const val NOTIFICATION_ID = 1001

        fun getCurrentProcessName(context: Context): String? {
            val pid = android.os.Process.myPid()
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (processInfo in manager.runningAppProcesses) {
                if (processInfo.pid == pid) {
                    return processInfo.processName
                }
            }
            return null
        }
    }
}
