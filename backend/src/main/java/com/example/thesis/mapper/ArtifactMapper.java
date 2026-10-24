package com.example.thesis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.thesis.entity.Artifact;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArtifactMapper extends BaseMapper<Artifact> {
}
