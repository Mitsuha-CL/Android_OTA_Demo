# Android OTA 服务端详细设计文档

## 一、项目概述

### 1.1 背景

在定制 Android 设备场景下，需要实现应用的远程 OTA（Over-The-Air）更新能力。服务端作为 OTA 系统的控制中心，负责版本管理、更新策略控制、APK 下载地址提供与更新结果接收。

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| 核心目标 | 支持客户端检测更新、强制更新策略、稳定 APK 下载、更新结果上报 |
| 非核心目标 | 灰度发布、设备管理系统、日志监控（后续扩展） |
| 设计原则 | 简单优先、高可靠、易扩展 |

### 1.3 技术栈

| 组件 | 技术选型 | 说明 |
|------|----------|------|
| 框架 | Spring Boot 3.x | REST API 服务 |
| 数据库 | MySQL 8.x | 版本/设备/日志存储 |
| 文件存储 | 本地磁盘 | APK 文件存储在服务器本地目录（`./data/apk/`） |
| 反向代理 | Nginx | 仅做反向代理，SSL 终止（后续可扩展限流等功能） |
| CDN/OSS | —（预留接口） | Demo 阶段不使用，后续通过 `StorageProvider` 接口平滑切换 |

---

## 二、系统架构

### 2.1 总体架构图

```
┌──────────────┐
│  Android 客户端 │
└──────┬───────┘
       │ HTTPS
       ▼
┌──────────────────┐
│   Nginx          │  ← 反向代理、SSL 终止
└──────┬───────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│          API Server (Spring Boot)         │
│  ┌──────────────┐  ┌──────────────────┐  │
│  │ UpdateCtrl   │  │ ReportController │  │
│  │ (接口层)      │  │   (上报接口)      │  │
│  └──────┬───────┘  └────────┬─────────┘  │
│         │                   │             │
│  ┌──────▼───────────────────▼─────────┐  │
│  │        VersionService              │  │
│  │  (版本管理 / 更新判断 / 策略生成)    │  │
│  └──────────────┬─────────────────────┘  │
│                 │                         │
│  ┌──────────────▼─────────────────────┐  │
│  │        DeviceService               │  │
│  │  (设备信息记录 / 版本更新)          │  │
│  └────────────────────────────────────┘  │
└─────────────────┬────────────────────────┘
                  │
     ┌────────────┼────────────┐
     ▼            ▼            ▼
┌────────┐  ┌────────────┐  ┌──────────┐
│ MySQL  │  │StorageService│  │ 本地磁盘  │
│(数据层) │  │(文件存储抽象) │  │(APK文件) │
└────────┘  └────────────┘  └──────────┘
```

### 2.2 模块职责

| 模块 | 职责 |
|------|------|
| UpdateController | 提供 REST API，接收客户端请求，委托 Service 处理 |
| VersionService | 管理版本信息，判断是否需要更新，生成更新策略与响应体 |
| ReportService | 接收客户端更新结果，记录更新日志 |
| DeviceService | 记录设备信息，更新设备当前版本 |
| StorageService | APK 文件存储抽象（当前：本地磁盘；后续可扩展 OSS/CDN） |
| Nginx | 反向代理、SSL 终止 |

---

## 三、API 设计

### 3.1 全局约定

| 项 | 说明 |
|----|------|
| 协议 | HTTPS |
| 数据格式 | JSON (application/json) |
| 字符编码 | UTF-8 |
| 时间格式 | ISO 8601 (yyyy-MM-dd'T'HH:mm:ssZ) |
| 版本号 | versionCode（整型，递增），versionName（字符串，展示用） |
| 统一响应体 | `{ "success": true/false, "data": {...}, "errorCode": "", "errorMsg": "" }` |

### 3.2 统一响应结构

