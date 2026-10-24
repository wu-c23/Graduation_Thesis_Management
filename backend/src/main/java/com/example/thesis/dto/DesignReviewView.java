package com.example.thesis.dto;

import com.example.thesis.entity.DesignReview;

import java.time.LocalDateTime;

public record DesignReviewView(
        Long id,
        Long projectId,
        Long reviewerId,
        String reviewerName,
        Integer score,
        String comment,
        String result,
        Integer roundNo,
        LocalDateTime reviewedAt
) {

    public static DesignReviewView from(DesignReview review, String reviewerName) {
        return new DesignReviewView(
                review.getId(),
                review.getProjectId(),
                review.getReviewerId(),
                reviewerName,
                review.getScore(),
                review.getComment(),
                review.getResult(),
                review.getRoundNo(),
                review.getReviewedAt()
        );
    }
}
