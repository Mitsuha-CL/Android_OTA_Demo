package com.example.ota.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateCheckRequest {

    @NotBlank(message = "deviceId is required")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "deviceId can only contain letters, numbers, and hyphens")
    private String deviceId;

    @NotBlank(message = "versionCode is required")
    private String versionCode;
}
