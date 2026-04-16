package com.example.ota.watchdog

import android.util.Log
import com.example.ota.controller.UpdateController
import com.example.ota.model.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Retry mechanism with exponential backoff.
 * Max 3 retries, starting from 3s base delay.
 */
class WatchDog(
    private val controller: UpdateController,
    private val scope: CoroutineScope
) {

    private var retryCount = 0
    private var retryJob: Job? = null

    fun onFailed(failedState: UpdateState, errorCode: Int, errorMsg: String) {
        if (retryCount >= MAX_RETRY) {
            Log.w(TAG, "max retry reached ($MAX_RETRY), giving up. Last error: $errorMsg")
            retryCount = 0
            // State remains FAILED, client is already notified
            return
        }

        retryCount++
        val delayMs = BASE_DELAY_MS * (1L shl (retryCount - 1)) // exponential backoff
        Log.d(TAG, "retry #$retryCount after ${delayMs}ms (failed state: ${failedState.label})")

        retryJob = scope.launch {
            delay(delayMs)
            // Reset to IDLE then restart the flow
            controller.transitionTo(UpdateState.Idle)
            controller.checkUpdate(null)
        }
    }

    fun reset() {
        retryCount = 0
        retryJob?.cancel()
        retryJob = null
    }

    companion object {
        private const val TAG = "WatchDog"
        private const val MAX_RETRY = 3
        private const val BASE_DELAY_MS = 3000L // 3 seconds
    }
}
