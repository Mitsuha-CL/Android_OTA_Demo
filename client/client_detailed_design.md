# Android OTA 客户端详细设计文档

## 一、项目概述

### 1.1 背景

在定制 Android 设备上实现应用 OTA 远程更新能力，采用"主进程 + 守护进程"多进程架构，确保更新流程的高可靠性和独立性。

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| 强制更新 | 不可跳过，服务端控制策略 |
| 静默安装 | 无需用户操作，通过 PackageInstaller API |
| 高可靠 | 断点续传、重试机制、崩溃恢复 |
| 可扩展 | 支持后续灰度发布、回滚等 |

### 1.3 技术约束

| 项 | 说明 |
|----|------|
| 系统应用 | 必须为 system app，预装于 /system/priv-app |
| 签名一致 | 与系统签名一致，获取 INSTALL_PACKAGES 权限 |
| 独立进程 | UpdateService 运行在 :ota 独立进程 |
| 前台服务 | UpdateService 必须为前台服务，避免被杀 |
| 网络 | 使用 HTTPS 与服务端通信 |

---

## 二、系统架构

### 2.1 多进程架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Main Process (default)                    │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ UpdateActivity│  │BootReceiver  │  │   UpdateClient       │   │
│  │  (UI 层)     │  │(开机自启)    │  │   - bindService      │   │
│  └──────┬───────┘  └──────┬───────┘  │   - 发起请求          │   │
│         └──────────┬──────┘          │   - 接收回调          │   │
│                    │                 └──────────┬───────────┘   │
│                    │                            │                │
├────────────────────┼────────────────────────────┼────────────────┤
│                    │         AIDL (Binder IPC)  │                │
│                    ▼                            ▼                │
│              ┌──────────────────────────────────────────────┐   │
│              │         IUpdateService (AIDL 接口)            │   │
│              └──────────────────────┬───────────────────────┘   │
├─────────────────────────────────────┼───────────────────────────┤
│                        Daemon Process (:ota)                     │
│                                     │                            │
│              ┌──────────────────────▼───────────────────────┐   │
│              │            UpdateService (Foreground Service) │   │
│              │  - 暴露 AIDL 服务接口                         │   │
│              │  - 持有 UpdateController 状态机               │   │
│              │  - 管理前台通知                               │   │
│              └──────────────────────┬───────────────────────┘   │
│                                     │                           │
│              ┌──────────────────────┼───────────────────────┐   │
│              │                      │                       │   │
│        ┌─────▼─────┐        ┌──────▼──────┐         ┌───────▼──┐
│        │UpdateCtrl │        │  WatchDog   │         │  EventBus│
│        │(流程调度)  │        │ (重试/恢复) │         │ (内部通信)│
│        └─────┬─────┘        └──────▲──────┘         └──────────┘
│              │                     │                      │
│    ┌─────────┼──────────┐          │                      │
│    ▼         ▼          ▼          │                      │
│ ┌──────┐ ┌───────┐ ┌────────┐      │                      │
│ │VerChk│ │DlMgr  │ │Install │◀─────┘                      │
│ │(检查) │ │(下载) │ │(安装)  │                             │
│ └──────┘ └───────┘ └────────┘                             │
│    │          │          │                                  │
│    ▼          ▼          ▼                                  │
│ ┌─────────────────────────────────┐                        │
│ │       Network / File System     │                        │
│ └─────────────────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 进程通信方式

| 通信方向 | 方式 | 说明 |
|----------|------|------|
| 主进程 → 守护进程 | AIDL (Binder) | IUpdateService 接口调用 |
| 守护进程 → 主进程 | AIDL Callback | IUpdateCallback 回调接口 |
| 守护进程内部 | LiveData / EventBus | 模块间通信 |

### 2.3 模块清单

| 模块 | 所在进程 | 职责 |
|------|----------|------|
| UpdateClient | 主进程 | 绑定守护进程服务，转发请求与回调 |
| UpdateActivity | 主进程 | UI 展示更新状态，处理用户交互 |
| BootReceiver | 主进程 | 开机广播，触发自动检查更新 |
| UpdateService | 守护进程 | 前台服务，暴露 AIDL 接口 |
| UpdateController | 守护进程 | OTA 流程调度与状态机管理 |
| VersionChecker | 守护进程 | HTTP 请求服务端版本接口 |
| DownloadManager | 守护进程 | APK 文件下载、断点续传、进度 |
| VerifyManager | 守护进程 | APK 文件完整性校验（MD5） |
| Installer | 守护进程 | 静默安装执行（PackageInstaller） |
| WatchDog | 守护进程 | 失败重试、崩溃恢复、指数退避 |

---

## 三、AIDL 接口设计

### 3.1 IUpdateService.aidl（守护进程 → 主进程暴露）

