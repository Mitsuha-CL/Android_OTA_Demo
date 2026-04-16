package com.example.ota.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.example.ota.aidl.IUpdateCallback
import com.example.ota.aidl.IUpdateService
import com.example.ota.model.ErrorCode
import com.example.ota.model.UpdateState
import com.example.ota.service.UpdateService

/**
 * Main process client that binds to the daemon UpdateService.
 * Forwards requests and manages callback lifecycle across IPC.
 */
class UpdateClient(private val context: Context) {

    private var updateService: IUpdateService? = null
    private val clientCallbacks = mutableListOf<IUpdateCallback>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            updateService = IUpdateService.Stub.asInterface(service)
            Log.d(TAG, "Service connected")
            // Re-register existing callbacks after reconnection
            synchronized(clientCallbacks) {
                for (cb in clientCallbacks) {
                    try {
                        updateService?.registerCallback(cb)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "re-register callback failed", e)
                    }
                }
            }
            onServiceConnectedListener?.invoke()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            updateService = null
            Log.w(TAG, "Service disconnected")
            notifyAllCallbacks { it.onError(ErrorCode.SERVICE_DISCONNECTED.code, "Service disconnected") }
        }
    }

    var isBound = false
        private set

    var onServiceConnectedListener: (() -> Unit)? = null

    fun bind() {
        if (isBound) return
        val intent = Intent(context, UpdateService::class.java).apply {
            action = "com.example.ota.IUpdateService"
        }
        isBound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        if (!isBound) return
        try {
            context.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            // Not bound
        }
        isBound = false
        updateService = null
    }

    fun checkUpdate(callback: IUpdateCallback) {
        registerIfNeeded(callback)
        try {
            updateService?.checkUpdate(callback)
        } catch (e: RemoteException) {
            callback.onError(ErrorCode.SERVICE_NOT_CONNECTED.code, "IPC call failed")
        }
    }

    fun startUpdate(callback: IUpdateCallback) {
        registerIfNeeded(callback)
        try {
            updateService?.startUpdate(callback)
        } catch (e: RemoteException) {
            callback.onError(ErrorCode.SERVICE_NOT_CONNECTED.code, "IPC call failed")
        }
    }

    fun getCurrentState(): UpdateState {
        return try {
            val code = updateService?.currentState ?: UpdateState.Idle.code
            UpdateState.fromCode(code)
        } catch (e: RemoteException) {
            UpdateState.Idle
        }
    }

    private fun registerIfNeeded(callback: IUpdateCallback) {
        synchronized(clientCallbacks) {
            if (!clientCallbacks.contains(callback)) {
                clientCallbacks.add(callback)
            }
        }
        try {
            updateService?.registerCallback(callback)
        } catch (e: RemoteException) {
            Log.e(TAG, "register callback failed", e)
        }
    }

    fun unregisterCallback(callback: IUpdateCallback) {
        synchronized(clientCallbacks) {
            clientCallbacks.remove(callback)
        }
        try {
            updateService?.unregisterCallback(callback)
        } catch (e: RemoteException) {
            Log.e(TAG, "unregister callback failed", e)
        }
    }

    private fun notifyAllCallbacks(action: (IUpdateCallback) -> Unit) {
        synchronized(clientCallbacks) {
            for (cb in clientCallbacks) {
                try {
                    action(cb)
                } catch (e: RemoteException) {
                    Log.e(TAG, "notify failed", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "UpdateClient"
    }
}
