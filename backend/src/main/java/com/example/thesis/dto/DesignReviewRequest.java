package com.example.thesis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 导师设计评审：0–100 分，结论为 PASS 或 RETURN。
 */
public record DesignReviewRequest(
        @NotNull(message = "评分不能为空")
        @Min(value = 0, message = "评分不能小于 0")
        @Max(value = 100, message = "评分不能大于 100")
        Integer score,

        @Size(max = 1000, message = "评语不能超过 1000 字")
        String comment,

        @NotBlank(message = "评审结论不能为空")
        String result
) {
}