```java
// IUpdateService.aidl
package com.example.ota.aidl;

import com.example.ota.aidl.IUpdateCallback;

interface IUpdateService {
    /**
     * 检查更新
     * 异步调用，结果通过 IUpdateCallback 回调
     */
    void checkUpdate(in IUpdateCallback callback);

    /**
     * 开始下载并安装
     * 异步调用，结果通过 IUpdateCallback 回调
     */
    void startUpdate(in IUpdateCallback callback);

    /**
     * 注册状态监听回调
     */
    void registerCallback(in IUpdateCallback callback);

    /**
     * 注销状态监听回调
     */
    void unregisterCallback(in IUpdateCallback callback);

    /**
     * 获取当前更新状态
     */
    int getCurrentState();
}
```

### 3.2 IUpdateCallback.aidl（守护进程 → 主进程回调）

```java
// IUpdateCallback.aidl
package com.example.ota.aidl;

import com.example.ota.aidl.UpdateInfo;

oneway interface IUpdateCallback {
    /**
     * 状态变更通知
     * @param state 当前状态（见状态机定义）
     * @param info  附加信息（可能包含进度、错误等）
     */
    void onStateChanged(int state, in UpdateInfo info);

    /**
     * 下载进度回调
     * @param current  已下载字节
     * @param total    总字节数
     */
    void onProgress(long current, long total);

    /**
     * 错误通知
     * @param errorCode 错误码
     * @param errorMsg  错误描述
     */
    void onError(int errorCode, in String errorMsg);

    /**
     * 更新完成
     * @param success 是否成功
     */
    void onComplete(boolean success);
}
```

### 3.3 UpdateInfo.aidl（AIDL Parcelable）

```java
// UpdateInfo.aidl
package com.example.ota.aidl;

parcelable UpdateInfo;
```

```java
// UpdateInfo.java
package com.example.ota.aidl;

import android.os.Parcel;
import android.os.Parcelable;

public class UpdateInfo implements Parcelable {
    public int versionCode;
    public String versionName;
    public String downloadUrl;
    public String md5;
    public long fileSize;
    public int minSupportVersion;
    public String updateLog;
    public boolean force;
    public int errorCode;
    public String errorMsg;

    // ... Parcelable 实现
}
```

### 3.4 AndroidManifest 服务声明

```xml
<service
    android:name=".service.UpdateService"
    android:process=":ota"
    android:exported="false"
    android:foregroundServiceType="dataSync">
    <intent-filter>
        <action android:name="com.example.ota.IUpdateService" />
    </intent-filter>
</service>
```

---

## 四、状态机设计

### 4.1 状态定义

```java
public final class UpdateState {
    public static final int IDLE        = 0;   // 空闲
    public static final int CHECKING    = 1;   // 正在检查更新
    public static final int DOWNLOADING = 2;   // 正在下载
    public static final int VERIFYING   = 3;   // 正在校验
    public static final int INSTALLING  = 4;   // 正在安装
    public static final int SUCCESS     = 5;   // 更新成功
    public static final int FAILED      = 6;   // 更新失败
}
```

### 4.2 状态转移表

| 当前状态 | 事件 | 下一状态 | 说明 |
|----------|------|----------|------|
| IDLE | startCheck() | CHECKING | 开始检查更新 |
| CHECKING | onUpdateFound() | DOWNLOADING | 发现更新，开始下载 |
| CHECKING | onNoUpdate() | IDLE | 已是最新 |
| CHECKING | onError() | FAILED | 检查失败 |
| DOWNLOADING | onDownloadDone() | VERIFYING | 下载完成，开始校验 |
| DOWNLOADING | onError() | FAILED | 下载失败 |
| VERIFYING | onVerifyPass() | INSTALLING | 校验通过，开始安装 |
| VERIFYING | onVerifyFail() | FAILED | 校验失败 |
| INSTALLING | onInstallSuccess() | SUCCESS | 安装成功 |
| INSTALLING | onInstallFail() | FAILED | 安装失败 |
| FAILED | WatchDog 重试 | CHECKING | 重试检查 |
| FAILED | 超过最大重试 | IDLE | 放弃重试 |
| SUCCESS | — | IDLE | 流程结束 |

### 4.3 状态转移图