```json
{
  "success": true,
  "data": { ... },
  "errorCode": "",
  "errorMsg": ""
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 请求是否成功 |
| data | object | 业务数据 |
| errorCode | string | 错误码，成功时为空 |
| errorMsg | string | 错误描述，成功时为空 |

### 3.3 全局错误码

| errorCode | 含义 | HTTP 状态码 |
|-----------|------|-------------|
| "" | 成功 | 200 |
| INVALID_PARAM | 参数校验失败 | 400 |
| DEVICE_NOT_FOUND | 设备不存在 | 404 |
| VERSION_NOT_FOUND | 版本信息不存在 | 404 |
| RATE_LIMITED | 请求频率超限 | 429 |
| INTERNAL_ERROR | 服务端内部错误 | 500 |

---

### 3.4 POST /api/v1/update/check —— 检查更新

#### 请求

```json
{
  "deviceId": "device-001",
  "versionCode": 5
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceId | string | 是 | 设备唯一标识，长度 1-128 |
| versionCode | int | 是 | 客户端当前版本号，必须 > 0 |

#### 响应（有更新）

```json
{
  "success": true,
  "data": {
    "hasUpdate": true,
    "versionCode": 10,
    "versionName": "1.0.10",
    "force": true,
    "downloadUrl": "https://your-server.com/api/v1/apk/download/10",
    "md5": "a1b2c3d4e5f6...",
    "fileSize": 12345678,
    "minSupportVersion": 8,
    "updateLog": "修复若干问题"
  },
  "errorCode": "",
  "errorMsg": ""
}
```

#### 响应（无更新）

```json
{
  "success": true,
  "data": {
    "hasUpdate": false,
    "versionCode": 5,
    "versionName": "1.0.5",
    "force": false,
    "downloadUrl": "",
    "md5": "",
    "fileSize": 0,
    "minSupportVersion": 5,
    "updateLog": ""
  },
  "errorCode": "",
  "errorMsg": ""
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| hasUpdate | boolean | 是否存在可用更新 |
| versionCode | int | 目标版本号 |
| versionName | string | 目标版本名称 |
| force | boolean | 是否强制更新（客户端不可跳过） |
| downloadUrl | string | APK 下载地址（hasUpdate=false 时为空） |
| md5 | string | APK 文件 MD5 校验值（hasUpdate=false 时为空） |
| fileSize | long | APK 文件大小（字节） |
| minSupportVersion | int | 最低支持版本，低于此值 force=true |
| updateLog | string | 更新日志说明 |

#### 业务逻辑

```
输入: deviceId, clientVersionCode

1. 校验参数合法性
   - deviceId 不能为空
   - versionCode > 0

2. 查询设备信息
   - 若设备不存在，自动注册（deviceId, currentVersion=clientVersionCode, lastSeenTime=now）
   - 若设备存在，更新 lastSeenTime

3. 查询最新版本记录
   - 取 version 表中状态为 active 的最新记录（按 versionCode 降序取第一条）

4. 判断更新策略
   latestVersion = 最新版本记录的 versionCode
   minSupportVersion = 最新版本记录的 min_support_version

   if clientVersionCode < minSupportVersion:
       force = true, hasUpdate = true
   elif clientVersionCode < latestVersion:
       force = false, hasUpdate = true
   else:
       hasUpdate = false

5. 构造响应返回
```

---

### 3.5 POST /api/v1/update/report —— 更新结果上报

#### 请求

```json
{
  "deviceId": "device-001",
  "versionCode": 10,
  "status": "SUCCESS",
  "errorMsg": ""
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceId | string | 是 | 设备唯一标识 |
| versionCode | int | 是 | 目标版本号 |
| status | string | 是 | 更新结果，枚举：SUCCESS / FAILED / DOWNLOAD_FAILED / VERIFY_FAILED / INSTALL_FAILED |
| errorMsg | string | 否 | 失败时的错误信息 |

#### 响应

```json
{
  "success": true,
  "data": {},
  "errorCode": "",
  "errorMsg": ""
}
```

#### 业务逻辑

```
输入: deviceId, versionCode, status, errorMsg

1. 校验参数

2. 写入 update_log 表
   - 记录 deviceId, versionCode, status, errorMsg, createTime

3. 若 status == SUCCESS
   - 更新 device 表的 currentVersion = versionCode

4. 返回成功
```

---

### 3.6 GET /api/v1/apk/download/{versionCode} —— APK 文件下载

#### 请求

```
GET /api/v1/apk/download/10
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| versionCode | path | 是 | 版本号 |

#### 响应

- 成功：`Content-Type: application/vnd.android.package-archive`，响应体为 APK 二进制流
- 失败：返回 JSON 错误响应

```json
{
  "success": false,
  "data": null,
  "errorCode": "VERSION_NOT_FOUND",
  "errorMsg": "version not found"
}
```

#### 响应头

```
Content-Type: application/vnd.android.package-archive
Content-Length: 12345678
Content-Disposition: attachment; filename="app_v10.apk"
Accept-Ranges: bytes          ← 支持断点续传
```

#### 业务逻辑

```
输入: versionCode

1. 查询 version 表获取版本记录
2. 若记录不存在，返回 404
3. 通过 StorageService 读取 APK 文件流
4. 设置响应头，输出文件流
5. 支持 HTTP Range 请求（断点续传）
```

---

## 四、数据库设计

### 4.1 version 表

存储应用版本信息与 APK 元数据。

```sql
CREATE TABLE `version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `version_code` INT UNSIGNED NOT NULL COMMENT '版本号（递增整型）',
  `version_name` VARCHAR(64) NOT NULL COMMENT '版本名称（展示用）',
  `download_url` VARCHAR(512) NOT NULL COMMENT 'APK 下载路径（当前为服务端 API 路径，后续可切换为 CDN URL）',
  `md5` VARCHAR(64) NOT NULL COMMENT 'APK 文件 MD5 值',
  `file_size` BIGINT UNSIGNED NOT NULL COMMENT 'APK 文件大小（字节）',
  `force_update` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否强制更新',
  `min_support_version` INT UNSIGNED NOT NULL COMMENT '最低支持版本',
  `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态：active / archived',
  `update_log` VARCHAR(1024) DEFAULT '' COMMENT '更新日志',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version_code` (`version_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本信息表';
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| version_code | INT | 版本号，唯一 |
| version_name | VARCHAR | 展示名称 |
| download_url | VARCHAR | APK 下载路径（当前为 `/api/v1/apk/download/{id}`，后续可切换为 CDN URL） |
| md5 | VARCHAR | 文件 MD5 |
| file_size | BIGINT | 文件大小 |
| force_update | TINYINT | 是否强制 |
| min_support_version | INT | 最低支持版本 |
| status | VARCHAR | active / archived |
| update_log | VARCHAR | 更新说明 |
| status | VARCHAR | active / archived |

### 4.2 device 表

记录设备基本信息。

```sql
CREATE TABLE `device` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
  `current_version` INT UNSIGNED NOT NULL COMMENT '当前应用版本',
  `last_seen_time` DATETIME NOT NULL COMMENT '最后活跃时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';
```

### 4.3 update_log 表

记录更新结果日志。

```sql
CREATE TABLE `update_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` VARCHAR(128) NOT NULL COMMENT '设备标识',
  `version_code` INT UNSIGNED NOT NULL COMMENT '目标版本号',
  `status` VARCHAR(32) NOT NULL COMMENT '更新状态',
  `error_msg` VARCHAR(512) DEFAULT '' COMMENT '错误信息',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_version_code` (`version_code`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='更新日志表';
```

### 4.4 索引策略

| 表 | 索引 | 用途 |
|----|------|------|
| version | uk_version_code | 唯一约束，快速查询指定版本 |
| version | idx_status | 过滤 active 版本 |
| device | uk_device_id | 唯一约束，快速查询设备 |
| update_log | idx_device_id | 查询某设备的更新历史 |
| update_log | idx_created_at | 按时间范围查询 |

---

## 五、服务层设计

### 5.1 UpdateController

```java
@RestController
@RequestMapping("/api/v1/update")
@RequiredArgsConstructor
public class UpdateController {

    private final VersionService versionService;
    private final ReportService reportService;

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<UpdateCheckResponse>> checkUpdate(
            @Valid @RequestBody UpdateCheckRequest request) {
        UpdateCheckResponse response = versionService.checkUpdate(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/report")
    public ResponseEntity<ApiResponse<Void>> reportUpdate(
            @Valid @RequestBody UpdateReportRequest request) {
        reportService.handleReport(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

/**
 * APK 文件下载控制器
 */
@RestController
@RequestMapping("/api/v1/apk")
@RequiredArgsConstructor
public class ApkDownloadController {

    private final VersionMapper versionMapper;
    private final StorageProvider storageProvider;

    @GetMapping("/download/{versionCode}")
    public ResponseEntity<InputStreamResource> downloadApk(
            @PathVariable int versionCode) throws IOException {

        Version version = versionMapper.selectByVersionCode(versionCode);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }

        InputStream inputStream = storageProvider.getInputStream(versionCode);
        InputStreamResource resource = new InputStreamResource(inputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .contentLength(version.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"app_v" + versionCode + ".apk\"")
                .body(resource);
    }
}
```

### 5.2 VersionService

```java
@Service
@RequiredArgsConstructor
public class VersionService {

    private final VersionMapper versionMapper;
    private final DeviceService deviceService;
    private final StorageProvider storageProvider;

    /**
     * 核心方法：判断是否需要更新并生成策略响应
     */
    public UpdateCheckResponse checkUpdate(UpdateCheckRequest request) {
        // 1. 注册或更新设备
        deviceService.registerOrUpdate(request.getDeviceId(), request.getVersionCode());

        // 2. 查询最新版本
        Version latestVersion = versionMapper.selectLatestActive();
        if (latestVersion == null) {
            return buildNoUpdateResponse(request.getVersionCode());
        }

        // 3. 判断更新策略
        boolean hasUpdate = request.getVersionCode() < latestVersion.getVersionCode();
        boolean force = request.getVersionCode() < latestVersion.getMinSupportVersion();

        if (!hasUpdate) {
            return buildNoUpdateResponse(request.getVersionCode());
        }

        return buildUpdateResponse(latestVersion, force);
    }

    private UpdateCheckResponse buildUpdateResponse(Version version, boolean force) {
        UpdateCheckResponse response = new UpdateCheckResponse();
        response.setHasUpdate(true);
        response.setVersionCode(version.getVersionCode());
        response.setVersionName(version.getVersionName());
        response.setForce(force || version.isForceUpdate());
        response.setDownloadUrl(storageProvider.getDownloadUrl(version.getVersionCode()));
        response.setMd5(version.getMd5());
        response.setFileSize(version.getFileSize());
        response.setMinSupportVersion(version.getMinSupportVersion());
        response.setUpdateLog(version.getUpdateLog());
        return response;
    }
}
```

### 5.3 ReportService

```java
@Service
@RequiredArgsConstructor
public class ReportService {

    private final UpdateLogMapper updateLogMapper;
    private final DeviceService deviceService;

    public void handleReport(UpdateReportRequest request) {
        // 1. 写入日志
        UpdateLog log = new UpdateLog();
        log.setDeviceId(request.getDeviceId());
        log.setVersionCode(request.getVersionCode());
        log.setStatus(request.getStatus());
        log.setErrorMsg(request.getErrorMsg());
        updateLogMapper.insert(log);

        // 2. 成功后更新设备版本
        if ("SUCCESS".equals(request.getStatus())) {
            deviceService.updateVersion(request.getDeviceId(), request.getVersionCode());
        }
    }
}
```

### 5.4 DeviceService

```java
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;

    /**
     * 设备不存在时自动注册，存在时更新 lastSeenTime
     */
    public void registerOrUpdate(String deviceId, int currentVersion) {
        Device device = deviceMapper.selectByDeviceId(deviceId);
        if (device == null) {
            deviceMapper.insert(new Device(deviceId, currentVersion, new Date()));
        } else {
            deviceMapper.updateLastSeenTime(deviceId, new Date());
        }
    }

    /**
     * 更新设备版本（更新成功后调用）
     */
    public void updateVersion(String deviceId, int newVersion) {
        deviceMapper.updateVersion(deviceId, newVersion, new Date());
    }
}
```

### 5.5 StorageService（APK 文件存储抽象）

#### 设计目的

当前 Demo 阶段 APK 存储在本地磁盘，但需要预留接口以便未来平滑切换到 OSS/CDN，避免大规模重构。

#### 接口定义

```java
/**
 * APK 文件存储提供者接口
 * 当前实现：LocalStorageProvider（本地磁盘）
 * 未来扩展：OssStorageProvider（阿里云 OSS）、MinIO 等
 */
public interface StorageProvider {

    /**
     * 存储 APK 文件
     * @param versionCode 版本号
     * @param inputStream 文件输入流
     * @param fileSize 文件大小
     * @return 存储路径/URL（本地存储时为相对路径，OSS 时为完整 URL）
     */
    String store(int versionCode, InputStream inputStream, long fileSize) throws IOException;

    /**
     * 获取 APK 文件输入流（用于下载）
     */
    InputStream getInputStream(int versionCode) throws IOException;

    /**
     * 获取 APK 文件大小
     */
    long getFileSize(int versionCode) throws IOException;

    /**
     * 删除 APK 文件
     */
    void delete(int versionCode) throws IOException;

    /**
     * 获取对外访问的下载 URL
     * 当前：/api/v1/apk/download/{versionCode}
     * OSS/CDN：https://cdn.example.com/ota/app_v10.apk
     */
    String getDownloadUrl(int versionCode);
}
```

#### 当前实现：LocalStorageProvider

```java
@Service
public class LocalStorageProvider implements StorageProvider {

    // APK 存储根目录，可通过 application.yml 配置
    @Value("${ota.storage.local-path:./data/apk}")
    private String basePath;

    @Override
    public String store(int versionCode, InputStream inputStream, long fileSize) throws IOException {
        File dir = new File(basePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, "app_v" + versionCode + ".apk");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            inputStream.transferTo(fos);
        }
        return file.getAbsolutePath();
    }

    @Override
    public InputStream getInputStream(int versionCode) throws IOException {
        File file = getFile(versionCode);
        return new FileInputStream(file);
    }

    @Override
    public long getFileSize(int versionCode) throws IOException {
        return getFile(versionCode).length();
    }

    @Override
    public void delete(int versionCode) throws IOException {
        getFile(versionCode).delete();
    }

    @Override
    public String getDownloadUrl(int versionCode) {
        // Demo 阶段：通过 Spring Boot API 下载
        return "/api/v1/apk/download/" + versionCode;
    }

    private File getFile(int versionCode) throws IOException {
        File file = new File(basePath, "app_v" + versionCode + ".apk");
        if (!file.exists()) {
            throw new FileNotFoundException("APK not found: " + file.getAbsolutePath());
        }
        return file;
    }
}
```

#### 本地文件存储路径

```
./data/apk/
    app_v1.apk
    app_v2.apk
    app_v10.apk
```

#### 未来扩展：OssStorageProvider（示意）

```java
// 未来切换到 OSS 时，仅需新增此实现类，无需修改任何业务代码
@Service
@ConditionalOnProperty(name = "ota.storage.type", havingValue = "oss")
public class OssStorageProvider implements StorageProvider {

    private final OSS ossClient;

    @Override
    public String store(int versionCode, InputStream inputStream, long fileSize) {
        String key = "ota/app_v" + versionCode + ".apk";
        ossClient.putObject(bucketName, key, inputStream);
        return "https://" + bucketName + ".oss-cn-hangzhou.aliyuncs.com/" + key;
    }

    @Override
    public String getDownloadUrl(int versionCode) {
        // 返回 CDN/OSS 公开访问 URL
        return cdnDomain + "/ota/app_v" + versionCode + ".apk";
    }

    // ... 其他方法
}
```

#### 切换方式

通过 `application.yml` 配置切换实现：

```yaml
# Demo 阶段：本地存储
ota:
  storage:
    type: local
    local-path: ./data/apk

# 未来切换为 OSS：
# ota:
#   storage:
#     type: oss
#     oss:
#       endpoint: oss-cn-hangzhou.aliyuncs.com
#       bucket: my-ota-bucket
#       cdn-domain: https://cdn.example.com
```

通过 Spring 的 `@ConditionalOnProperty` 自动选择实现类，业务层代码无需任何改动。

---

## 六、核心流程时序图

### 6.1 检查更新流程

```
客户端                 Nginx              UpdateController      VersionService        DeviceService       MySQL
  │                     │                      │                      │                    │                 │
  │ POST /check         │                      │                      │                    │                 │
  │────────────────────>│                      │                      │                    │                 │
  │                     │ POST /check          │                      │                    │                 │
  │                     │─────────────────────>│                      │                    │                 │
  │                     │                      │ checkUpdate(req)     │                    │                 │
  │                     │                      │─────────────────────>│                    │                 │
  │                     │                      │                      │ registerOrUpdate() │                 │
  │                     │                      │                      │───────────────────>│ SELECT device   │
  │                     │                      │                      │                    │────────────────>│
  │                     │                      │                      │                    │                 │
  │                     │                      │                      │ selectLatestActive()│                │
  │                     │                      │                      │────────────────────────────────────>│
  │                     │                      │                      │                    │                 │
  │                     │                      │                      │ <── 版本记录 ──────│                 │
  │                     │                      │                      │                    │                 │
  │                     │                      │                      │ 判断策略            │                 │
  │                     │                      │                      │                    │                 │
  │                     │                      │ <── UpdateCheckRes ──│                    │                 │
  │                     │                      │                      │                    │                 │
  │                     │ <── JSON Response ── │                      │                    │                 │
  │ <── JSON Response ─ │                      │                      │                    │                 │
```

### 6.2 更新结果上报流程

```
客户端                 Nginx              UpdateController      ReportService         DeviceService       MySQL
  │                     │                      │                      │                    │                 │
  │ POST /report        │                      │                      │                    │                 │
  │────────────────────>│                      │                      │                    │                 │
  │                     │ POST /report         │                      │                    │                 │
  │                     │─────────────────────>│                      │                    │                 │
  │                     │                      │ handleReport(req)    │                    │                 │
  │                     │                      │─────────────────────>│                    │                 │
  │                     │                      │                      │ INSERT update_log  │                 │
  │                     │                      │                      │───────────────────────────────────>│
  │                     │                      │                      │                    │                 │
  │                     │                      │                      │ (if SUCCESS)       │                 │
  │                     │                      │                      │ updateVersion()    │                 │
  │                     │                      │                      │───────────────────>│ UPDATE device   │
  │                     │                      │                      │                    │────────────────>│
  │                     │                      │                      │                    │                 │
  │                     │                      │ <── success ─────────│                    │                 │
  │                     │ <── JSON Response ── │                      │                    │                 │
  │ <── JSON Response ─ │                      │                      │                    │                 │
```

### 6.3 APK 文件下载流程

```
客户端                 Nginx              UpdateController      StorageService       本地磁盘
  │                     │                      │                      │                  │
  │ GET /apk/download/10│                      │                      │                  │
  │────────────────────>│                      │                      │                  │
  │                     │ GET /apk/download/10 │                      │                  │
  │                     │─────────────────────>│                      │                  │
  │                     │                      │ getInputStream(10)   │                  │
  │                     │                      │─────────────────────>│                  │
  │                     │                      │                      │ read APK file    │
  │                     │                      │                      │─────────────────>│
  │                     │                      │                      │ <── InputStream ─│
  │                     │                      │                      │                  │
  │                     │                      │ <── InputStream ──── │                  │
  │                     │ <── APK 文件流 ──────│                      │                  │
  │ <── APK 文件流 ──── │                      │                      │                  │
```

---

## 七、安全设计

### 7.1 HTTPS

- 所有接口必须通过 HTTPS 访问
- Nginx 层配置 SSL 证书，强制 HTTPS 重定向

### 7.2 参数校验

| 参数 | 校验规则 |
|------|----------|
| deviceId | 非空，长度 1-128，仅允许字母/数字/横线 |
| versionCode | 必填，整数，> 0 |
| status | 必填，枚举值限制 |

使用 Jakarta Validation (`@Valid`) 实现。

### 7.3 防刷 / 限流

> Demo 阶段暂不启用，仅预留接口。后续可通过以下层级实现：

| 层级 | 策略 | 说明 |
|------|------|------|
| Nginx | 按 IP 限流 | 通过 `limit_req_zone` 配置，当前未启用 |
| 应用层 | 按 deviceId 限流 | 通过 Spring 拦截器 + 内存 Map 实现，后续可接 Redis |

### 7.4 Nginx 配置（当前仅反向代理）

```nginx
server {
    listen 443 ssl;
    server_name your-ota-server.com;

    ssl_certificate     /etc/nginx/ssl/server.crt;
    ssl_certificate_key /etc/nginx/ssl/server.key;

    # 反向代理到 Spring Boot
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # APK 下载需要调整超时和缓冲区
        proxy_read_timeout 300s;
        proxy_buffering off;
    }

    # 预留：后续可在此层添加限流配置
    # limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    # limit_req zone=api burst=20;
}
```

### 7.5 接口幂等

- report 接口可能因网络重试被多次调用，需保证幂等
- 通过 `(deviceId, versionCode, status)` 组合键在 update_log 表去重

---

## 八、扩展设计（低优先级）

### 8.1 灰度发布

- version 表增加 `gray_ratio` 字段（0-100）
- DeviceService 根据 deviceId hash 值决定设备是否在灰度范围内
- checkUpdate 接口根据灰度策略返回不同的更新结果

### 8.2 设备管理

- 设备分组（按型号、地区、渠道等）
- 批量推送能力
- 版本分布统计

### 8.3 日志监控

- 接入 ELK 收集与分析 update_log
- Prometheus + Grafana 监控接口响应时间与错误率
- 告警规则：接口错误率 > 5% 告警

### 8.4 文件存储升级

- 当前使用 `LocalStorageProvider` 存储 APK 到本地磁盘
- 未来通过配置切换为 `OssStorageProvider`，无需修改业务代码
- 升级步骤：
  1. 实现 `OssStorageProvider` 类
  2. 将本地 APK 文件迁移到 OSS
  3. 修改 `application.yml` 中 `ota.storage.type` 为 `oss`
  4. 重启服务即可生效

---

## 九、项目结构

```
ota-server/
├── src/main/java/com/example/ota/
│   ├── OtaApplication.java
│   ├── config/
│   │   └── WebMvcConfig.java
│   ├── controller/
│   │   └── UpdateController.java
│   ├── service/
│   │   ├── VersionService.java
│   │   ├── ReportService.java
│   │   └── DeviceService.java
│   ├── storage/
│   │   ├── StorageProvider.java          ← 存储抽象接口
│   │   └── LocalStorageProvider.java     ← 当前实现（本地磁盘）
│   ├── mapper/
│   │   ├── VersionMapper.java
│   │   ├── DeviceMapper.java
│   │   └── UpdateLogMapper.java
│   ├── model/
│   │   ├── entity/
│   │   │   ├── Version.java
│   │   │   ├── Device.java
│   │   │   └── UpdateLog.java
│   │   ├── dto/
│   │   │   ├── UpdateCheckRequest.java
│   │   │   ├── UpdateCheckResponse.java
│   │   │   ├── UpdateReportRequest.java
│   │   │   └── ApiResponse.java
│   │   └── enums/
│   │       └── UpdateStatus.java
│   └── exception/
│       ├── GlobalExceptionHandler.java
│       └── OtaException.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       └── V1__init.sql
├── data/apk/                            ← APK 本地存储目录（Demo 阶段）
└── pom.xml
```

---

## 十、测试策略

### 10.1 单元测试

| 模块 | 测试重点 |
|------|----------|
| VersionService | 版本比较逻辑、强制更新判断、无版本时的降级 |
| ReportService | 日志写入、设备版本更新、幂等性 |
| DeviceService | 注册/更新逻辑、并发安全 |

### 10.2 集成测试

| 测试场景 | 说明 |
|----------|------|
| 检查更新 —— 有新版本 | 正常返回更新信息，downloadUrl 指向本地 API |
| 检查更新 —— 已是最新 | 返回 hasUpdate=false |
| 检查更新 —— 低于最低版本 | force=true |
| 检查更新 —— 设备首次请求 | 自动注册设备 |
| 上报结果 —— SUCCESS | 日志写入 + 设备版本更新 |
| 上报结果 —— FAILED | 仅日志写入 |
| APK 下载 —— 正常下载 | 返回 APK 文件流，Content-Type 正确 |
| APK 下载 —— 版本不存在 | 返回 404 |
| APK 下载 —— 断点续传 | 发送 Range 头，返回 206 + 部分内容 |

### 10.3 性能测试

> Demo 阶段无高并发要求，仅做基础验证：

- 目标：check 接口 P99 < 200ms
- 工具：curl / Postman
- 场景：连续请求 10 次 check 接口，观察响应时间

---

## 十一、部署架构

### 11.1 Demo 阶段部署架构

```
┌────────┐       ┌──────────┐       ┌──────────────────┐
│ 客户端 │──HTTPS─▶│  Nginx   │──HTTP─▶│   Spring Boot    │
└────────┘       │(反向代理) │       │   (单节点)       │
                 └──────────┘       └────────┬─────────┘
                                              │
                                    ┌─────────┴─────────┐
                                    ▼                   ▼
                              ┌──────────┐       ┌──────────┐
                              │  MySQL   │       │ 本地磁盘  │
                              │  (单节点) │       │ ./data/  │
                              └──────────┘       └──────────┘
```

- 单节点部署，Nginx + Spring Boot + MySQL 可运行在同一台机器
- APK 文件存储在 Spring Boot 进程可访问的本地目录

### 11.2 未来升级路径

当需要扩展到生产环境时，升级路径如下：

| 组件 | 当前 | 升级后 | 改动范围 |
|------|------|--------|----------|
| 文件存储 | 本地磁盘 | OSS / CDN | 仅需新增 `OssStorageProvider`，通过配置切换 |
| Nginx | 反向代理 | + 限流 + 负载均衡 | 仅需修改 Nginx 配置 |
| Spring Boot | 单节点 | 多节点 | 无状态设计，天然支持水平扩展 |
| MySQL | 单节点 | 主从读写分离 | 配置多数据源，业务代码改动小 |
| 限流 | 无 | 应用层拦截器 | 新增拦截器，不影响核心业务逻辑 |

### 11.3 application.yml 配置示例

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ota?useSSL=true&characterEncoding=utf8
    username: root
    password: your-password

# OTA 配置
ota:
  storage:
    type: local                    # 当前: local；未来可切换为 oss
    local-path: ./data/apk         # APK 本地存储目录
```
