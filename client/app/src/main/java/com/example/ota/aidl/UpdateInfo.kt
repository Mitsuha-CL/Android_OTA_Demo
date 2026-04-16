package com.example.ota.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UpdateInfo(
    var versionCode: Int = 0,
    var versionName: String = "",
    var downloadUrl: String = "",
    var md5: String = "",
    var fileSize: Long = 0L,
    var minSupportVersion: Int = 0,
    var updateLog: String = "",
    var force: Boolean = false,
    var errorCode: Int = 0,
    var errorMsg: String = ""
) : Parcelable