```
                    startCheck()
  [IDLE] ──────────────────────▶ [CHECKING]
    ▲                               │
    │                  onNoUpdate() │ onNoUpdate
    │          ◀───────────────────┘
    │                               │
    │        onUpdateFound()        ▼
    │          ◀──────────── [DOWNLOADING]
    │                               │
    │           onError() ──────────┼──▶ [FAILED] ◀──┐
    │                               │        ▲       │
    │                               ▼        │       │
    │                          [VERIFYING]   │ Retry │
    │                               │        │       │
    │        onVerifyFail() ────────┼──▶ [FAILED]    │
    │                               │        ▲       │
    │                               ▼        │       │
    │                         [INSTALLING]   │       │
    │                               │        │       │
    │ onInstallFail() ──────────────┼──▶ [FAILED]    │
    │                               │                │
    │                               ▼                │
    │                          [SUCCESS] ────────────┘
    │                               │
    │          reset()              │
    └───────────────────────────────┘
```

---

## 五、核心模块设计

### 5.1 UpdateClient（主进程）

```java
public class UpdateClient {
    private Context context;
    private IUpdateService updateService;
    private final List<IUpdateCallback> callbacks = new CopyOnWriteArrayList<>();
    private final ServiceConnection connection;

    // 绑定守护进程服务
    public void bind() {
        Intent intent = new Intent(context, UpdateService.class);
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    // 解绑
    public void unbind() {
        context.unbindService(connection);
    }

    // 检查更新
    public void checkUpdate(IUpdateCallback callback) {
        if (updateService == null) {
            callback.onError(ErrorCode.SERVICE_NOT_CONNECTED, "Service not bound");
            return;
        }
        updateService.checkUpdate(callback);
    }

    // 开始更新
    public void startUpdate(IUpdateCallback callback) {
        if (updateService == null) {
            callback.onError(ErrorCode.SERVICE_NOT_CONNECTED, "Service not bound");
            return;
        }
        updateService.startUpdate(callback);
    }

    // 注册回调
    public void addCallback(IUpdateCallback callback) {
        callbacks.add(callback);
        if (updateService != null) {
            updateService.registerCallback(callback);
        }
    }

    // 注销回调
    public void removeCallback(IUpdateCallback callback) {
        callbacks.remove(callback);
        if (updateService != null) {
            updateService.unregisterCallback(callback);
        }
    }
}
```

#### 服务连接管理

```java
private final ServiceConnection connection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        updateService = IUpdateService.Stub.asInterface(service);
        // 重连后重新注册已有回调
        for (IUpdateCallback cb : callbacks) {
            try {
                updateService.registerCallback(cb);
            } catch (RemoteException e) {
                Log.e(TAG, "re-register callback failed", e);
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        updateService = null;
        // Binder 断开，通知客户端
        for (IUpdateCallback cb : callbacks) {
            cb.onError(ErrorCode.SERVICE_DISCONNECTED, "Service disconnected");
        }
    }
};
```

### 5.2 UpdateService（守护进程）

```java
public class UpdateService extends Service {

    private UpdateController controller;
    private final IUpdateService.Stub binder = new IUpdateService.Stub() {
        @Override
        public void checkUpdate(IUpdateCallback callback) {
            controller.checkUpdate(callback);
        }

        @Override
        public void startUpdate(IUpdateCallback callback) {
            controller.startUpdate(callback);
        }

        @Override
        public void registerCallback(IUpdateCallback callback) {
            controller.addCallback(callback);
        }

        @Override
        public void unregisterCallback(IUpdateCallback callback) {
            controller.removeCallback(callback);
        }

        @Override
        public int getCurrentState() {
            return controller.getState();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        controller = new UpdateController(this);
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OTA 更新服务")
                .setContentText("正在检查更新...")
                .setSmallIcon(R.drawable.ic_ota)
                .setOngoing(true)
                .build();
    }
}
```

### 5.3 UpdateController（流程调度）

