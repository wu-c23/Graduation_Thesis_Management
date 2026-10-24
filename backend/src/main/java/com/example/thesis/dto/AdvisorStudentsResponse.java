package com.example.thesis.dto;

import java.util.List;

public record AdvisorStudentsResponse(
        int studentCount,
        long pendingArtifactCount,
        long pendingWeeklyReportCount,
        List<AdvisorStudentView> students
) {
}
