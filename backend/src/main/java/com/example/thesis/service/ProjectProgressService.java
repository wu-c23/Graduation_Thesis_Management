package com.example.thesis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.thesis.common.ApiException;
import com.example.thesis.dto.AdvisorStudentView;
import com.example.thesis.dto.AdvisorStudentsResponse;
import com.example.thesis.dto.StepStatus;
import com.example.thesis.dto.StudentProjectResponse;
import com.example.thesis.dto.UserView;
import com.example.thesis.entity.Artifact;
import com.example.thesis.entity.DesignReview;
import com.example.thesis.entity.Project;
import com.example.thesis.entity.User;
import com.example.thesis.mapper.ArtifactMapper;
import com.example.thesis.mapper.DesignReviewMapper;
import com.example.thesis.mapper.ProjectMapper;
import com.example.thesis.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 第 2 周只计算前 3 步（需求、原型、设计评审）的真实状态；
 * 周报、仓库、测试报告按计划在第 4 周接入，当前显示“未开始”。
 */
@Service
public class ProjectProgressService {

    public static final String TYPE_REQUIREMENT = "REQUIREMENT";
    public static final String TYPE_PROTOTYPE = "PROTOTYPE";

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_NOT_STARTED = "NOT_STARTED";

    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final ArtifactMapper artifactMapper;
    private final DesignReviewMapper designReviewMapper;

    public ProjectProgressService(
            ProjectMapper projectMapper,
            UserMapper userMapper,
            ArtifactMapper artifactMapper,
            DesignReviewMapper designReviewMapper
    ) {
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.artifactMapper = artifactMapper;
        this.designReviewMapper = designReviewMapper;
    }

