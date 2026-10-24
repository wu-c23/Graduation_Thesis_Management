package com.example.thesis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.thesis.common.ApiException;
import com.example.thesis.dto.ArtifactReviewRequest;
import com.example.thesis.dto.ArtifactView;
import com.example.thesis.dto.UserView;
import com.example.thesis.entity.Artifact;
import com.example.thesis.entity.Project;
import com.example.thesis.mapper.ArtifactMapper;
import com.example.thesis.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 材料提交、历史查询、鉴权下载与导师审核。
 */
@Service
public class ArtifactService {

    public static final String TYPE_REQUIREMENT = "REQUIREMENT";
    public static final String TYPE_PROTOTYPE = "PROTOTYPE";
    public static final String TYPE_TEST_REPORT = "TEST_REPORT";

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_RETURNED = "RETURNED";

    private static final Set<String> SUBMITTABLE_TYPES = Set.of(TYPE_REQUIREMENT, TYPE_PROTOTYPE);
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "docx", "zip", "png", "jpg", "jpeg");
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_URL_LENGTH = 500;

    private final ArtifactMapper artifactMapper;
    private final ProjectMapper projectMapper;
    private final Path uploadRoot;
    private final long maxSizeBytes;

    public ArtifactService(
            ArtifactMapper artifactMapper,
            ProjectMapper projectMapper,
            @Value("${app.upload.dir:../uploads}") String uploadDir,
            @Value("${app.upload.max-size-bytes:20971520}") long maxSizeBytes
    ) {
        this.artifactMapper = artifactMapper;
        this.projectMapper = projectMapper;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
    }

    public List<ArtifactView> listByProject(Long projectId) {
        return artifactMapper.selectList(new LambdaQueryWrapper<Artifact>()
                        .eq(Artifact::getProjectId, projectId)
                        .orderByDesc(Artifact::getSubmittedAt)
                        .orderByDesc(Artifact::getId))
                .stream()
                .map(ArtifactView::from)
                .toList();
    }

    public List<ArtifactView> listForStudent(Long studentId) {
        Project project = requireStudentProject(studentId);
        return listByProject(project.getId());
    }

    /**
     * 文件上传与外链提交二选一；每次提交插入新记录，旧记录保留形成历史。
     */
    @Transactional
    public ArtifactView submit(Long studentId, String type, String externalUrl, MultipartFile file) {
        Project project = requireStudentProject(studentId);
        String normalizedType = normalizeType(type);

        Artifact latest = latestArtifact(project.getId(), normalizedType);
        if (latest != null && STATUS_SUBMITTED.equals(latest.getStatus())) {
            throw ApiException.badRequest("该材料正在等待导师审核，请勿重复提交");
        }

        boolean hasFile = file != null && !file.isEmpty();
        String url = trimToNull(externalUrl);
        if (hasFile && url != null) {
            throw ApiException.badRequest("文件与外部链接只能选择一种方式");
        }
        if (!hasFile && url == null) {
            throw ApiException.badRequest("请上传文件或填写外部链接");
        }

        String storedPath = null;
        String fileName = null;
        if (hasFile) {
            fileName = sanitizeFileName(file.getOriginalFilename());
            String extension = extensionOf(fileName);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw ApiException.badRequest("只支持 PDF、DOCX、ZIP、PNG、JPG 文件");
            }
            if (file.getSize() > maxSizeBytes) {
                throw ApiException.badRequest("文件大小超过限制");
            }
            storedPath = storeFile(project.getId(), extension, file);
        } else {
            validateExternalUrl(url);
        }

        Artifact artifact = new Artifact();
        artifact.setProjectId(project.getId());
        artifact.setType(normalizedType);
        artifact.setFilePath(storedPath);
        artifact.setFileName(fileName);
        artifact.setExternalUrl(url);
        artifact.setStatus(STATUS_SUBMITTED);
        artifact.setSubmittedAt(LocalDateTime.now());

        try {
            artifactMapper.insert(artifact);
        } catch (RuntimeException exception) {
            if (storedPath != null) {
                deleteQuietly(uploadRoot.resolve(storedPath));
            }
            throw exception;
        }
        return ArtifactView.from(artifact);
    }

    /**
     * 导师审核材料；只有待审核状态可以操作，退回时必须填写评语。
     */
    @Transactional
    public ArtifactView review(Long advisorId, Long artifactId, ArtifactReviewRequest request) {
        Artifact artifact = requireArtifact(artifactId);
        Project project = requireProject(artifact.getProjectId());
        if (!Objects.equals(project.getAdvisorId(), advisorId)) {
            throw ApiException.forbidden("无权审核该材料");
        }
        if (!STATUS_SUBMITTED.equals(artifact.getStatus())) {
            throw ApiException.badRequest("该材料已审核，请勿重复操作");
        }

        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!STATUS_APPROVED.equals(status) && !STATUS_RETURNED.equals(status)) {
            throw ApiException.badRequest("审核结论只能是 APPROVED 或 RETURNED");
        }
        String feedback = trimToNull(request.feedback());
        if (STATUS_RETURNED.equals(status) && feedback == null) {
            throw ApiException.badRequest("退回时必须填写评语");
        }

        artifact.setStatus(status);
        artifact.setFeedback(feedback);
        artifact.setScore(request.score());
        artifact.setReviewedAt(LocalDateTime.now());
        artifactMapper.updateById(artifact);
        return ArtifactView.from(artifact);
    }

    /**
     * 下载前校验角色与项目归属；学生只能下载本人材料，导师只能下载名下学生材料。
     */
    public DownloadFile download(Long artifactId, UserView user) {
        Artifact artifact = requireArtifact(artifactId);
        Project project = requireProject(artifact.getProjectId());

        if ("STUDENT".equals(user.role())) {
            if (!Objects.equals(project.getStudentId(), user.id())) {
                throw ApiException.forbidden("无权下载该材料");
            }
        } else if ("ADVISOR".equals(user.role())) {
            if (!Objects.equals(project.getAdvisorId(), user.id())) {
                throw ApiException.forbidden("无权下载该材料");
            }
        } else {
            throw ApiException.forbidden("当前账号无权下载材料");
        }

        if (artifact.getFilePath() == null || artifact.getFilePath().isBlank()) {
            throw ApiException.badRequest("该材料为外部链接，无文件可下载");
        }

        Path path = uploadRoot.resolve(artifact.getFilePath()).normalize();
        if (!path.startsWith(uploadRoot) || !Files.isRegularFile(path)) {
            throw ApiException.notFound("文件不存在或已被移除");
        }
        String fileName = artifact.getFileName() == null || artifact.getFileName().isBlank()
                ? path.getFileName().toString()
                : artifact.getFileName();
        return new DownloadFile(path, fileName);
    }

    private Project requireStudentProject(Long studentId) {
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getStudentId, studentId)
                .orderByAsc(Project::getId));
        if (projects.isEmpty()) {
            throw ApiException.notFound("未找到与当前账号关联的课题");
        }
        return projects.get(0);
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ApiException.notFound("学生课题不存在");
        }
        return project;
    }

    private Artifact requireArtifact(Long artifactId) {
        Artifact artifact = artifactMapper.selectById(artifactId);
        if (artifact == null) {
            throw ApiException.notFound("材料不存在");
        }
        return artifact;
    }

    private Artifact latestArtifact(Long projectId, String type) {
        List<Artifact> artifacts = artifactMapper.selectList(new LambdaQueryWrapper<Artifact>()
                .eq(Artifact::getProjectId, projectId)
                .eq(Artifact::getType, type)
                .orderByDesc(Artifact::getSubmittedAt)
                .orderByDesc(Artifact::getId)
                .last("LIMIT 1"));
        return artifacts.isEmpty() ? null : artifacts.get(0);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw ApiException.badRequest("材料类型不能为空");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (TYPE_TEST_REPORT.equals(normalized)) {
            throw ApiException.badRequest("测试报告将在第 4–5 周开放");
        }
        if (!SUBMITTABLE_TYPES.contains(normalized)) {
            throw ApiException.badRequest("不支持的材料类型");
        }
        return normalized;
    }

    private void validateExternalUrl(String url) {
        if (url.length() > MAX_URL_LENGTH) {
            throw ApiException.badRequest("外部链接不能超过 500 个字符");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            boolean http = scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            if (!http || uri.getHost() == null) {
                throw ApiException.badRequest("外部链接必须是 http/https 地址");
            }
        } catch (URISyntaxException exception) {
            throw ApiException.badRequest("外部链接格式不正确");
        }
    }

    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw ApiException.badRequest("文件名为空");
        }
        String name;
        try {
            name = Paths.get(originalName).getFileName().toString();
        } catch (InvalidPathException exception) {
            throw ApiException.badRequest("文件名不合法");
        }
        name = name.replaceAll("[\\r\\n\\t]", "").trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("文件名不合法");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw ApiException.badRequest("文件名过长");
        }
        return name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw ApiException.badRequest("文件必须包含扩展名");
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String storeFile(Long projectId, String extension, MultipartFile file) {
        String relativePath = projectId + "/" + UUID.randomUUID() + "." + extension;
        Path target = uploadRoot.resolve(relativePath).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw ApiException.badRequest("非法的文件路径");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败");
        }
        return relativePath;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不影响主流程，残留文件由 uploads 目录整体管理。
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
