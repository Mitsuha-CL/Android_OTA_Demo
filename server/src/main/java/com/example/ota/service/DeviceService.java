package com.example.ota.service;

import com.example.ota.mapper.DeviceMapper;
import com.example.ota.model.entity.Device;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;

    public void registerOrUpdate(String deviceId, String currentVersion) {
        Device device = deviceMapper.selectByDeviceId(deviceId);
        if (device == null) {
            device = new Device();
            device.setDeviceId(deviceId);
            device.setCurrentVersion(currentVersion);
            device.setLastSeenTime(LocalDateTime.now());
            deviceMapper.insert(device);
        } else {
            deviceMapper.updateLastSeenTime(deviceId, LocalDateTime.now());
        }
    }

    public void updateVersion(String deviceId, String newVersion) {
        deviceMapper.updateVersion(deviceId, newVersion, LocalDateTime.now());
    }
}
