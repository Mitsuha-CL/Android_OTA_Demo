package com.example.ota.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ota.OtaApplication
import com.example.ota.R
import com.example.ota.aidl.IUpdateCallback
import com.example.ota.aidl.IUpdateService
import com.example.ota.controller.UpdateController
import com.example.ota.model.UpdateState

class UpdateService : Service() {

    private lateinit var controller: UpdateController

    private val binder = object : IUpdateService.Stub() {
        override fun checkUpdate(callback: IUpdateCallback?) {
            if (callback != null) {
                controller.checkUpdate(callback)
            }
        }

        override fun startUpdate(callback: IUpdateCallback?) {
            if (callback != null) {
                controller.startUpdate(callback)
            }
        }

        override fun registerCallback(callback: IUpdateCallback?) {
            if (callback != null) {
                controller.addCallback(callback)
            }
        }

        override fun unregisterCallback(callback: IUpdateCallback?) {
            if (callback != null) {
                controller.removeCallback(callback)
            }
        }

        override fun getCurrentState(): Int {
            return controller.getState().code
        }
    }

    override fun onCreate() {
        super.onCreate()
        controller = UpdateController(this)
        startForeground(OtaApplication.NOTIFICATION_ID, buildNotification("OTA update"))
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.cleanup()
    }

    internal fun updateNotification(state: UpdateState) {
        val title = when (state) {
            UpdateState.Checking -> getString(R.string.notification_checking)
            UpdateState.Downloading -> getString(R.string.notification_downloading)
            UpdateState.Verifying -> getString(R.string.notification_verifying)
            UpdateState.Installing -> getString(R.string.notification_installing)
            UpdateState.Success -> getString(R.string.notification_success)
            UpdateState.Failed -> getString(R.string.notification_failed)
            else -> getString(R.string.notification_title)
        }
        val notification = buildNotification(title)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(OtaApplication.NOTIFICATION_ID, notification)
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Class.forName("com.example.ota.ui.UpdateActivity")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, OtaApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_ota)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
