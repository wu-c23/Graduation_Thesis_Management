package com.example.thesis.controller;

import com.example.thesis.auth.AuthContext;
import com.example.thesis.common.ApiResponse;
import com.example.thesis.dto.AdvisorStudentsResponse;
import com.example.thesis.dto.ArtifactReviewRequest;
import com.example.thesis.dto.ArtifactView;
import com.example.thesis.dto.DesignReviewRequest;
import com.example.thesis.dto.DesignReviewView;
import com.example.thesis.dto.StudentProjectResponse;
import com.example.thesis.dto.WeeklyReportReviewRequest;
import com.example.thesis.dto.WeeklyReportView;
import com.example.thesis.service.ArtifactService;
import com.example.thesis.service.DesignReviewService;
import com.example.thesis.service.ProjectProgressService;
import com.example.thesis.service.WeeklyReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/advisor")
public class AdvisorController {

    private final ProjectProgressService projectProgressService;
    private final ArtifactService artifactService;
    private final DesignReviewService designReviewService;
    private final WeeklyReportService weeklyReportService;

    public AdvisorController(
            ProjectProgressService projectProgressService,
            ArtifactService artifactService,
            DesignReviewService designReviewService,
            WeeklyReportService weeklyReportService
    ) {
        this.projectProgressService = projectProgressService;
        this.artifactService = artifactService;
        this.designReviewService = designReviewService;
        this.weeklyReportService = weeklyReportService;
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

    @PostMapping("/artifacts/{artifactId}/review")
    public ApiResponse<ArtifactView> reviewArtifact(
            @PathVariable Long artifactId,
            @Valid @RequestBody ArtifactReviewRequest reviewRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(artifactService.review(
                AuthContext.get(request).id(), artifactId, reviewRequest));
    }

    @PostMapping("/projects/{projectId}/design-review")
    public ApiResponse<DesignReviewView> designReview(
            @PathVariable Long projectId,
            @Valid @RequestBody DesignReviewRequest reviewRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(designReviewService.create(
                AuthContext.get(request).id(), projectId, reviewRequest));
    }

    @GetMapping("/weekly-reports")
    public ApiResponse<List<WeeklyReportView>> weeklyReports(
            @RequestParam Long projectId,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(weeklyReportService.listForAdvisor(
                AuthContext.get(request).id(), projectId));
    }

    @PostMapping("/weekly-reports/{reportId}/review")
    public ApiResponse<WeeklyReportView> reviewWeeklyReport(
            @PathVariable Long reportId,
            @Valid @RequestBody WeeklyReportReviewRequest reviewRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(weeklyReportService.review(
                AuthContext.get(request).id(), reportId, reviewRequest));
    }
}
