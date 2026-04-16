CREATE DATABASE IF NOT EXISTS `ota` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `ota`;

CREATE TABLE `version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `version_code` VARCHAR(64) NOT NULL COMMENT '版本号（如 1.2.3）',
  `version_name` VARCHAR(64) NOT NULL COMMENT '版本名称（展示用）',
  `download_url` VARCHAR(512) NOT NULL COMMENT 'APK 下载路径',
  `md5` VARCHAR(64) NOT NULL COMMENT 'APK 文件 MD5 值',
  `file_size` BIGINT UNSIGNED NOT NULL COMMENT 'APK 文件大小（字节）',
  `force_update` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否强制更新',
  `min_support_version` VARCHAR(64) NOT NULL COMMENT '最低支持版本',
  `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态：active / archived',
  `update_log` VARCHAR(1024) DEFAULT '' COMMENT '更新日志',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version_code` (`version_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本信息表';

CREATE TABLE `device` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
  `current_version` VARCHAR(64) NOT NULL COMMENT '当前应用版本',
  `last_seen_time` DATETIME NOT NULL COMMENT '最后活跃时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

CREATE TABLE `update_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` VARCHAR(128) NOT NULL COMMENT '设备标识',
  `version_code` VARCHAR(64) NOT NULL COMMENT '目标版本号',
  `status` VARCHAR(32) NOT NULL COMMENT '更新状态',
  `error_msg` VARCHAR(512) DEFAULT '' COMMENT '错误信息',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_version_code` (`version_code`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='更新日志表';
