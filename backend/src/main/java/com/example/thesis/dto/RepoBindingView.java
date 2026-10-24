package com.example.thesis.dto;

import com.example.thesis.entity.RepoBinding;

import java.time.LocalDateTime;
import java.util.Map;

public record RepoBindingView(
        Long id,
        Long projectId,
        String repoUrl,
        String repoHost,
        Integer commitCount,
        LocalDateTime latestCommitAt,
        LocalDateTime lastSyncAt,
        String syncStatus,
        String syncStatusLabel
) {

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "SUCCESS", "同步成功",
            "FAILED", "同步失败",
            "NOT_SYNCED", "未同步"
    );

    public static RepoBindingView from(RepoBinding binding) {
        String host = "UNKNOWN";
        if (binding.getRepoUrl() != null) {
            host = binding.getRepoUrl().contains("gitee.com") ? "GITEE" : "GITHUB";
        }
        return new RepoBindingView(
                binding.getId(),
                binding.getProjectId(),
                binding.getRepoUrl(),
                host,
                binding.getCommitCount(),
                binding.getLatestCommitAt(),
                binding.getLastSyncAt(),
                binding.getSyncStatus(),
                STATUS_LABELS.getOrDefault(binding.getSyncStatus(), binding.getSyncStatus())
        );
    }
}