```java
public class UpdateController {

    @IntDef({UpdateState.IDLE, UpdateState.CHECKING, UpdateState.DOWNLOADING,
             UpdateState.VERIFYING, UpdateState.INSTALLING, UpdateState.SUCCESS,
             UpdateState.FAILED})
    @interface State {}

    private @State int currentState = UpdateState.IDLE;
    private final Context context;
    private final List<IUpdateCallback> callbacks = new CopyOnWriteArrayList<>();

    // 模块实例
    private VersionChecker versionChecker;
    private DownloadManager downloadManager;
    private VerifyManager verifyManager;
    private Installer installer;
    private WatchDog watchDog;

    // 当前更新信息
    private UpdateInfo currentUpdateInfo;

    public UpdateController(Context context) {
        this.context = context;
        this.versionChecker = new VersionChecker(context);
        this.downloadManager = new DownloadManager(context);
        this.verifyManager = new VerifyManager();
        this.installer = new Installer(context);
        this.watchDog = new WatchDog(this);
    }

    /**
     * 检查更新
     * 流程：IDLE → CHECKING → (DOWNLOADING or IDLE or FAILED)
     */
    public void checkUpdate(IUpdateCallback callback) {
        if (currentState != UpdateState.IDLE) {
            callback.onError(ErrorCode.STATE_ILLEGAL, "Cannot check update in state: " + currentState);
            return;
        }

        addCallback(callback);
        transitionTo(UpdateState.CHECKING);

        versionChecker.check(new VersionChecker.Callback() {
            @Override
            public void onUpdateFound(UpdateInfo info) {
                currentUpdateInfo = info;
                transitionTo(UpdateState.DOWNLOADING);
                notifyCallbacks(c -> c.onStateChanged(UpdateState.DOWNLOADING, info));
                startDownload();
            }

            @Override
            public void onNoUpdate() {
                transitionTo(UpdateState.IDLE);
                notifyCallbacks(c -> c.onStateChanged(UpdateState.IDLE, null));
                notifyCallbacks(c -> c.onComplete(false));
            }

            @Override
            public void onError(int code, String msg) {
                transitionTo(UpdateState.FAILED);
                notifyCallbacks(c -> c.onError(code, msg));
                watchDog.onFailed(UpdateState.CHECKING, code, msg);
            }
        });
    }

    /**
     * 开始更新（下载 + 校验 + 安装）
     */
    public void startUpdate(IUpdateCallback callback) {
        if (currentState != UpdateState.DOWNLOADING) {
            callback.onError(ErrorCode.STATE_ILLEGAL, "Cannot start update in state: " + currentState);
            return;
        }
        addCallback(callback);
        startDownload();
    }

    private void startDownload() {
        downloadManager.download(currentUpdateInfo.downloadUrl, new DownloadManager.Callback() {
            @Override
            public void onProgress(long current, long total) {
                notifyCallbacks(c -> c.onProgress(current, total));
            }

            @Override
            public void onComplete(String filePath) {
                transitionTo(UpdateState.VERIFYING);
                notifyCallbacks(c -> c.onStateChanged(UpdateState.VERIFYING, null));
                startVerify(filePath);
            }

            @Override
            public void onError(int code, String msg) {
                transitionTo(UpdateState.FAILED);
                notifyCallbacks(c -> c.onError(code, msg));
                watchDog.onFailed(UpdateState.DOWNLOADING, code, msg);
            }
        });
    }

    private void startVerify(String filePath) {
        boolean pass = verifyManager.verify(filePath, currentUpdateInfo.md5);
        if (pass) {
            transitionTo(UpdateState.INSTALLING);
            notifyCallbacks(c -> c.onStateChanged(UpdateState.INSTALLING, null));
            startInstall(filePath);
        } else {
            transitionTo(UpdateState.FAILED);
            notifyCallbacks(c -> c.onError(ErrorCode.VERIFY_FAILED, "APK verify failed"));
            // 删除损坏文件，触发重试（会重新下载）
            verifyManager.deleteInvalidFile(filePath);
            watchDog.onFailed(UpdateState.VERIFYING, ErrorCode.VERIFY_FAILED, "APK verify failed");
        }
    }

    private void startInstall(String filePath) {
        installer.install(filePath, new Installer.Callback() {
            @Override
            public void onSuccess() {
                transitionTo(UpdateState.SUCCESS);
                notifyCallbacks(c -> c.onStateChanged(UpdateState.SUCCESS, null));
                notifyCallbacks(c -> c.onComplete(true));
                // 上报成功
                reportUpdateResult(currentUpdateInfo.versionCode, "SUCCESS", "");
            }

            @Override
            public void onFailure(int code, String msg) {
                transitionTo(UpdateState.FAILED);
                notifyCallbacks(c -> c.onError(code, msg));
                watchDog.onFailed(UpdateState.INSTALLING, code, msg);
                // 上报失败
                reportUpdateResult(currentUpdateInfo.versionCode, "INSTALL_FAILED", msg);
            }
        });
    }

    private void reportUpdateResult(int versionCode, String status, String errorMsg) {
        // 通过 VersionChecker 上报到服务端
        // 异步操作，不影响主流程
    }

    private void transitionTo(@State int newState) {
        int oldState = currentState;
        currentState = newState;
        Log.d(TAG, "State: " + oldState + " -> " + newState);
    }

    void addCallback(IUpdateCallback callback) {
        callbacks.add(callback);
    }

    void removeCallback(IUpdateCallback callback) {
        callbacks.remove(callback);
    }

    @State
    int getState() {
        return currentState;
    }

    private void notifyCallbacks(CallbackAction action) {
        for (IUpdateCallback cb : callbacks) {
            try {
                action.invoke(cb);
            } catch (RemoteException e) {
                Log.e(TAG, "notify callback failed", e);
            }
        }
    }

    interface CallbackAction {
        void invoke(IUpdateCallback callback) throws RemoteException;
    }
}
```

### 5.4 VersionChecker

