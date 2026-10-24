package com.example.thesis.dto;

import com.example.thesis.entity.WeeklyReport;

import java.time.LocalDateTime;
import java.util.Map;

public record WeeklyReportView(
        Long id,
        Long projectId,
        Integer weekNo,
        String content,
        String status,
        String statusLabel,
        String comment,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt
) {

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "SUBMITTED", "待批阅",
            "REVIEWED", "已批阅",
            "RETURNED", "已退回"
    );

    public static WeeklyReportView from(WeeklyReport report) {
        return new WeeklyReportView(
                report.getId(),
                report.getProjectId(),
                report.getWeekNo(),
                report.getContent(),
                report.getStatus(),
                STATUS_LABELS.getOrDefault(report.getStatus(), report.getStatus()),
                report.getComment(),
                report.getSubmittedAt(),
                report.getReviewedAt()
        );
    }
}
