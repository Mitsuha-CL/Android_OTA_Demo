package com.example.ota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ota.model.entity.Version;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VersionMapper extends BaseMapper<Version> {

    @Select("SELECT * FROM version WHERE status = 'active'")
    List<Version> selectAllActive();

    @Select("SELECT * FROM version WHERE version_code = #{versionCode}")
    Version selectByVersionCode(@Param("versionCode") String versionCode);
}
