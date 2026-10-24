package com.example.thesis.controller;

import com.example.thesis.auth.AuthContext;
import com.example.thesis.service.ArtifactService;
import com.example.thesis.service.DownloadFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 材料文件下载：必须登录，且由 ArtifactService 校验学生/导师的项目归属。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final ArtifactService artifactService;

    public FileController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @GetMapping("/{artifactId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long artifactId,
            HttpServletRequest request
    ) {
        DownloadFile file = artifactService.download(artifactId, AuthContext.get(request));
        Resource resource = new FileSystemResource(file.path());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
