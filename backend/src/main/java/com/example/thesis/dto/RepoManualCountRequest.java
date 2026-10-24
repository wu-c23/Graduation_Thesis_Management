package com.example.thesis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 自动同步失败时的手工兜底：至少填写提交数，最近提交时间可选。
 */
public record RepoManualCountRequest(
        @NotNull(message = "提交数不能为空")
        @Min(value = 0, message = "提交数不能小于 0")
        @Max(value = 100000, message = "提交数不能大于 100000")
        Integer commitCount,

        LocalDateTime latestCommitAt
) {
}