    public StudentProjectResponse getStudentProject(Long studentId) {
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getStudentId, studentId)
                .orderByAsc(Project::getId));
        if (projects.isEmpty()) {
            throw ApiException.notFound("未找到与当前账号关联的课题");
        }
        return toStudentProject(projects.get(0));
    }

    public AdvisorStudentsResponse getAdvisorStudents(Long advisorId) {
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getAdvisorId, advisorId)
                .orderByAsc(Project::getId));
        if (projects.isEmpty()) {
            return new AdvisorStudentsResponse(0, 0, 0, List.of());
        }

        Set<Long> studentIds = projects.stream()
                .map(Project::getStudentId)
                .collect(Collectors.toSet());
        Map<Long, User> students = userMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        List<Artifact> submittedArtifacts = artifactMapper.selectList(new LambdaQueryWrapper<Artifact>()
                .in(Artifact::getProjectId, projectIds)
                .eq(Artifact::getStatus, "SUBMITTED"));
        Map<Long, Long> pendingByProject = submittedArtifacts.stream()
                .collect(Collectors.groupingBy(Artifact::getProjectId, Collectors.counting()));

        List<AdvisorStudentView> views = new ArrayList<>();
        for (Project project : projects) {
            List<StepStatus> steps = buildSteps(project.getId());
            int completed = completedStepCount(steps);
            int current = currentStep(steps);
            boolean allCompleted = completed == steps.size();
            User student = students.get(project.getStudentId());
            String currentStage = allCompleted ? "全部完成" : steps.get(current - 1).title();
            String currentStatus = allCompleted ? STATUS_COMPLETED : steps.get(current - 1).status();

            views.add(new AdvisorStudentView(
                    project.getId(),
                    project.getStudentId(),
                    student == null ? "未知学生" : student.getRealName(),
                    student == null ? "" : student.getUsername(),
                    project.getTitle(),
                    completed,
                    current,
                    currentStage,
                    currentStatus,
                    pendingByProject.getOrDefault(project.getId(), 0L)
            ));
        }

        return new AdvisorStudentsResponse(
                views.size(),
                submittedArtifacts.size(),
                0,
                views
        );
    }

    public StudentProjectResponse getAdvisorStudentDetail(Long advisorId, Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ApiException.notFound("学生课题不存在");
        }
        if (!Objects.equals(project.getAdvisorId(), advisorId)) {
            throw ApiException.forbidden("无权查看该学生");
        }
        return toStudentProject(project);
    }

    private StudentProjectResponse toStudentProject(Project project) {
        User student = userMapper.selectById(project.getStudentId());
        User advisor = userMapper.selectById(project.getAdvisorId());
        if (student == null || advisor == null) {
            throw ApiException.notFound("课题关联的用户不存在");
        }

        List<StepStatus> steps = buildSteps(project.getId());
        int completed = completedStepCount(steps);
        int current = currentStep(steps);
        boolean allCompleted = completed == steps.size();

        return new StudentProjectResponse(
                project.getId(),
                project.getTitle(),
                UserView.from(student),
                UserView.from(advisor),
                completed,
                current,
                allCompleted,
                steps
        );
    }

    private List<StepStatus> buildSteps(Long projectId) {
        Artifact requirement = latestArtifact(projectId, TYPE_REQUIREMENT);
        Artifact prototype = latestArtifact(projectId, TYPE_PROTOTYPE);
        DesignReview review = latestReview(projectId);

        StepStatus requirementStep = artifactStep(
                TYPE_REQUIREMENT, "需求文档", requirement, "待提交（第 3 周开放）");
        StepStatus prototypeStep = artifactStep(
                TYPE_PROTOTYPE, "原型", prototype, "待提交（第 3 周开放）");

        List<StepStatus> steps = new ArrayList<>();
        steps.add(requirementStep);
        steps.add(prototypeStep);
        steps.add(reviewStep(review, requirementStep, prototypeStep));
        steps.add(new StepStatus("WEEKLY_REPORT", "周报", STATUS_NOT_STARTED, "第 4 周开放提交与批阅"));
        steps.add(new StepStatus("REPO_BINDING", "代码仓库", STATUS_NOT_STARTED, "第 4 周开放仓库绑定"));
        steps.add(new StepStatus("TEST_REPORT", "测试报告", STATUS_NOT_STARTED, "第 4–5 周开放"));
        return steps;
    }

    private StepStatus artifactStep(String key, String title, Artifact artifact, String emptyDescription) {
        if (artifact == null) {
            return new StepStatus(key, title, STATUS_NOT_STARTED, emptyDescription);
        }
        return switch (artifact.getStatus() == null ? "" : artifact.getStatus()) {
            case "APPROVED" -> new StepStatus(key, title, STATUS_COMPLETED, "已通过");
            case "RETURNED" -> new StepStatus(key, title, STATUS_RETURNED, "已退回，请修改后重新提交");
            case "SUBMITTED" -> new StepStatus(key, title, STATUS_IN_REVIEW, "已提交，等待导师审核");
            default -> new StepStatus(key, title, STATUS_NOT_STARTED, "状态未知");
        };
    }

    private StepStatus reviewStep(DesignReview review, StepStatus requirement, StepStatus prototype) {
        if (review != null) {
            String score = review.getScore() == null ? "" : "（" + review.getScore() + " 分）";
            if ("PASS".equals(review.getResult())) {
                return new StepStatus("DESIGN_REVIEW", "设计评审", STATUS_COMPLETED, "评审通过" + score);
            }
            return new StepStatus("DESIGN_REVIEW", "设计评审", STATUS_RETURNED, "评审退回，请修改后重新评审");
        }
        if (!STATUS_COMPLETED.equals(requirement.status()) || !STATUS_COMPLETED.equals(prototype.status())) {
            return new StepStatus("DESIGN_REVIEW", "设计评审", STATUS_NOT_STARTED, "需求与原型通过后可评审");
        }
        return new StepStatus("DESIGN_REVIEW", "设计评审", STATUS_NOT_STARTED, "等待导师评审");
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

    private DesignReview latestReview(Long projectId) {
        List<DesignReview> reviews = designReviewMapper.selectList(new LambdaQueryWrapper<DesignReview>()
                .eq(DesignReview::getProjectId, projectId)
                .orderByDesc(DesignReview::getReviewedAt)
                .orderByDesc(DesignReview::getId)
                .last("LIMIT 1"));
        return reviews.isEmpty() ? null : reviews.get(0);
    }

    private int completedStepCount(List<StepStatus> steps) {
        int count = 0;
        for (StepStatus step : steps) {
            if (!STATUS_COMPLETED.equals(step.status())) {
                break;
            }
            count++;
        }
        return count;
    }

    private int currentStep(List<StepStatus> steps) {
        for (int index = 0; index < steps.size(); index++) {
            if (!STATUS_COMPLETED.equals(steps.get(index).status())) {
                return index + 1;
            }
        }
        return steps.size();
    }
}
