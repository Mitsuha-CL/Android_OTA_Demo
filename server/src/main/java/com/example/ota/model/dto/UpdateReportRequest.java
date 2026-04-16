package com.example.ota.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateReportRequest {

    @NotBlank(message = "deviceId is required")
    private String deviceId;

    @NotBlank(message = "versionCode is required")
    private String versionCode;

    @NotBlank(message = "status is required")
    private String status;

    private String errorMsg;
}
