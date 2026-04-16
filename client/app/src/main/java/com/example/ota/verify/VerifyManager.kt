package com.example.ota.verify

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * APK file integrity verification using MD5.
 */
class VerifyManager {

    /**
     * Verify APK file integrity against expected MD5.
     */
    fun verify(filePath: String, expectedMd5: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "APK file not found: $filePath")
            return false
        }

        val actualMd5 = calculateMd5(file)
        if (actualMd5.isEmpty()) {
            Log.e(TAG, "Failed to calculate MD5")
            return false
        }

        val match = actualMd5.equals(expectedMd5, ignoreCase = true)
        Log.d(TAG, "MD5 verify: expected=$expectedMd5, actual=$actualMd5, match=$match")
        return match
    }

    private fun calculateMd5(file: File): String {
        return try {
            val md = MessageDigest.getInstance(ALGORITHM)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            val digest = md.digest()
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "calculate md5 failed", e)
            ""
        }
    }

    fun deleteInvalidFile(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            val deleted = file.delete()
            Log.d(TAG, "delete invalid APK: $filePath, deleted=$deleted")
        }
    }

    companion object {
        private const val TAG = "VerifyManager"
        private const val ALGORITHM = "MD5"
        private const val BUFFER_SIZE = 8 * 1024
    }
}
