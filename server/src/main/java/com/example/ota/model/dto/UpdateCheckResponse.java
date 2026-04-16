package com.example.ota.model.dto;

import lombok.Data;

@Data
public class UpdateCheckResponse {

    private boolean hasUpdate;
    private String versionCode;
    private String versionName;
    private boolean force;
    private String downloadUrl;
    private String md5;
    private long fileSize;
    private String minSupportVersion;
    private String updateLog;
}
