package com.example.thesis.dto;

import com.example.thesis.entity.Artifact;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对外返回的材料信息。不暴露服务器端存储路径，下载统一走 /api/files/{id}。
 */
public record ArtifactView(
        Long id,
        Long projectId,
        String type,
        String typeLabel,
        String fileName,
        String externalUrl,
        String status,
        Integer score,
        String feedback,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        boolean downloadable
) {

    private static final Map<String, String> TYPE_LABELS = Map.of(
            "REQUIREMENT", "需求文档",
            "PROTOTYPE", "原型",
            "TEST_REPORT", "测试报告"
    );

    public static ArtifactView from(Artifact artifact) {
        return new ArtifactView(
                artifact.getId(),
                artifact.getProjectId(),
                artifact.getType(),
                TYPE_LABELS.getOrDefault(artifact.getType(), artifact.getType()),
                artifact.getFileName(),
                artifact.getExternalUrl(),
                artifact.getStatus(),
                artifact.getScore(),
                artifact.getFeedback(),
                artifact.getSubmittedAt(),
                artifact.getReviewedAt(),
                artifact.getFilePath() != null && !artifact.getFilePath().isBlank()
        );
    }
}