```java
public class VersionChecker {

    private static final String BASE_URL = "https://your-ota-server.com";
    private static final String CHECK_URL = BASE_URL + "/api/v1/update/check";
    private static final String REPORT_URL = BASE_URL + "/api/v1/update/report";
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final Context context;
    private final OkHttpClient httpClient;

    public interface Callback {
        void onUpdateFound(UpdateInfo info);
        void onNoUpdate();
        void onError(int code, String msg);
    }

    public void check(Callback callback) {
        int currentVersionCode = getAppVersionCode();
        String deviceId = getDeviceId();

        JSONObject body = new JSONObject();
        body.put("deviceId", deviceId);
        body.put("versionCode", currentVersionCode);

        httpRequestWithRetry(CHECK_URL, body, MAX_RETRY, RETRY_DELAY_MS, (success, response, error) -> {
            if (!success) {
                callback.onError(ErrorCode.NETWORK_ERROR, error);
                return;
            }

            JSONObject data = response.getJSONObject("data");
            boolean hasUpdate = data.getBoolean("hasUpdate");

            if (!hasUpdate) {
                callback.onNoUpdate();
                return;
            }

            UpdateInfo info = new UpdateInfo();
            info.versionCode = data.getInt("versionCode");
            info.versionName = data.getString("versionName");
            info.downloadUrl = data.getString("downloadUrl");
            info.md5 = data.getString("md5");
            info.fileSize = data.getLong("fileSize");
            info.minSupportVersion = data.getInt("minSupportVersion");
            info.updateLog = data.getString("updateLog");
            info.force = data.getBoolean("force");

            callback.onUpdateFound(info);
        });
    }

    public void report(int versionCode, String status, String errorMsg) {
        JSONObject body = new JSONObject();
        body.put("deviceId", getDeviceId());
        body.put("versionCode", versionCode);
        body.put("status", status);
        body.put("errorMsg", errorMsg);

        // Fire-and-forget，不影响主流程
        httpRequestWithRetry(REPORT_URL, body, 1, 0, (success, response, error) -> {
            if (!success) {
                Log.e(TAG, "report update result failed: " + error);
            }
        });
    }

    private int getAppVersionCode() {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return (int) info.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    private String getDeviceId() {
        // 使用 ANDROID_ID 或 IMEI 作为设备标识
        return Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
}
```

### 5.5 DownloadManager

```java
public class DownloadManager {

    private static final int BUFFER_SIZE = 8 * 1024;
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 3000;

    private final Context context;
    private OkHttpClient httpClient;
    private volatile boolean cancelled = false;

    public interface Callback {
        void onProgress(long current, long total);
        void onComplete(String filePath);
        void onError(int code, String msg);
    }

    public void download(String url, Callback callback) {
        String filePath = getDownloadPath();
        downloadWithResume(url, filePath, callback, 0);
    }

    private void downloadWithResume(String url, String filePath, Callback callback, long resumeOffset) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (resumeOffset > 0) {
            requestBuilder.header("Range", "bytes=" + resumeOffset + "-");
        }

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                callback.onError(ErrorCode.DOWNLOAD_FAILED,
                        "HTTP " + response.code());
                return;
            }

            long totalBytes;
            if (resumeOffset > 0) {
                // Range 响应: Content-Range 头包含完整大小
                totalBytes = parseContentRange(response.header("Content-Range"));
            } else {
                totalBytes = response.body().contentLength();
            }

            long downloaded = resumeOffset;

            try (InputStream is = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(filePath, resumeOffset > 0)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    if (cancelled) {
                        callback.onError(ErrorCode.DOWNLOAD_CANCELLED, "Download cancelled");
                        return;
                    }
                    fos.write(buffer, 0, read);
                    downloaded += read;
                    callback.onProgress(downloaded, totalBytes);
                }
            }

            callback.onComplete(filePath);

        } catch (IOException e) {
            // 如果已下载部分文件，下次可断点续传
            callback.onError(ErrorCode.DOWNLOAD_FAILED, e.getMessage());
        }
    }

    private String getDownloadPath() {
        File dir = context.getCacheDir();
        return new File(dir, "ota_update.apk").getAbsolutePath();
    }

    public void cancel() {
        cancelled = true;
    }
}
```

### 5.6 VerifyManager

```java
public class VerifyManager {

    private static final String ALGORITHM = "MD5";

    /**
     * 校验 APK 文件完整性
     * @param filePath APK 文件路径
     * @param expectedMd5 期望的 MD5 值（来自服务端）
     * @return true = 校验通过
     */
    public boolean verify(String filePath, String expectedMd5) {
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "APK file not found: " + filePath);
            return false;
        }

        String actualMd5 = calculateMd5(file);
        return actualMd5.equalsIgnoreCase(expectedMd5);
    }

    private String calculateMd5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "calculate md5 failed", e);
            return "";
        }
    }

    public void deleteInvalidFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "delete invalid APK: " + filePath + ", deleted=" + deleted);
        }
    }
}
```

