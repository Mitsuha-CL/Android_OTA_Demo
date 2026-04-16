package com.example.ota.controller;

import com.example.ota.mapper.VersionMapper;
import com.example.ota.model.dto.ApiResponse;
import com.example.ota.model.entity.Version;
import com.example.ota.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.InputStreamResource;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/v1/apk")
@RequiredArgsConstructor
public class ApkDownloadController {

    private final VersionMapper versionMapper;
    private final StorageProvider storageProvider;

    @GetMapping("/download/{versionCode}")
    public ResponseEntity<?> downloadApk(@PathVariable String versionCode) {
        Version version = versionMapper.selectByVersionCode(versionCode);
        if (version == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("VERSION_NOT_FOUND", "version not found"));
        }

        try {
            InputStream inputStream = storageProvider.getInputStream(versionCode);
            InputStreamResource resource = new InputStreamResource(inputStream);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                    .contentLength(version.getFileSize())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"app_v" + versionCode + ".apk\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("INTERNAL_ERROR", "failed to read APK file"));
        }
    }
}
