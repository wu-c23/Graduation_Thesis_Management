package com.example.thesis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.thesis.common.ApiException;
import com.example.thesis.dto.RepoBindingRequest;
import com.example.thesis.dto.RepoBindingView;
import com.example.thesis.dto.RepoManualCountRequest;
import com.example.thesis.entity.Project;
import com.example.thesis.entity.RepoBinding;
import com.example.thesis.mapper.ProjectMapper;
import com.example.thesis.mapper.RepoBindingMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公开 GitHub / Gitee 仓库绑定与提交统计同步。
 * 自动同步失败时保留 FAILED 状态，学生可手工填写提交数完成兜底。
 */
@Service
public class RepoBindingService {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_NOT_SYNCED = "NOT_SYNCED";

    private static final Logger log = LoggerFactory.getLogger(RepoBindingService.class);
    private static final Pattern PAGE_PATTERN = Pattern.compile("[?&]page=(\\d+)");
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");

    private final RepoBindingMapper repoBindingMapper;
    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RepoBindingService(
            RepoBindingMapper repoBindingMapper,
            ProjectMapper projectMapper,
            ObjectMapper objectMapper
    ) {
        this.repoBindingMapper = repoBindingMapper;
        this.projectMapper = projectMapper;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public RepoBindingView getForProject(Long projectId) {
        RepoBinding binding = findByProject(projectId);
        return binding == null ? null : RepoBindingView.from(binding);
    }

    @Transactional
    public RepoBindingView bind(Long studentId, RepoBindingRequest request) {
        Project project = requireStudentProject(studentId);
        RepoRef ref = parseRepoUrl(request.repoUrl());

        RepoBinding existing = findByProject(project.getId());
        if (existing == null) {
            RepoBinding binding = new RepoBinding();
            binding.setProjectId(project.getId());
            binding.setRepoUrl(ref.canonicalUrl());
            binding.setCommitCount(0);
            binding.setSyncStatus(STATUS_NOT_SYNCED);
            repoBindingMapper.insert(binding);
            return RepoBindingView.from(binding);
        }

        repoBindingMapper.update(null, new LambdaUpdateWrapper<RepoBinding>()
                .eq(RepoBinding::getId, existing.getId())
                .set(RepoBinding::getRepoUrl, ref.canonicalUrl())
                .set(RepoBinding::getCommitCount, 0)
                .set(RepoBinding::getLatestCommitAt, null)
                .set(RepoBinding::getLastSyncAt, null)
                .set(RepoBinding::getSyncStatus, STATUS_NOT_SYNCED));
        existing.setRepoUrl(ref.canonicalUrl());
        existing.setCommitCount(0);
        existing.setLatestCommitAt(null);
        existing.setLastSyncAt(null);
        existing.setSyncStatus(STATUS_NOT_SYNCED);
        return RepoBindingView.from(existing);
    }

    /**
     * 调用平台公开 API 同步提交数与最近提交时间；网络或仓库不可用时返回 FAILED 而不是抛出异常，
     * 便于前端立即引导到手工填写。
     */
    @Transactional
    public RepoBindingView sync(Long studentId) {
        Project project = requireStudentProject(studentId);
        RepoBinding binding = findByProject(project.getId());
        if (binding == null) {
            throw ApiException.badRequest("请先绑定代码仓库");
        }

        LocalDateTime now = LocalDateTime.now();
        try {
            RepoRef ref = parseRepoUrl(binding.getRepoUrl());
            RepoStats stats = fetchStats(ref);
            repoBindingMapper.update(null, new LambdaUpdateWrapper<RepoBinding>()
                    .eq(RepoBinding::getId, binding.getId())
                    .set(RepoBinding::getCommitCount, stats.commitCount())
                    .set(RepoBinding::getLatestCommitAt, stats.latestCommitAt())
                    .set(RepoBinding::getLastSyncAt, now)
                    .set(RepoBinding::getSyncStatus, STATUS_SUCCESS));
            binding.setCommitCount(stats.commitCount());
            binding.setLatestCommitAt(stats.latestCommitAt());
            binding.setLastSyncAt(now);
            binding.setSyncStatus(STATUS_SUCCESS);
        } catch (Exception exception) {
            log.info("仓库同步失败 projectId={} reason={}", project.getId(), exception.getMessage());
            repoBindingMapper.update(null, new LambdaUpdateWrapper<RepoBinding>()
                    .eq(RepoBinding::getId, binding.getId())
                    .set(RepoBinding::getLastSyncAt, now)
                    .set(RepoBinding::getSyncStatus, STATUS_FAILED));
            binding.setLastSyncAt(now);
            binding.setSyncStatus(STATUS_FAILED);
        }
        return RepoBindingView.from(binding);
    }

    @Transactional
    public RepoBindingView manualCount(Long studentId, RepoManualCountRequest request) {
        Project project = requireStudentProject(studentId);
        RepoBinding binding = findByProject(project.getId());
        if (binding == null) {
            throw ApiException.badRequest("请先绑定代码仓库");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime latest = request.latestCommitAt() == null ? binding.getLatestCommitAt()
                : request.latestCommitAt();
        if (latest != null && latest.isAfter(now)) {
            throw ApiException.badRequest("最近提交时间不能晚于当前时间");
        }

        repoBindingMapper.update(null, new LambdaUpdateWrapper<RepoBinding>()
                .eq(RepoBinding::getId, binding.getId())
                .set(RepoBinding::getCommitCount, request.commitCount())
                .set(RepoBinding::getLatestCommitAt, latest)
                .set(RepoBinding::getLastSyncAt, now)
                .set(RepoBinding::getSyncStatus, STATUS_SUCCESS));
        binding.setCommitCount(request.commitCount());
        binding.setLatestCommitAt(latest);
        binding.setLastSyncAt(now);
        binding.setSyncStatus(STATUS_SUCCESS);
        return RepoBindingView.from(binding);
    }

    private RepoStats fetchStats(RepoRef ref) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ref.apiUrl()))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("User-Agent", "GraduationThesisPlatform/1.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("仓库不可访问（HTTP " + response.statusCode() + "）");
        }

