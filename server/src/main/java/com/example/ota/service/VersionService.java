package com.example.ota.service;

import com.example.ota.mapper.VersionMapper;
import com.example.ota.model.dto.UpdateCheckRequest;
import com.example.ota.model.dto.UpdateCheckResponse;
import com.example.ota.model.entity.Version;
import com.example.ota.storage.StorageProvider;
import com.example.ota.util.VersionComparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VersionService {

    private final VersionMapper versionMapper;
    private final DeviceService deviceService;
    private final StorageProvider storageProvider;

    public UpdateCheckResponse checkUpdate(UpdateCheckRequest request) {
        deviceService.registerOrUpdate(request.getDeviceId(), request.getVersionCode());

        List<Version> activeVersions = versionMapper.selectAllActive();
        if (activeVersions.isEmpty()) {
            return buildNoUpdateResponse(request.getVersionCode());
        }

        Version latestVersion = activeVersions.stream()
                .max(Comparator.comparing(Version::getVersionCode, VersionComparator.INSTANCE))
                .orElse(null);

        if (latestVersion == null) {
            return buildNoUpdateResponse(request.getVersionCode());
        }

        int cmp = VersionComparator.INSTANCE.compare(request.getVersionCode(), latestVersion.getVersionCode());
        boolean hasUpdate = cmp < 0;
        boolean force = false;

        if (latestVersion.getMinSupportVersion() != null) {
            int minCmp = VersionComparator.INSTANCE.compare(request.getVersionCode(), latestVersion.getMinSupportVersion());
            force = minCmp < 0;
        }

        if (!hasUpdate) {
            return buildNoUpdateResponse(request.getVersionCode());
        }

        return buildUpdateResponse(latestVersion, force);
    }

    private UpdateCheckResponse buildNoUpdateResponse(String versionCode) {
        UpdateCheckResponse response = new UpdateCheckResponse();
        response.setHasUpdate(false);
        response.setVersionCode(versionCode);
        response.setVersionName("");
        response.setForce(false);
        response.setDownloadUrl("");
        response.setMd5("");
        response.setFileSize(0);
        response.setMinSupportVersion(versionCode);
        response.setUpdateLog("");
        return response;
    }

    private UpdateCheckResponse buildUpdateResponse(Version version, boolean force) {
        UpdateCheckResponse response = new UpdateCheckResponse();
        response.setHasUpdate(true);
        response.setVersionCode(version.getVersionCode());
        response.setVersionName(version.getVersionName());
        response.setForce(force || version.getForceUpdate());
        response.setDownloadUrl(storageProvider.getDownloadUrl(version.getVersionCode()));
        response.setMd5(version.getMd5());
        response.setFileSize(version.getFileSize());
        response.setMinSupportVersion(version.getMinSupportVersion());
        response.setUpdateLog(version.getUpdateLog());
        return response;
    }
}