### 5.7 Installer（静默安装）

```java
public class Installer {

    private final Context context;

    public interface Callback {
        void onSuccess();
        void onFailure(int code, String msg);
    }

    /**
     * 使用 PackageInstaller API 执行静默安装
     */
    public void install(String apkPath, Callback callback) {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        int sessionId;
        try {
            sessionId = packageInstaller.createSession(params);
        } catch (IOException e) {
            callback.onFailure(ErrorCode.INSTALL_FAILED, "create session failed: " + e.getMessage());
            return;
        }

        PackageInstaller.Session session = null;
        try {
            session = packageInstaller.openSession(sessionId);

            // 写入 APK
            try (InputStream in = new FileInputStream(apkPath);
                 OutputStream out = session.openWrite("ota_install", 0, -1)) {
                byte[] buffer = new byte[64 * 1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                session.fsync(out);
            }

            // 提交安装（使用 PendingIntent 接收结果）
            Intent intent = new Intent(context, InstallResultReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent, PendingIntent.FLAG_MUTABLE);

            session.commit(pendingIntent.getIntentSender());
            // 安装结果由 InstallResultReceiver 回调

        } catch (IOException e) {
            if (session != null) {
                try { session.abort(); } catch (IOException ignored) {}
            }
            callback.onFailure(ErrorCode.INSTALL_FAILED, e.getMessage());
        }
    }
}
```

#### InstallResultReceiver（安装结果广播接收）

```java
public class InstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_SUCCESS) {
            // 通知 UpdateController 安装成功
            // 通过本地广播或 EventBus 传递
        } else {
            // 通知 UpdateController 安装失败
        }
    }
}
```

### 5.8 WatchDog

```java
public class WatchDog {

    private static final int MAX_RETRY = 3;
    private static final long BASE_DELAY_MS = 3000; // 3 秒

    private int retryCount = 0;
    private final UpdateController controller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable retryRunnable;

    public WatchDog(UpdateController controller) {
        this.controller = controller;
    }

    /**
     * 收到失败事件，根据重试策略决定是否重试
     */
    public void onFailed(int failedState, int errorCode, String errorMsg) {
        if (retryCount >= MAX_RETRY) {
            Log.w(TAG, "max retry reached (" + MAX_RETRY + "), giving up");
            retryCount = 0;
            // 状态保持在 FAILED，通知客户端
            return;
        }

        retryCount++;
        long delayMs = BASE_DELAY_MS * (long) Math.pow(2, retryCount - 1); // 指数退避

        Log.d(TAG, "retry #" + retryCount + " after " + delayMs + "ms");

        retryRunnable = () -> {
            controller.transitionTo(UpdateState.IDLE);
            controller.checkUpdate(null); // 重新开始检查
        };

        handler.postDelayed(retryRunnable, delayMs);
    }

    /**
     * 重置重试计数器（成功时调用）
     */
    public void reset() {
        retryCount = 0;
        if (retryRunnable != null) {
            handler.removeCallbacks(retryRunnable);
        }
    }
}
```

---

## 六、错误码定义

```java
public final class ErrorCode {
    // 通用
    public static final int SUCCESS                  = 0;
    public static final int UNKNOWN_ERROR            = -1;

    // 服务相关
    public static final int SERVICE_NOT_CONNECTED    = 1001;
    public static final int SERVICE_DISCONNECTED     = 1002;
    public static final int STATE_ILLEGAL            = 1003;

    // 网络相关
    public static final int NETWORK_ERROR            = 2001;
    public static final int TIMEOUT_ERROR            = 2002;

    // 下载相关
    public static final int DOWNLOAD_FAILED          = 3001;
    public static final int DOWNLOAD_CANCELLED       = 3002;

    // 校验相关
    public static final int VERIFY_FAILED            = 4001;

    // 安装相关
    public static final int INSTALL_FAILED           = 5001;
    public static final int INSTALL_PERMISSION_DENIED  = 5002;

    // 服务端错误码映射（来自 API 响应）
    public static final int SERVER_INVALID_PARAM     = 6001;  // INVALID_PARAM
    public static final int SERVER_DEVICE_NOT_FOUND  = 6002;  // DEVICE_NOT_FOUND
    public static final int SERVER_VERSION_NOT_FOUND = 6003;  // VERSION_NOT_FOUND
    public static final int SERVER_RATE_LIMITED      = 6004;  // RATE_LIMITED
    public static final int SERVER_INTERNAL_ERROR    = 6005;  // INTERNAL_ERROR
}
```

