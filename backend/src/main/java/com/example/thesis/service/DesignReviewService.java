package com.example.thesis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.thesis.common.ApiException;
import com.example.thesis.dto.DesignReviewRequest;
import com.example.thesis.dto.DesignReviewView;
import com.example.thesis.entity.Artifact;
import com.example.thesis.entity.DesignReview;
import com.example.thesis.entity.Project;
import com.example.thesis.entity.User;
import com.example.thesis.mapper.ArtifactMapper;
import com.example.thesis.mapper.DesignReviewMapper;
import com.example.thesis.mapper.ProjectMapper;
import com.example.thesis.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设计评审：需求与原型都通过后才允许评分与结论；每次评审保留轮次形成历史。
 */
@Service
public class DesignReviewService {

    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_RETURN = "RETURN";

    private final DesignReviewMapper designReviewMapper;
    private final ArtifactMapper artifactMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;

    public DesignReviewService(
            DesignReviewMapper designReviewMapper,
            ArtifactMapper artifactMapper,
            ProjectMapper projectMapper,
            UserMapper userMapper
    ) {
        this.designReviewMapper = designReviewMapper;
        this.artifactMapper = artifactMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
    }

    public List<DesignReviewView> listByProject(Long projectId) {
        List<DesignReview> reviews = designReviewMapper.selectList(new LambdaQueryWrapper<DesignReview>()
                .eq(DesignReview::getProjectId, projectId)
                .orderByDesc(DesignReview::getReviewedAt)
                .orderByDesc(DesignReview::getId));
        if (reviews.isEmpty()) {
            return List.of();
        }

        Set<Long> reviewerIds = reviews.stream()
                .map(DesignReview::getReviewerId)
                .collect(Collectors.toSet());
        Map<Long, String> reviewerNames = userMapper.selectBatchIds(reviewerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));

        return reviews.stream()
                .map(review -> DesignReviewView.from(
                        review,
                        reviewerNames.getOrDefault(review.getReviewerId(), "导师")
                ))
                .toList();
    }

    @Transactional
    public DesignReviewView create(Long advisorId, Long projectId, DesignReviewRequest request) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ApiException.notFound("学生课题不存在");
        }
        if (!Objects.equals(project.getAdvisorId(), advisorId)) {
            throw ApiException.forbidden("无权评审该学生");
        }

        Artifact requirement = latestArtifact(projectId, ArtifactService.TYPE_REQUIREMENT);
        Artifact prototype = latestArtifact(projectId, ArtifactService.TYPE_PROTOTYPE);
        if (requirement == null || !ArtifactService.STATUS_APPROVED.equals(requirement.getStatus())
                || prototype == null || !ArtifactService.STATUS_APPROVED.equals(prototype.getStatus())) {
            throw ApiException.badRequest("需求文档与原型均通过后才能进行设计评审");
        }

        String result = request.result().trim().toUpperCase(Locale.ROOT);
        if (!RESULT_PASS.equals(result) && !RESULT_RETURN.equals(result)) {
            throw ApiException.badRequest("评审结论只能是 PASS 或 RETURN");
        }

        Long existingCount = designReviewMapper.selectCount(new LambdaQueryWrapper<DesignReview>()
                .eq(DesignReview::getProjectId, projectId));

        DesignReview review = new DesignReview();
        review.setProjectId(projectId);
        review.setReviewerId(advisorId);
        review.setScore(request.score());
        review.setComment(trimToNull(request.comment()));
        review.setResult(result);
        review.setRoundNo(existingCount == null ? 1 : existingCount.intValue() + 1);
        review.setReviewedAt(LocalDateTime.now());
        designReviewMapper.insert(review);

        User advisor = userMapper.selectById(advisorId);
        return DesignReviewView.from(review, advisor == null ? "导师" : advisor.getRealName());
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
