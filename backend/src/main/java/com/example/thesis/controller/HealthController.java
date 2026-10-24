package com.example.thesis.controller;

import com.example.thesis.common.ApiResponse;
import com.example.thesis.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final UserMapper userMapper;

    public HealthController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 第 1 周验收接口：同时验证 Web 层、MyBatis-Plus 与 H2 数据库。
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        long userCount = userMapper.selectCount(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("database", "UP");
        data.put("userCount", userCount);
        data.put("phase", "week-2-auth");
        data.put("timestamp", OffsetDateTime.now().toString());
        return ApiResponse.ok(data);
    }
}
