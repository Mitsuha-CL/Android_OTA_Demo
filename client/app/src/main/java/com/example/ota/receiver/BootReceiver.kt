package com.example.ota.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.ota.client.UpdateClient

/**
 * Receives BOOT_COMPLETED and triggers auto update check.
 * directBootAware=true in manifest allows receiving LOCKED_BOOT_COMPLETED
 * before user unlock (for headless/kiosk devices).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            Log.d(TAG, "Boot completed, triggering auto update check")
            val client = UpdateClient(context)
            client.bind()
            // Give service time to bind, then check
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                client.checkUpdate(object : com.example.ota.aidl.IUpdateCallback.Stub() {
                    override fun onStateChanged(state: Int, info: com.example.ota.aidl.UpdateInfo?) {
                        // State changes handled here
                    }

                    override fun onProgress(current: Long, total: Long) {
                        // Progress updates
                    }

                    override fun onError(errorCode: Int, errorMsg: String?) {
                        Log.e(TAG, "Auto check failed: $errorCode - $errorMsg")
                        client.unbind()
                    }

                    override fun onComplete(success: Boolean) {
                        Log.d(TAG, "Auto check complete: $success")
                        client.unbind()
                    }
                })
            }, 3000)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
