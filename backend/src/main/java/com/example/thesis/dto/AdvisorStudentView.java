package com.example.thesis.dto;

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
        long pendingCount
) {
}