---

## 七、核心流程时序图

### 7.1 完整 OTA 流程

```
用户/UI          UpdateClient         IUpdateService        UpdateController     VersionChecker    DownloadManager   VerifyManager    Installer      HTTP Server
  │                 │                      │                      │                    │                   │                │               │              │
  │ 点击更新        │                      │                      │                    │                   │                │               │              │
  │────────────────>│                      │                      │                    │                   │                │               │              │
  │                 │  startUpdate(cb)     │                      │                    │                   │                │               │              │
  │                 │─────────────────────>│                      │                    │                   │                │               │              │
  │                 │                      │ checkUpdate(cb)      │                    │                   │                │               │              │
  │                 │                      │─────────────────────>│                    │                   │                │               │              │
  │                 │                      │                      │                    │                   │                │               │              │
  │                 │                      │                      │  check()           │                   │                │               │              │
  │                 │                      │                      │───────────────────>│                   │                │               │              │
  │                 │                      │                      │                    │ POST /check      │                │               │              │
  │                 │                      │                      │                    │────────────────────────────────────────────────────────────────────>│
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │                    │ <── JSON ─────── │                │               │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ <── onUpdateFound ─│                  │                │               │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ state: DOWNLOADING │                  │                │               │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ download(url)      │                  │                │               │              │
  │                 │                      │                      │─────────────────────────────────────────>│                │               │              │
  │                 │                      │                      │                    │                  │  GET APK       │               │              │
  │                 │                      │                      │                    │                  │──────────────────────────────────────────────>│
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ <── onProgress ─── │ <── progress ───│                │               │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ <── onComplete ─── │ <── done ───────│                │               │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ state: VERIFYING   │                  │                │               │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ verify(filePath)   │                  │                │               │              │
  │                 │                      │                      │──────────────────────────────────────────────────────────>│               │              │
  │                 │                      │                      │                    │                  │                │ <── boolean ─ │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ <── true ───────── │                  │                │               │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ state: INSTALLING  │                  │                │               │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ install(path)      │                  │                │               │              │
  │                 │                      │                      │────────────────────────────────────────────────────────────────────────>│              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ <── onSuccess ──── │                  │                │               │              │
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │                      │ state: SUCCESS     │                  │                │               │              │
  │                 │                      │                      │                    │                   │                │               │              │
  │                 │                      │                      │ report(SUCCESS)    │                   │                │               │              │
  │                 │                      │                      │───────────────────>│                   │                │               │              │
  │                 │                      │                      │                    │ POST /report     │                │               │              │
  │                 │                      │                      │                    │────────────────────────────────────────────────────────────────────>│
  │                 │                      │                      │                    │                  │                │               │              │
  │                 │                      │ <── onComplete(true) │                    │                  │                │               │              │
  │                 │ <── onComplete(true) │                      │                    │                  │                │               │              │
  │ <── 更新成功 ───│                      │                      │                    │                  │                │               │              │
```

### 7.2 WatchDog 重试流程

```
UpdateController     WatchDog         VersionChecker    HTTP Server
       │                │                   │              │
       │ onFailed()     │                   │              │
       │───────────────>│                   │              │
       │                │                   │              │
       │                │ retryCount < 3 ?  │              │
       │                │ delay = 3s * 2^n  │              │
       │                │                   │              │
       │                │ ... wait ...      │              │
       │                │                   │              │
       │  transitionTo  │                   │              │
       │  (IDLE)        │                   │              │
       │<────────────── │                   │              │
       │                │                   │              │
       │  checkUpdate() │                   │              │
       │───────────────>│                   │              │
       │                │ check()           │              │
       │                │──────────────────>│              │
       │                │                   │ POST /check  │
       │                │                   │─────────────>│
       │                │                   │              │
```

---

## 八、安全设计

### 8.1 传输安全

| 项 | 说明 |
|----|------|
| HTTPS | 所有 HTTP 请求使用 HTTPS，防止中间人攻击 |
| 证书固定（可选） | 对高安全场景，可启用 Certificate Pinning |

### 8.2 文件安全

| 项 | 说明 |
|----|------|
| MD5 校验 | 下载完成后校验 APK 文件完整性 |
| 文件存储 | APK 存储在应用私有目录，其他应用无法访问 |
| 防降级 | versionCode 必须递增，防止回退到低版本 |

### 8.3 权限要求

