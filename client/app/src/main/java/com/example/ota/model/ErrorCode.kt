package com.example.ota.model

/**
 * Error codes used across the OTA system.
 * Enum for easy AIDL crossing and lookup.
 */
enum class ErrorCode(val code: Int, val message: String) {
    // General
    SUCCESS(0, "Success"),
    UNKNOWN_ERROR(-1, "Unknown error"),

    // Service
    SERVICE_NOT_CONNECTED(1001, "Service not connected"),
    SERVICE_DISCONNECTED(1002, "Service disconnected"),
    STATE_ILLEGAL(1003, "Illegal state"),

    // Network
    NETWORK_ERROR(2001, "Network error"),
    TIMEOUT_ERROR(2002, "Timeout"),

    // Download
    DOWNLOAD_FAILED(3001, "Download failed"),
    DOWNLOAD_CANCELLED(3002, "Download cancelled"),

    // Verify
    VERIFY_FAILED(4001, "Verify failed"),

    // Install
    INSTALL_FAILED(5001, "Install failed"),
    INSTALL_PERMISSION_DENIED(5002, "Install permission denied"),

    // Server error mappings
    SERVER_INVALID_PARAM(6001, "Invalid parameter"),
    SERVER_DEVICE_NOT_FOUND(6002, "Device not found"),
    SERVER_VERSION_NOT_FOUND(6003, "Version not found"),
    SERVER_RATE_LIMITED(6004, "Rate limited"),
    SERVER_INTERNAL_ERROR(6005, "Internal error");

    companion object {
        fun fromCode(code: Int): ErrorCode? = values().find { it.code == code }
    }
}
