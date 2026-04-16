package com.example.ota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ota.model.entity.Device;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    @Select("SELECT * FROM device WHERE device_id = #{deviceId}")
    Device selectByDeviceId(String deviceId);

    @Update("UPDATE device SET last_seen_time = #{lastSeenTime}, updated_at = NOW() WHERE device_id = #{deviceId}")
    int updateLastSeenTime(@Param("deviceId") String deviceId, @Param("lastSeenTime") LocalDateTime lastSeenTime);

    @Update("UPDATE device SET current_version = #{newVersion}, last_seen_time = #{lastSeenTime}, updated_at = NOW() WHERE device_id = #{deviceId}")
    int updateVersion(@Param("deviceId") String deviceId, @Param("newVersion") String newVersion, @Param("lastSeenTime") LocalDateTime lastSeenTime);
}
