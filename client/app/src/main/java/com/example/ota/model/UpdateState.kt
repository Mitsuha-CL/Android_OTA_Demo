package com.example.ota.model

/**
 * OTA state machine states.
 * Sealed interface with integer codes for AIDL crossing.
 */
sealed interface UpdateState {
    val code: Int
    val label: String

    data object Idle : UpdateState {
        override val code = 0
        override val label = "Idle"
    }

    data object Checking : UpdateState {
        override val code = 1
        override val label = "Checking"
    }

    data object Downloading : UpdateState {
        override val code = 2
        override val label = "Downloading"
    }

    data object Verifying : UpdateState {
        override val code = 3
        override val label = "Verifying"
    }

    data object Installing : UpdateState {
        override val code = 4
        override val label = "Installing"
    }

    data object Success : UpdateState {
        override val code = 5
        override val label = "Success"
    }

    data object Failed : UpdateState {
        override val code = 6
        override val label = "Failed"
    }

    companion object {
        fun fromCode(code: Int): UpdateState = when (code) {
            0 -> Idle
            1 -> Checking
            2 -> Downloading
            3 -> Verifying
            4 -> Installing
            5 -> Success
            6 -> Failed
            else -> throw IllegalArgumentException("Unknown state code: $code")
        }
    }
}
