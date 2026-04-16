package com.example.ota.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("update_log")
public class UpdateLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceId;

    private String versionCode;

    private String status;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
