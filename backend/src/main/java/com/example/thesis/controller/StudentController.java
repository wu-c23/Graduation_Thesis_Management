package com.example.thesis.controller;

import com.example.thesis.auth.AuthContext;
import com.example.thesis.common.ApiResponse;
import com.example.thesis.dto.ArtifactView;
import com.example.thesis.dto.RepoBindingRequest;
import com.example.thesis.dto.RepoBindingView;
import com.example.thesis.dto.RepoManualCountRequest;
import com.example.thesis.dto.StudentProjectResponse;
import com.example.thesis.dto.WeeklyReportRequest;
import com.example.thesis.dto.WeeklyReportView;
import com.example.thesis.service.ArtifactService;
import com.example.thesis.service.ProjectProgressService;
import com.example.thesis.service.RepoBindingService;
import com.example.thesis.service.WeeklyReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final ProjectProgressService projectProgressService;
    private final ArtifactService artifactService;
    private final WeeklyReportService weeklyReportService;
    private final RepoBindingService repoBindingService;

    public StudentController(
            ProjectProgressService projectProgressService,
            ArtifactService artifactService,
            WeeklyReportService weeklyReportService,
            RepoBindingService repoBindingService
    ) {
        this.projectProgressService = projectProgressService;
        this.artifactService = artifactService;
        this.weeklyReportService = weeklyReportService;
        this.repoBindingService = repoBindingService;
    }

    @GetMapping("/project")
    public ApiResponse<StudentProjectResponse> project(HttpServletRequest request) {
        return ApiResponse.ok(projectProgressService.getStudentProject(AuthContext.get(request).id()));
    }

    @GetMapping("/artifacts")
    public ApiResponse<List<ArtifactView>> artifacts(HttpServletRequest request) {
        return ApiResponse.ok(artifactService.listForStudent(AuthContext.get(request).id()));
    }

    /**
     * 文件上传与外链提交共用同一入口，两者必须二选一。
     * 测试报告可额外携带用例数、通过率与缺陷数。
     */
    @PostMapping(value = "/artifacts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ArtifactView> submitArtifact(
            @RequestParam String type,
            @RequestParam(required = false) String externalUrl,
            @RequestParam(required = false) Integer testCaseCount,
            @RequestParam(required = false) Integer testPassRate,
            @RequestParam(required = false) Integer defectCount,
            @RequestPart(required = false) MultipartFile file,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(artifactService.submit(
                AuthContext.get(request).id(), type, externalUrl, file,
                testCaseCount, testPassRate, defectCount));
    }

    @GetMapping("/weekly-reports")
    public ApiResponse<List<WeeklyReportView>> weeklyReports(HttpServletRequest request) {
        return ApiResponse.ok(weeklyReportService.listForStudent(AuthContext.get(request).id()));
    }

    @PostMapping("/weekly-reports")
    public ApiResponse<WeeklyReportView> submitWeeklyReport(
            @Valid @RequestBody WeeklyReportRequest reportRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(weeklyReportService.submit(
                AuthContext.get(request).id(), reportRequest));
    }

    @PostMapping("/repo-binding")
    public ApiResponse<RepoBindingView> bindRepo(
            @Valid @RequestBody RepoBindingRequest bindingRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(repoBindingService.bind(
                AuthContext.get(request).id(), bindingRequest));
    }

    @PostMapping("/repo-binding/sync")
    public ApiResponse<RepoBindingView> syncRepo(HttpServletRequest request) {
        return ApiResponse.ok(repoBindingService.sync(AuthContext.get(request).id()));
    }

    @PostMapping("/repo-binding/manual-count")
    public ApiResponse<RepoBindingView> manualRepoCount(
            @Valid @RequestBody RepoManualCountRequest countRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(repoBindingService.manualCount(
                AuthContext.get(request).id(), countRequest));
    }
}
