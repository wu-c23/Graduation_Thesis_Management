package com.example.thesis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 导师批阅周报：status 为 REVIEWED（通过）或 RETURNED（退回）。
 */
public record WeeklyReportReviewRequest(
        @NotBlank(message = "批阅结论不能为空")
        String status,

        @Size(max = 1000, message = "批阅意见不能超过 1000 字")
        String comment
) {
}
