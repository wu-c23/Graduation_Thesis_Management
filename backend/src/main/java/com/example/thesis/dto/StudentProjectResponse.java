package com.example.thesis.dto;

import java.util.List;

public record StudentProjectResponse(
        Long projectId,
        String title,
        UserView student,
        UserView advisor,
        int completedStepCount,
        int currentStep,
        boolean allCompleted,
        List<StepStatus> steps,
        List<ArtifactView> artifacts,
        List<DesignReviewView> designReviews
) {
}
