📄 Android OTA 服务端完整设计文档（最终版）

# Android OTA 服务端完整设计文档

---

## 一、项目背景

在定制 Android 设备场景下，需要实现应用的远程 OTA（Over-The-Air）更新能力。

服务端作为 OTA 系统的控制中心，负责：

- 管理应用版本
- 控制更新策略
- 提供 APK 下载地址
- 接收客户端更新结果

---

## 二、设计目标

---

### 2.1 核心目标

- 支持客户端检测更新
- 支持强制更新策略
- 提供稳定的 APK 下载能力
- 支持更新结果上报

---

### 2.2 非核心目标（后续扩展）

- 灰度发布
- 设备管理系统
- 日志监控与分析

---

### 2.3 设计原则

- 简单优先（优先跑通主流程）
- 高可靠（接口稳定）
- 易扩展（支持后续功能扩展）

---

## 三、系统架构设计

---

### 3.1 总体架构

客户端设备
│
│ HTTPS
▼
Nginx（网关）
│
▼
API Server（Spring Boot）
│
├── UpdateController（接口层）
├── VersionService（版本控制核心）
├── ReportService（上报处理）
├── DeviceService（设备管理-简化）
│
▼
MySQL（数据库）
│
▼
OSS/CDN（APK文件存储与分发）

---

### 3.2 架构说明

- Nginx：负责请求转发、限流
- API Server：处理业务逻辑
- MySQL：存储版本与设备信息
- OSS/CDN：提供 APK 下载能力

---

## 四、核心流程设计

---

### 4.1 OTA 主流程

客户端启动
↓
调用 /api/v1/update/check
↓
服务端判断版本
↓
返回更新信息
↓
客户端下载 APK
↓
安装完成
↓
调用 /api/v1/update/report

---

## 五、模块设计

---

### 5.1 UpdateController（接口层）

#### 职责

- 提供 OTA API 接口
- 接收客户端请求
- 返回更新策略

---

#### 接口列表

- POST /api/v1/update/check
- POST /api/v1/update/report

---

#### 设计要求

- 无状态设计
- 支持高并发
- 响应时间 < 200ms

---

---

### 5.2 VersionService（核心模块）

---

#### 职责

- 管理版本信息
- 判断是否需要更新
- 生成更新策略

---

#### 输入

- deviceId
- versionCode

---

#### 输出

```json
{
  "hasUpdate": true,
  "versionCode": 10,
  "force": true
}


⸻

核心逻辑

if 当前版本 < 最低支持版本:
    force = true

if 当前版本 < 最新版本:
    hasUpdate = true
else:
    hasUpdate = false


⸻

说明
	•	最新版本从数据库读取
	•	支持未来扩展灰度发布策略

⸻

⸻

5.3 文件分发模块

⸻

职责
	•	提供 APK 下载服务
	•	支持高并发访问

⸻

实现方式
	•	使用 OSS（对象存储）
	•	使用 CDN 加速

⸻

文件结构建议

/app-release/
    v1.0.1.apk
    v1.0.2.apk


⸻

下载地址示例

https://cdn.xxx.com/app/v1.0.10.apk


⸻

要求
	•	支持断点续传（HTTP Range）
	•	高带宽
	•	高可用

⸻

⸻

5.4 ReportService（上报模块）

⸻

职责
	•	接收客户端更新结果
	•	记录更新日志

⸻

输入

{
  "deviceId": "device-001",
  "versionCode": 10,
  "status": "SUCCESS",
  "errorMsg": ""
}


⸻

输出

{
  "success": true
}


⸻

作用
	•	问题排查
	•	后续统计分析

⸻

⸻

5.5 DeviceService（简化模块）

⸻

职责
	•	记录设备信息
	•	更新设备当前版本

⸻

当前实现
	•	仅记录：
	•	deviceId
	•	currentVersion
	•	lastSeenTime

⸻

⸻

六、API 设计

⸻

6.1 检查更新接口（核心）

⸻

请求

POST /api/v1/update/check
``` id="api-check-full"

---

#### 请求体

```json id="check-request"
{
  "deviceId": "device-001",
  "versionCode": 5
}


⸻

返回

{
  "hasUpdate": true,
  "versionCode": 10,
  "versionName": "1.0.10",
  "force": true,
  "downloadUrl": "https://cdn.xxx.com/app.apk",
  "md5": "abc123",
  "fileSize": 12345678,
  "minSupportVersion": 8,
  "updateLog": "修复若干问题"
}


⸻

字段说明

字段	含义
hasUpdate	是否有更新
force	是否强制更新
minSupportVersion	最低支持版本
downloadUrl	APK 下载地址


⸻

⸻

6.2 更新结果上报接口

⸻

请求

POST /api/v1/update/report
``` id="api-report-full"

---

#### 请求体

```json id="report-request"
{
  "deviceId": "device-001",
  "versionCode": 10,
  "status": "SUCCESS",
  "errorMsg": ""
}


⸻

⸻

七、数据库设计

⸻

7.1 version 表（核心）

id
version_code
version_name
download_url
md5
file_size
force_update
min_support_version
create_time


⸻

7.2 device 表

device_id
current_version
last_seen_time


⸻

7.3 update_log 表

id
device_id
version_code
status
error_msg
create_time


⸻

⸻

八、安全设计

⸻

8.1 HTTPS
	•	所有接口必须使用 HTTPS

⸻

8.2 参数校验
	•	deviceId 必填
	•	versionCode 必须合法

⸻

8.3 防刷机制
	•	限制请求频率（例如：1分钟1次）

⸻

⸻

九、扩展设计（低优先级）

⸻

9.1 灰度发布
	•	按 deviceId 控制更新范围

⸻

9.2 日志监控
	•	接入 ELK / Prometheus

⸻

9.3 设备管理
	•	设备分组
	•	版本统计

⸻

⸻




⸻

⸻

十一、总结

⸻

服务端核心职责：
	1.	决定是否更新
	2.	提供下载地址
	3.	接收更新结果

⸻

系统优先保证 OTA 主流程稳定，其余功能按需扩展。

---

