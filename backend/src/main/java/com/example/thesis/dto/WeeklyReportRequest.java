package com.example.thesis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WeeklyReportRequest(
        @NotNull(message = "周次不能为空")
        @Min(value = 1, message = "周次不能小于 1")
        @Max(value = 30, message = "周次不能大于 30")
        Integer weekNo,

        @NotBlank(message = "周报内容不能为空")
        @Size(max = 2000, message = "周报内容不能超过 2000 字")
        String content
) {
}
