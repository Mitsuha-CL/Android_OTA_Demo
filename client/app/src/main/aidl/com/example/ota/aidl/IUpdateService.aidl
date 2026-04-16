package com.example.ota.aidl;

import com.example.ota.aidl.IUpdateCallback;

interface IUpdateService {
    void checkUpdate(in IUpdateCallback callback);
    void startUpdate(in IUpdateCallback callback);
    void registerCallback(in IUpdateCallback callback);
    void unregisterCallback(in IUpdateCallback callback);
    int getCurrentState();
}
