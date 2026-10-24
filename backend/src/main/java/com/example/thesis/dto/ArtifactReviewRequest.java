package com.example.thesis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 导师审核材料：status 为 APPROVED 或 RETURNED。
 */
public record ArtifactReviewRequest(
        @NotBlank(message = "审核结论不能为空")
        String status,

        @Size(max = 1000, message = "评语不能超过 1000 字")
        String feedback,

        @Min(value = 0, message = "评分不能小于 0")
        @Max(value = 100, message = "评分不能大于 100")
        Integer score
) {
}
