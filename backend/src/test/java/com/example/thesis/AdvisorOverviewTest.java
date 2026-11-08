package com.example.thesis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdvisorOverviewTest {

    private static final String PASSWORD_HASH =
            "$2b$10$TGJuZlTcc4.6SZP/6tevzepENGEd.fKQB4ePo8T0aykucgfpxUFdi";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM weekly_report");
        jdbcTemplate.update("DELETE FROM repo_binding");
        jdbcTemplate.update("DELETE FROM design_review");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM project WHERE id = 3");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (4, 5)");
    }

    @Test
    void overviewAggregatesProgressCompletionRateAndPendingItems() throws Exception {
        seedCompletedProject(1L);
        jdbcTemplate.update("""
                MERGE INTO artifact (id, project_id, type, file_name, status, submitted_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 801L, 2L, "REQUIREMENT", "需求文档.pdf", "SUBMITTED");
        jdbcTemplate.update("""
                MERGE INTO weekly_report (id, project_id, week_no, content, status, submitted_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 901L, 2L, 1, "本周完成需求整理。", "SUBMITTED");

        String token = login("tea001", "123456");
        mockMvc.perform(get("/api/advisor/students").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(2))
                .andExpect(jsonPath("$.data.pendingArtifactCount").value(1))
                .andExpect(jsonPath("$.data.pendingWeeklyReportCount").value(1))
                .andExpect(jsonPath("$.data.completedStudentCount").value(1))
                .andExpect(jsonPath("$.data.pendingStudentCount").value(1))
                .andExpect(jsonPath("$.data.averageCompletionRate").value(50))
                .andExpect(jsonPath("$.data.students[0].completedStepCount").value(6))
                .andExpect(jsonPath("$.data.students[0].steps.length()").value(6))
                .andExpect(jsonPath("$.data.students[0].steps[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.students[0].steps[5].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.students[0].lastActiveAt").isNotEmpty())
                .andExpect(jsonPath("$.data.students[1].completedStepCount").value(0))
                .andExpect(jsonPath("$.data.students[1].pendingCount").value(2))
                .andExpect(jsonPath("$.data.students[1].steps[0].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.students[1].lastActiveAt").isNotEmpty());
    }

    @Test
    void overviewOnlyCountsStudentsOfCurrentAdvisor() throws Exception {
        jdbcTemplate.update("""
                MERGE INTO sys_user (id, username, password_hash, real_name, role)
                KEY (id) VALUES (?, ?, ?, ?, ?)
                """, 4L, "tea002", PASSWORD_HASH, "赵老师", "ADVISOR");
        jdbcTemplate.update("""
                MERGE INTO project (id, title, student_id, advisor_id)
                KEY (id) VALUES (?, ?, ?, ?)
                """, 3L, "其他导师的课题", 2L, 4L);
        jdbcTemplate.update("""
                MERGE INTO artifact (id, project_id, type, file_name, status, submitted_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 802L, 3L, "REQUIREMENT", "他人需求.pdf", "SUBMITTED");

        String advisorToken = login("tea001", "123456");
        mockMvc.perform(get("/api/advisor/students").header("Authorization", bearer(advisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(2))
                .andExpect(jsonPath("$.data.pendingArtifactCount").value(0))
                .andExpect(jsonPath("$.data.students.length()").value(2));

        String otherToken = login("tea002", "123456");
        mockMvc.perform(get("/api/advisor/students").header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(1))
                .andExpect(jsonPath("$.data.pendingArtifactCount").value(1))
                .andExpect(jsonPath("$.data.students[0].studentId").value(2));
    }

    @Test
    void advisorWithoutStudentsGetsEmptyOverview() throws Exception {
        jdbcTemplate.update("""
                MERGE INTO sys_user (id, username, password_hash, real_name, role)
                KEY (id) VALUES (?, ?, ?, ?, ?)
                """, 5L, "tea003", PASSWORD_HASH, "钱老师", "ADVISOR");

        String token = login("tea003", "123456");
        mockMvc.perform(get("/api/advisor/students").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(0))
                .andExpect(jsonPath("$.data.pendingArtifactCount").value(0))
                .andExpect(jsonPath("$.data.completedStudentCount").value(0))
                .andExpect(jsonPath("$.data.pendingStudentCount").value(0))
                .andExpect(jsonPath("$.data.averageCompletionRate").value(0))
                .andExpect(jsonPath("$.data.students.length()").value(0));
    }

    private void seedCompletedProject(Long projectId) {
        jdbcTemplate.update("""
                MERGE INTO artifact (id, project_id, type, file_name, status, submitted_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 810L, projectId, "REQUIREMENT", "需求文档.pdf", "APPROVED");
        jdbcTemplate.update("""
                MERGE INTO artifact (id, project_id, type, file_name, status, submitted_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 811L, projectId, "PROTOTYPE", "原型.png", "APPROVED");
        jdbcTemplate.update("""
                MERGE INTO artifact (id, project_id, type, file_name, status, submitted_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 812L, projectId, "TEST_REPORT", "测试报告.pdf", "APPROVED");
        jdbcTemplate.update("""
                MERGE INTO design_review (id, project_id, reviewer_id, score, comment, result, round_no, reviewed_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 820L, projectId, 3L, 92, "设计合理", "PASS", 1);
        jdbcTemplate.update("""
                MERGE INTO weekly_report (id, project_id, week_no, content, status, submitted_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 900L, projectId, 1, "本周完成需求与原型。", "REVIEWED");
        jdbcTemplate.update("""
                MERGE INTO repo_binding (id, project_id, repo_url, commit_count, last_sync_at, sync_status)
                KEY (id) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, 910L, projectId,
                "https://github.com/wu-c23/Graduation_Thesis_Management", 4, "SUCCESS");
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
