package com.example.ota.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("version")
public class Version {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String versionCode;

    private String versionName;

    private String downloadUrl;

    private String md5;

    private Long fileSize;

    private Boolean forceUpdate;

    private String minSupportVersion;

    private String status;

    private String updateLog;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
