package com.example.ota.controller;

import com.example.ota.model.dto.ApiResponse;
import com.example.ota.model.dto.UpdateCheckRequest;
import com.example.ota.model.dto.UpdateCheckResponse;
import com.example.ota.model.dto.UpdateReportRequest;
import com.example.ota.service.ReportService;
import com.example.ota.service.VersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
