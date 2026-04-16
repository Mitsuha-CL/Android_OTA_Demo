package com.example.ota.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ota.model.UpdateState
import com.example.ota.ui.theme.Green
import com.example.ota.ui.theme.Orange
import com.example.ota.ui.theme.Red

/**
 * Main OTA update screen composed of status indicator, version info,
 * update log, progress bar, and action buttons.
 */
@Composable
fun UpdateScreen(
    state: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onStartUpdate: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("OTA Update") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // Status Icon
            UpdateStatusIcon(state.state)

            // Version Info
            VersionInfo(state)

            // Update Log Card
            state.updateInfo?.updateLog?.takeIf { it.isNotBlank() }?.let { log ->
                UpdateLogCard(log)
            }

            // Progress Bar (visible during downloading)
            if (state.state == UpdateState.Downloading) {
                ProgressBarWithText(state.progress, state.progressText)
            }

            // Status Text
            StatusText(state.state, state.errorMessage)

            Spacer(Modifier.height(16.dp))

            // Action Buttons
            ActionButtons(state, onCheckUpdate, onStartUpdate)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UpdateStatusIcon(state: UpdateState) {
    val color = when (state) {
        UpdateState.Idle -> Color.Gray
        UpdateState.Checking -> Color.Gray
        UpdateState.Downloading -> Orange
        UpdateState.Verifying -> Color.Magenta
        UpdateState.Installing -> MaterialTheme.colorScheme.primary
        UpdateState.Success -> Green
        UpdateState.Failed -> Red
    }

    val progress by animateFloatAsState(
        targetValue = if (state in listOf(UpdateState.Checking, UpdateState.Downloading,
                UpdateState.Verifying, UpdateState.Installing)) 1f else 0f
    )

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        if (progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 4.dp
            )
        }
        Text(
            text = getIconText(state),
            fontSize = 40.sp,
            color = color
        )
    }
}

@Composable
private fun VersionInfo(state: UpdateUiState) {
    val info = state.updateInfo
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (info != null) {
            Text(
                text = "Current -> ${info.versionName}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (info.force) {
                Text(
                    text = "Required Update",
                    fontSize = 14.sp,
                    color = Red,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Text(
                text = "No update info",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun UpdateLogCard(updateLog: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Update Log",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = updateLog,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgressBarWithText(progress: Float, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatusText(state: UpdateState, errorMessage: String?) {
    val text = when (state) {
        UpdateState.Idle -> "Ready to check for updates"
        UpdateState.Checking -> "Checking for updates..."
        UpdateState.Downloading -> "Downloading update..."
        UpdateState.Verifying -> "Verifying update package..."
        UpdateState.Installing -> "Installing update..."
        UpdateState.Success -> "Update installed successfully"
        UpdateState.Failed -> errorMessage ?: "Update failed"
    }
    val color = when (state) {
        UpdateState.Failed -> Red
        UpdateState.Success -> Green
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        fontSize = 16.sp,
        color = color,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ActionButtons(
    state: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onStartUpdate: () -> Unit
) {
    when (state.state) {
        UpdateState.Idle, UpdateState.Failed -> {
            Button(
                onClick = onCheckUpdate,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(if (state.state == UpdateState.Failed) "Retry" else "Check for Update")
            }
        }
        UpdateState.Checking -> {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        UpdateState.Downloading, UpdateState.Verifying -> {
            // Buttons disabled during active download/verify
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(if (state.state == UpdateState.Downloading) "Downloading..." else "Verifying...")
            }
        }
        UpdateState.Installing -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("Installing...")
            }
        }
        UpdateState.Success -> {
            Button(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("Installed")
            }
        }
    }
}

private fun getIconText(state: UpdateState): String = when (state) {
    UpdateState.Idle -> ""
    UpdateState.Checking -> ""
    UpdateState.Downloading -> ""
    UpdateState.Verifying -> ""
    UpdateState.Installing -> ""
    UpdateState.Success -> ""
    UpdateState.Failed -> ""
}
