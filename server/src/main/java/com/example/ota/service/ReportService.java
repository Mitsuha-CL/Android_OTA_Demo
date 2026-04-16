package com.example.ota.service;

import com.example.ota.mapper.UpdateLogMapper;
import com.example.ota.model.dto.UpdateReportRequest;
import com.example.ota.model.entity.UpdateLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UpdateLogMapper updateLogMapper;
    private final DeviceService deviceService;

    public void handleReport(UpdateReportRequest request) {
        UpdateLog log = new UpdateLog();
        log.setDeviceId(request.getDeviceId());
        log.setVersionCode(request.getVersionCode());
        log.setStatus(request.getStatus());
        log.setErrorMsg(request.getErrorMsg());
        updateLogMapper.insert(log);

        if ("SUCCESS".equals(request.getStatus())) {
            deviceService.updateVersion(request.getDeviceId(), request.getVersionCode());
        }
    }
}
