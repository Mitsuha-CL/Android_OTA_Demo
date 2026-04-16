package com.example.ota.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ota.aidl.IUpdateCallback
import com.example.ota.aidl.UpdateInfo
import com.example.ota.client.UpdateClient
import com.example.ota.model.UpdateState
import com.example.ota.ui.theme.OTATheme

/**
 * Main OTA UI activity using Jetpack Compose.
 * Manages UpdateClient lifecycle and exposes UI state to composables.
 */
class UpdateActivity : ComponentActivity() {

    private lateinit var updateClient: UpdateClient
    private var uiState by mutableStateOf(UpdateUiState())

    private val callback = object : IUpdateCallback.Stub() {
        override fun onStateChanged(state: Int, info: UpdateInfo?) {
            runOnUiThread {
                uiState = uiState.copy(
                    state = UpdateState.fromCode(state),
                    updateInfo = info,
                    errorMessage = null
                )
            }
        }

        override fun onProgress(current: Long, total: Long) {
            runOnUiThread {
                val progress = if (total > 0) current.toFloat() / total else 0f
                val percentText = if (total > 0) "${(progress * 100).toInt()}%" else ""
                uiState = uiState.copy(
                    progress = progress,
                    progressText = percentText
                )
            }
        }

        override fun onError(errorCode: Int, errorMsg: String?) {
            runOnUiThread {
                uiState = uiState.copy(
                    state = UpdateState.Failed,
                    errorMessage = errorMsg ?: "Unknown error"
                )
            }
        }

        override fun onComplete(success: Boolean) {
            runOnUiThread {
                if (success) {
                    uiState = uiState.copy(state = UpdateState.Success)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateClient = UpdateClient(this)
        updateClient.onServiceConnectedListener = {
            // Service ready, could query current state
            val currentState = updateClient.getCurrentState()
            if (currentState != UpdateState.Idle) {
                uiState = uiState.copy(state = currentState)
            }
        }

        setContent {
            OTATheme {
                // Block back press during forced active update
                BackHandler(enabled = shouldBlockBack()) {
                    // Do nothing - block back press
                }

                UpdateScreen(
                    state = uiState,
                    onCheckUpdate = { checkForUpdate() },
                    onStartUpdate = { startUpdate() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateClient.bind()
    }

    override fun onStop() {
        super.onStop()
        updateClient.unbind()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            updateClient.unregisterCallback(callback)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun checkForUpdate() {
        try {
            updateClient.checkUpdate(callback)
        } catch (e: Exception) {
            Log.e(TAG, "check update failed", e)
            uiState = uiState.copy(
                state = UpdateState.Failed,
                errorMessage = "Failed to connect to update service"
            )
        }
    }

    private fun startUpdate() {
        try {
            updateClient.startUpdate(callback)
        } catch (e: Exception) {
            Log.e(TAG, "start update failed", e)
            uiState = uiState.copy(
                state = UpdateState.Failed,
                errorMessage = "Failed to start update"
            )
        }
    }

    private fun shouldBlockBack(): Boolean {
        return uiState.updateInfo?.force == true &&
                uiState.state !in listOf(UpdateState.Idle, UpdateState.Success, UpdateState.Failed)
    }

    companion object {
        private const val TAG = "UpdateActivity"
    }
}

/**
 * UI state exposed to Compose.
 */
data class UpdateUiState(
    val state: UpdateState = UpdateState.Idle,
    val updateInfo: UpdateInfo? = null,
    val progress: Float = 0f,
    val progressText: String = "",
    val errorMessage: String? = null
)
