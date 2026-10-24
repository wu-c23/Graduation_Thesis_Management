package com.example.thesis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RepoBindingRequest(
        @NotBlank(message = "仓库地址不能为空")
        @Size(max = 500, message = "仓库地址不能超过 500 个字符")
        String repoUrl
) {
}
