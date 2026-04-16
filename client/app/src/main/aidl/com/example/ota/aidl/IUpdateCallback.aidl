package com.example.ota.aidl;

import com.example.ota.aidl.UpdateInfo;

oneway interface IUpdateCallback {
    void onStateChanged(int state, in UpdateInfo info);
    void onProgress(long current, long total);
    void onError(int errorCode, in String errorMsg);
    void onComplete(boolean success);
}
