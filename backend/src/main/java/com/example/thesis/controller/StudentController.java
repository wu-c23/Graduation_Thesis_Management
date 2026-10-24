package com.example.thesis.controller;

import com.example.thesis.auth.AuthContext;
import com.example.thesis.common.ApiResponse;
import com.example.thesis.dto.ArtifactView;
import com.example.thesis.dto.StudentProjectResponse;
import com.example.thesis.service.ArtifactService;
import com.example.thesis.service.ProjectProgressService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public StudentController(
            ProjectProgressService projectProgressService,
            ArtifactService artifactService
    ) {
        this.projectProgressService = projectProgressService;
        this.artifactService = artifactService;
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
     */
    @PostMapping(value = "/artifacts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ArtifactView> submitArtifact(
            @RequestParam String type,
            @RequestParam(required = false) String externalUrl,
            @RequestPart(required = false) MultipartFile file,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(artifactService.submit(
                AuthContext.get(request).id(), type, externalUrl, file));
    }
}