```xml
<!-- 系统权限（需 system app） -->
<uses-permission android:name="android.permission.INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.DELETE_PACKAGES" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

---

## 九、项目结构

```
app/
├── src/main/
│   ├── java/com/example/ota/
│   │   ├── OtaApplication.java
│   │   ├── client/
│   │   │   └── UpdateClient.java
│   │   ├── service/
│   │   │   └── UpdateService.java
│   │   ├── controller/
│   │   │   └── UpdateController.java
│   │   ├── checker/
│   │   │   └── VersionChecker.java
│   │   ├── download/
│   │   │   └── DownloadManager.java
│   │   ├── verify/
│   │   │   └── VerifyManager.java
│   │   ├── install/
│   │   │   ├── Installer.java
│   │   │   └── InstallResultReceiver.java
│   │   ├── watchdog/
│   │   │   └── WatchDog.java
│   │   ├── aidl/
│   │   │   ├── IUpdateService.aidl
│   │   │   ├── IUpdateCallback.aidl
│   │   │   └── UpdateInfo.java
│   │   ├── model/
│   │   │   ├── UpdateState.java
│   │   │   └── ErrorCode.java
│   │   ├── receiver/
│   │   │   └── BootReceiver.java
│   │   └── ui/
│   │       └── UpdateActivity.java
│   ├── AndroidManifest.xml
│   └── res/
│       └── ...
├── build.gradle
└── proguard-rules.pro
```

---

## 十、测试策略

### 10.1 单元测试

| 模块 | 测试重点 |
|------|----------|
| VerifyManager | MD5 计算正确性、文件不存在处理、大小写不敏感比较 |
| WatchDog | 重试次数限制、指数退避时间计算、reset 行为 |
| UpdateController | 状态转移合法性、非法状态拒绝 |
| VersionChecker | JSON 解析、错误码映射 |

### 10.2 集成测试

| 场景 | 说明 |
|------|------|
| 完整 OTA 流程 | 从检查到安装成功，验证状态转移正确 |
| 断网场景 | 网络断开时进入 FAILED 状态，WatchDog 重试 |
| 断点续传 | 下载中断后恢复，验证 Range 头与文件追加 |
| MD5 校验失败 | 篡改文件后校验应失败，触发重新下载 |
| 安装权限不足 | 非 system app 应返回 INSTALL_PERMISSION_DENIED |
| Binder 断开重连 | 模拟守护进程崩溃后主进程自动重连 |

### 10.3 稳定性测试

| 场景 | 说明 |
|------|------|
| 进程被杀恢复 | UpdateService 被杀后 BootReceiver 或 WatchDog 恢复 |
| 磁盘空间不足 | 下载前检查可用空间，不足时提前报错 |
| 并发请求 | 同时触发多次更新，确保串行执行 |

---

## 十一、客户端与服务端 API 对接说明

### 11.1 请求与响应映射

| 客户端发送 | 服务端接口 | 关键字段 |
|------------|-----------|----------|
| `VersionChecker.check()` | `POST /api/v1/update/check` | deviceId (ANDROID_ID), versionCode (PackageInfo) |
| `VersionChecker.report()` | `POST /api/v1/update/report` | deviceId, versionCode, status, errorMsg |

### 11.2 状态映射

| 客户端状态 | 服务端 status 值 |
|------------|-----------------|
| 安装成功 | SUCCESS |
| 下载失败 | DOWNLOAD_FAILED |
| 校验失败 | VERIFY_FAILED |
| 安装失败 | INSTALL_FAILED |
| 检查失败 | FAILED |

### 11.3 错误码映射

| 客户端 ErrorCode | 服务端 errorCode | 说明 |
|-----------------|------------------|------|
| SERVER_INVALID_PARAM | INVALID_PARAM | 参数校验失败 |
| SERVER_DEVICE_NOT_FOUND | DEVICE_NOT_FOUND | 设备不存在 |
| SERVER_VERSION_NOT_FOUND | VERSION_NOT_FOUND | 版本信息不存在 |
| SERVER_RATE_LIMITED | RATE_LIMITED | 请求频率超限 |
| SERVER_INTERNAL_ERROR | INTERNAL_ERROR | 服务端内部错误 |

---

## 十二、异常与边界情况处理

### 12.1 异常场景清单

| 场景 | 处理方式 |
|------|----------|
| 服务端无响应 | 重试 3 次，失败后进入 FAILED，WatchDog 处理 |
| 下载中途断网 | 保留已下载文件，重试时 Range 续传 |
| APK 文件损坏 | VerifyManager 校验失败 → 删除文件 → 触发重试（重新下载） |
| 磁盘空间不足 | 下载前检查 `StatFs.getAvailableBytes()`，不足则报错 |
| 安装被用户取消 | PackageInstaller 返回失败，进入 FAILED 状态 |
| 设备重启 | BootReceiver 收到 BOOT_COMPLETED，自动检查更新 |
| 服务端版本回退 | 客户端 versionCode 不会降级，忽略回退版本 |
| 多个更新请求 | UpdateController 状态机保证串行，非 IDLE 状态拒绝新请求 |
