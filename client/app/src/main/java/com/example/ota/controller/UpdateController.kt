package com.example.ota.controller

import android.content.Context
import android.os.RemoteException
import android.util.Log
import com.example.ota.aidl.IUpdateCallback
import com.example.ota.aidl.UpdateInfo
import com.example.ota.checker.VersionChecker
import com.example.ota.download.DownloadManager
import com.example.ota.install.Installer
import com.example.ota.model.ErrorCode
import com.example.ota.model.UpdateState
import com.example.ota.service.UpdateService
import com.example.ota.verify.VerifyManager
import com.example.ota.watchdog.WatchDog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * OTA flow orchestration and state machine.
 * Serial execution: check → download → verify → install.
 */
class UpdateController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentState: UpdateState = UpdateState.Idle
    private val callbacks = mutableListOf<IUpdateCallback>()
    private var currentUpdateInfo: UpdateInfo? = null

    private val versionChecker = VersionChecker(context)
    private val downloadManager = DownloadManager(context)
    private val verifyManager = VerifyManager()
    private val installer = Installer(context)
    private val watchDog = WatchDog(this, scope)

    fun checkUpdate(callback: IUpdateCallback) {
        if (currentState != UpdateState.Idle) {
            notifyError(callback, ErrorCode.STATE_ILLEGAL, "Cannot check update in state: $currentState")
            return
        }

        addCallback(callback)
        transitionTo(UpdateState.Checking)

        versionChecker.check(object : VersionChecker.Callback {
            override fun onUpdateFound(info: UpdateInfo) {
                currentUpdateInfo = info
                transitionTo(UpdateState.Downloading)
                notifyState(UpdateState.Downloading, info)
                startDownload()
            }

            override fun onNoUpdate() {
                transitionTo(UpdateState.Idle)
                notifyState(UpdateState.Idle, null)
                notifyCallbacks { it.onComplete(false) }
                watchDog.reset()
            }

            override fun onError(code: Int, msg: String) {
                transitionTo(UpdateState.Failed)
                notifyError(code, msg)
                watchDog.onFailed(UpdateState.Checking, code, msg)
            }
        })
    }

    fun startUpdate(callback: IUpdateCallback) {
        // startUpdate triggers the full flow (check → download → verify → install)
        checkUpdate(callback)
    }

    private fun startDownload() {
        val info = currentUpdateInfo ?: return
        downloadManager.download(info.downloadUrl, object : DownloadManager.Callback {
            override fun onProgress(current: Long, total: Long) {
                notifyCallbacks { it.onProgress(current, total) }
            }

            override fun onComplete(filePath: String) {
                transitionTo(UpdateState.Verifying)
                notifyState(UpdateState.Verifying, null)
                startVerify(filePath)
            }

            override fun onError(code: Int, msg: String) {
                transitionTo(UpdateState.Failed)
                notifyError(code, msg)
                watchDog.onFailed(UpdateState.Downloading, code, msg)
            }
        })
    }

    private fun startVerify(filePath: String) {
        val info = currentUpdateInfo ?: return
        val pass = verifyManager.verify(filePath, info.md5)
        if (pass) {
            transitionTo(UpdateState.Installing)
            notifyState(UpdateState.Installing, null)
            startInstall(filePath)
        } else {
            transitionTo(UpdateState.Failed)
            notifyError(ErrorCode.VERIFY_FAILED, "APK verify failed")
            verifyManager.deleteInvalidFile(filePath)
            watchDog.onFailed(UpdateState.Verifying, ErrorCode.VERIFY_FAILED.code, "APK verify failed")
        }
    }

    private fun startInstall(filePath: String) {
        installer.install(filePath, object : Installer.Callback {
            override fun onSuccess() {
                transitionTo(UpdateState.Success)
                notifyState(UpdateState.Success, null)
                notifyCallbacks { it.onComplete(true) }
                watchDog.reset()
                reportUpdateResult(currentUpdateInfo?.versionCode ?: 0, "SUCCESS", "")
            }

            override fun onFailure(code: Int, msg: String) {
                transitionTo(UpdateState.Failed)
                notifyError(code, msg)
                watchDog.onFailed(UpdateState.Installing, code, msg)
                reportUpdateResult(currentUpdateInfo?.versionCode ?: 0, "INSTALL_FAILED", msg)
            }
        })
    }

    private fun reportUpdateResult(versionCode: Int, status: String, errorMsg: String) {
        scope.launch {
            try {
                versionChecker.report(versionCode, status, errorMsg)
            } catch (e: Exception) {
                Log.e(TAG, "report update result failed", e)
            }
        }
    }

    internal fun transitionTo(newState: UpdateState) {
        val oldState = currentState
        currentState = newState
        Log.d(TAG, "State: ${oldState.label} -> ${newState.label}")
        if (context is UpdateService) {
            context.updateNotification(newState)
        }
    }

    fun addCallback(callback: IUpdateCallback) {
        synchronized(callbacks) {
            if (!callbacks.contains(callback)) {
                callbacks.add(callback)
            }
        }
    }

    fun removeCallback(callback: IUpdateCallback) {
        synchronized(callbacks) {
            callbacks.remove(callback)
        }
    }

    fun getState(): UpdateState = currentState

    fun cleanup() {
        scope.cancel()
        callbacks.clear()
    }

    private fun notifyState(state: UpdateState, info: UpdateInfo?) {
        notifyCallbacks { it.onStateChanged(state.code, info) }
    }

    private fun notifyError(code: Int, msg: String) {
        notifyCallbacks { it.onError(code, msg) }
    }

    private fun notifyError(callback: IUpdateCallback, errorCode: ErrorCode, msg: String) {
        try {
            callback.onError(errorCode.code, msg)
        } catch (e: RemoteException) {
            Log.e(TAG, "notify error failed", e)
        }
    }

    private fun notifyCallbacks(action: (IUpdateCallback) -> Unit) {
        val snapshot = synchronized(callbacks) { callbacks.toList() }
        for (cb in snapshot) {
            try {
                action(cb)
            } catch (e: RemoteException) {
                Log.e(TAG, "notify callback failed, removing", e)
                removeCallback(cb)
            }
        }
    }

    companion object {
        private const val TAG = "UpdateController"
    }
}
