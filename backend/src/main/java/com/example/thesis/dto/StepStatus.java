package com.example.thesis.dto;

/**
 * 六步流程中的一步。status 取值：
 * COMPLETED / IN_REVIEW / RETURNED / NOT_STARTED。
 */
public record StepStatus(String key, String title, String status, String description) {
}
