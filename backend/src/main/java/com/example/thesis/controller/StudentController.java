package com.example.thesis.controller;

import com.example.thesis.auth.AuthContext;
import com.example.thesis.common.ApiResponse;
import com.example.thesis.dto.StudentProjectResponse;
import com.example.thesis.service.ProjectProgressService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final ProjectProgressService projectProgressService;

    public StudentController(ProjectProgressService projectProgressService) {
        this.projectProgressService = projectProgressService;
    }

    @GetMapping("/project")
    public ApiResponse<StudentProjectResponse> project(HttpServletRequest request) {
        return ApiResponse.ok(projectProgressService.getStudentProject(AuthContext.get(request).id()));
    }
}
