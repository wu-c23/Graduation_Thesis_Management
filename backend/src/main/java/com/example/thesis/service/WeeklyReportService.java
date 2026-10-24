package com.example.thesis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.thesis.common.ApiException;
import com.example.thesis.dto.WeeklyReportRequest;
import com.example.thesis.dto.WeeklyReportReviewRequest;
import com.example.thesis.dto.WeeklyReportView;
import com.example.thesis.entity.Project;
import com.example.thesis.entity.WeeklyReport;
import com.example.thesis.mapper.ProjectMapper;
import com.example.thesis.mapper.WeeklyReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 周报按“课题 + 周次”保存一条记录；退回后允许修改并重新提交。
 */
@Service
public class WeeklyReportService {

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_REVIEWED = "REVIEWED";
    public static final String STATUS_RETURNED = "RETURNED";

    private final WeeklyReportMapper weeklyReportMapper;
    private final ProjectMapper projectMapper;

    public WeeklyReportService(WeeklyReportMapper weeklyReportMapper, ProjectMapper projectMapper) {
        this.weeklyReportMapper = weeklyReportMapper;
        this.projectMapper = projectMapper;
    }

    public List<WeeklyReportView> listByProject(Long projectId) {
        return weeklyReportMapper.selectList(new LambdaQueryWrapper<WeeklyReport>()
                        .eq(WeeklyReport::getProjectId, projectId)
                        .orderByDesc(WeeklyReport::getWeekNo)
                        .orderByDesc(WeeklyReport::getId))
                .stream()
                .map(WeeklyReportView::from)
                .toList();
    }

    public List<WeeklyReportView> listForStudent(Long studentId) {
        return listByProject(requireStudentProject(studentId).getId());
    }

    public List<WeeklyReportView> listForAdvisor(Long advisorId, Long projectId) {
        requireAdvisorProject(advisorId, projectId);
        return listByProject(projectId);
    }

    @Transactional
    public WeeklyReportView submit(Long studentId, WeeklyReportRequest request) {
        Project project = requireStudentProject(studentId);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw ApiException.badRequest("周报内容不能为空");
        }

        WeeklyReport existing = findByProjectAndWeek(project.getId(), request.weekNo());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            WeeklyReport report = new WeeklyReport();
            report.setProjectId(project.getId());
            report.setWeekNo(request.weekNo());
            report.setContent(content);
            report.setStatus(STATUS_SUBMITTED);
            report.setSubmittedAt(now);
            weeklyReportMapper.insert(report);
            return WeeklyReportView.from(report);
        }

        if (STATUS_SUBMITTED.equals(existing.getStatus())) {
            throw ApiException.badRequest("第 " + request.weekNo() + " 周周报已提交，正在等待导师批阅");
        }
        if (STATUS_REVIEWED.equals(existing.getStatus())) {
            throw ApiException.badRequest("第 " + request.weekNo() + " 周周报已批阅，无需重复提交");
        }

        weeklyReportMapper.update(null, new LambdaUpdateWrapper<WeeklyReport>()
                .eq(WeeklyReport::getId, existing.getId())
                .set(WeeklyReport::getContent, content)
                .set(WeeklyReport::getStatus, STATUS_SUBMITTED)
                .set(WeeklyReport::getComment, null)
                .set(WeeklyReport::getSubmittedAt, now)
                .set(WeeklyReport::getReviewedAt, null));
        existing.setContent(content);
        existing.setStatus(STATUS_SUBMITTED);
        existing.setComment(null);
        existing.setSubmittedAt(now);
        existing.setReviewedAt(null);
        return WeeklyReportView.from(existing);
    }

    @Transactional
    public WeeklyReportView review(Long advisorId, Long reportId, WeeklyReportReviewRequest request) {
        WeeklyReport report = weeklyReportMapper.selectById(reportId);
        if (report == null) {
            throw ApiException.notFound("周报不存在");
        }
        Project project = projectMapper.selectById(report.getProjectId());
        if (project == null) {
            throw ApiException.notFound("周报关联的课题不存在");
        }
        if (!Objects.equals(project.getAdvisorId(), advisorId)) {
            throw ApiException.forbidden("无权批阅该周报");
        }
        if (!STATUS_SUBMITTED.equals(report.getStatus())) {
            throw ApiException.badRequest("该周报已批阅，请勿重复操作");
        }

        String status = request.status() == null
                ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!STATUS_REVIEWED.equals(status) && !STATUS_RETURNED.equals(status)) {
            throw ApiException.badRequest("批阅结论只能是 REVIEWED 或 RETURNED");
        }
        String comment = trimToNull(request.comment());
        if (STATUS_RETURNED.equals(status) && comment == null) {
            throw ApiException.badRequest("退回周报时必须填写批阅意见");
        }

        LocalDateTime now = LocalDateTime.now();
        weeklyReportMapper.update(null, new LambdaUpdateWrapper<WeeklyReport>()
                .eq(WeeklyReport::getId, report.getId())
                .set(WeeklyReport::getStatus, status)
                .set(WeeklyReport::getComment, comment)
                .set(WeeklyReport::getReviewedAt, now));
        report.setStatus(status);
        report.setComment(comment);
        report.setReviewedAt(now);
        return WeeklyReportView.from(report);
    }

    private WeeklyReport findByProjectAndWeek(Long projectId, Integer weekNo) {
        List<WeeklyReport> reports = weeklyReportMapper.selectList(new LambdaQueryWrapper<WeeklyReport>()
                .eq(WeeklyReport::getProjectId, projectId)
                .eq(WeeklyReport::getWeekNo, weekNo)
                .last("LIMIT 1"));
        return reports.isEmpty() ? null : reports.get(0);
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

    private Project requireAdvisorProject(Long advisorId, Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ApiException.notFound("学生课题不存在");
        }
        if (!Objects.equals(project.getAdvisorId(), advisorId)) {
            throw ApiException.forbidden("无权查看该学生的周报");
        }
        return project;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
