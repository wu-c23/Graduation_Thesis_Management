package com.example.thesis.controller;

import com.example.thesis.auth.AuthContext;
import com.example.thesis.common.ApiResponse;
import com.example.thesis.dto.AdvisorStudentsResponse;
import com.example.thesis.dto.StudentProjectResponse;
import com.example.thesis.service.ProjectProgressService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/advisor")
public class AdvisorController {

    private final ProjectProgressService projectProgressService;

    public AdvisorController(ProjectProgressService projectProgressService) {
        this.projectProgressService = projectProgressService;
    }

    @GetMapping("/students")
    public ApiResponse<AdvisorStudentsResponse> students(HttpServletRequest request) {
        return ApiResponse.ok(projectProgressService.getAdvisorStudents(AuthContext.get(request).id()));
    }

    @GetMapping("/students/{projectId}")
    public ApiResponse<StudentProjectResponse> studentDetail(
            @PathVariable Long projectId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(projectProgressService.getAdvisorStudentDetail(
                AuthContext.get(request).id(), projectId));
    }
}
