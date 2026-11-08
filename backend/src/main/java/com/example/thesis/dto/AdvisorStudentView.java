package com.example.thesis.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdvisorStudentView(
        Long projectId,
        Long studentId,
        String studentName,
        String username,
        String title,
        int completedStepCount,
        int currentStep,
        String currentStage,
        String currentStepStatus,
        long pendingCount,
        LocalDateTime lastActiveAt,
        List<StepStatus> steps
) {
}