        JsonNode body = objectMapper.readTree(response.body());
        if (!body.isArray() || body.isEmpty()) {
            throw new IllegalStateException("仓库暂无提交记录");
        }

        int commitCount = parseCommitCount(response);
        JsonNode latestNode = body.get(0);
        LocalDateTime latestCommitAt = parseDateTime(latestNode
                .path("commit").path("committer").path("date").asText(null));
        if (latestCommitAt == null) {
            latestCommitAt = parseDateTime(latestNode
                    .path("commit").path("author").path("date").asText(null));
        }
        return new RepoStats(commitCount, latestCommitAt);
    }

    private int parseCommitCount(HttpResponse<String> response) {
        String total = response.headers().firstValue("X-Total-Count").orElse(null);
        if (total != null) {
            try {
                return Math.max(0, Integer.parseInt(total.trim()));
            } catch (NumberFormatException ignored) {
                // 继续尝试 Link 头。
            }
        }
        for (String link : response.headers().allValues("Link")) {
            int lastPage = parseLastPage(link);
            if (lastPage > 0) {
                return lastPage;
            }
        }
        return 1;
    }

    private int parseLastPage(String linkHeader) {
        if (linkHeader == null || !linkHeader.contains("rel=\"last\"")) {
            return -1;
        }
        for (String part : linkHeader.split(",")) {
            if (!part.contains("rel=\"last\"")) {
                continue;
            }
            Matcher matcher = PAGE_PATTERN.matcher(part);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    private RepoRef parseRepoUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw ApiException.badRequest("仓库地址不能为空");
        }
        String value = rawUrl.trim();
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception exception) {
            throw ApiException.badRequest("仓库地址格式不正确");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw ApiException.badRequest("仓库地址必须是 https://github.com 或 https://gitee.com 链接");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw ApiException.badRequest("仓库地址不能包含查询参数或锚点");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        boolean github = "github.com".equals(host);
        boolean gitee = "gitee.com".equals(host);
        if (!github && !gitee) {
            throw ApiException.badRequest("仅支持 GitHub 或 Gitee 公开仓库");
        }

        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
        String[] segments = path.split("/");
        if (segments.length != 2 || segments[0].isBlank() || segments[1].isBlank()) {
            throw ApiException.badRequest("仓库地址应形如 https://github.com/用户名/仓库名");
        }
        String owner = segments[0];
        String repoSegment = segments[1];
        String repo = repoSegment.toLowerCase(Locale.ROOT).endsWith(".git")
                ? repoSegment.substring(0, repoSegment.length() - 4)
                : repoSegment;
        if (repo.isBlank() || ".".equals(owner) || "..".equals(owner)
                || ".".equals(repo) || "..".equals(repo)
                || !NAME_PATTERN.matcher(owner).matches()
                || !NAME_PATTERN.matcher(repo).matches()) {
            throw ApiException.badRequest("仓库地址中的用户名或仓库名不合法");
        }

        String canonicalUrl = "https://" + host + "/" + owner + "/" + repo;
        String apiUrl = github
                ? "https://api.github.com/repos/" + owner + "/" + repo + "/commits?per_page=1"
                : "https://gitee.com/api/v5/repos/" + owner + "/" + repo + "/commits?per_page=1";
        return new RepoRef(canonicalUrl, apiUrl);
    }

    private RepoBinding findByProject(Long projectId) {
        List<RepoBinding> bindings = repoBindingMapper.selectList(new LambdaQueryWrapper<RepoBinding>()
                .eq(RepoBinding::getProjectId, projectId)
                .last("LIMIT 1"));
        return bindings.isEmpty() ? null : bindings.get(0);
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

    private record RepoRef(String canonicalUrl, String apiUrl) {
    }

    private record RepoStats(int commitCount, LocalDateTime latestCommitAt) {
    }
}
