package com.example.thesis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeeklyRepoAndTestReportTest {

    private static final String PASSWORD_HASH =
            "$2b$10$TGJuZlTcc4.6SZP/6tevzepENGEd.fKQB4ePo8T0aykucgfpxUFdi";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @AfterEach
    void cleanUp() throws IOException {
        jdbcTemplate.update("DELETE FROM weekly_report");
        jdbcTemplate.update("DELETE FROM repo_binding");
        jdbcTemplate.update("DELETE FROM design_review");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM project WHERE id = 3");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = 4");
        deleteRecursively(Paths.get(uploadDir));
    }

    @Test
    void weeklyReportSubmitAndReviewCompletesStepFour() throws Exception {
        String studentToken = login("stu001", "123456");
        String advisorToken = login("tea001", "123456");

        long reportId = weeklyId(submitWeekly(studentToken, 1,
                "完成需求分析，梳理借阅流程。").andReturn());

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[3].key").value("WEEKLY_REPORT"))
                .andExpect(jsonPath("$.data.steps[3].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.weeklyReports.length()").value(1))
                .andExpect(jsonPath("$.data.weeklyReports[0].weekNo").value(1));

        mockMvc.perform(get("/api/advisor/students").header("Authorization", bearer(advisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingWeeklyReportCount").value(1))
                .andExpect(jsonPath("$.data.students[0].pendingCount").value(1));

        mockMvc.perform(post("/api/advisor/weekly-reports/" + reportId + "/review")
                        .header("Authorization", bearer(advisorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "REVIEWED", "comment", "进展清晰，继续保持"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.statusLabel").value("已批阅"))
                .andExpect(jsonPath("$.data.comment").value("进展清晰，继续保持"));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[3].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[3].description").value(containsString("1 篇")));

        mockMvc.perform(get("/api/advisor/weekly-reports")
                        .param("projectId", "1")
                        .header("Authorization", bearer(advisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("REVIEWED"));

        mockMvc.perform(get("/api/advisor/students").header("Authorization", bearer(advisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingWeeklyReportCount").value(0));
    }

    @Test
    void returnedWeeklyReportRequiresCommentAndCanBeResubmitted() throws Exception {
        String studentToken = login("stu001", "123456");
        String advisorToken = login("tea001", "123456");
        long reportId = weeklyId(submitWeekly(studentToken, 2,
                "完成原型初稿。").andReturn());

        mockMvc.perform(post("/api/advisor/weekly-reports/" + reportId + "/review")
                        .header("Authorization", bearer(advisorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "RETURNED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("退回周报时必须填写批阅意见"));

        mockMvc.perform(post("/api/advisor/weekly-reports/" + reportId + "/review")
                        .header("Authorization", bearer(advisorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "RETURNED", "comment", "补充测试计划"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[3].status").value("RETURNED"))
                .andExpect(jsonPath("$.data.weeklyReports[0].comment").value("补充测试计划"));

        submitWeekly(studentToken, 2, "完成原型初稿，并补充测试计划。")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.comment").value(nullValue()));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[3].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.weeklyReports.length()").value(1));

        submitWeekly(studentToken, 2, "重复提交")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("正在等待导师批阅")));
    }

    @Test
    void weeklyReportsAreIsolatedByAdvisor() throws Exception {
        jdbcTemplate.update("""
                MERGE INTO sys_user (id, username, password_hash, real_name, role)
                KEY (id) VALUES (?, ?, ?, ?, ?)
                """, 4L, "tea002", PASSWORD_HASH, "赵老师", "ADVISOR");
        jdbcTemplate.update("""
                MERGE INTO project (id, title, student_id, advisor_id)
                KEY (id) VALUES (?, ?, ?, ?)
                """, 3L, "其他导师的课题", 2L, 4L);
        jdbcTemplate.update("""
                MERGE INTO weekly_report (id, project_id, week_no, content, status, submitted_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 900L, 3L, 1, "其他学生的周报", "SUBMITTED");

        String advisorToken = login("tea001", "123456");
        mockMvc.perform(get("/api/advisor/weekly-reports")
                        .param("projectId", "3")
                        .header("Authorization", bearer(advisorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权查看该学生的周报"));

        mockMvc.perform(post("/api/advisor/weekly-reports/900/review")
                        .header("Authorization", bearer(advisorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "REVIEWED"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权批阅该周报"));
    }

    @Test
    void repoBindingAndManualCountCompleteStepFive() throws Exception {
        String studentToken = login("stu001", "123456");

        mockMvc.perform(post("/api/student/repo-binding")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("repoUrl", "https://gitlab.com/demo/repo"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅支持 GitHub 或 Gitee 公开仓库"));

        mockMvc.perform(post("/api/student/repo-binding")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("repoUrl",
                                "https://github.com/wu-c23/Graduation_Thesis_Management.git"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repoUrl")
                        .value("https://github.com/wu-c23/Graduation_Thesis_Management"))
                .andExpect(jsonPath("$.data.repoHost").value("GITHUB"))
                .andExpect(jsonPath("$.data.syncStatus").value("NOT_SYNCED"));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[4].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.repoBinding.repoHost").value("GITHUB"));

        Map<String, Object> manual = new LinkedHashMap<>();
        manual.put("commitCount", 42);
        manual.put("latestCommitAt", "2026-10-20T10:00:00");
        mockMvc.perform(post("/api/student/repo-binding/manual-count")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(manual)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commitCount").value(42))
                .andExpect(jsonPath("$.data.syncStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.latestCommitAt").value("2026-10-20T10:00:00"));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[4].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[4].description").value("已同步 42 次提交"))
                .andExpect(jsonPath("$.data.repoBinding.commitCount").value(42));
    }

    @Test
    void manualCountRequiresBindingAndValidRange() throws Exception {
        String studentToken = login("stu001", "123456");

        mockMvc.perform(post("/api/student/repo-binding/manual-count")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("commitCount", 10))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先绑定代码仓库"));

        mockMvc.perform(post("/api/student/repo-binding")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("repoUrl", "https://gitee.com/demo/thesis"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repoHost").value("GITEE"));

        mockMvc.perform(post("/api/student/repo-binding/manual-count")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("commitCount", -1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("提交数不能小于 0"));
    }

    @Test
    void testReportMetricsAndApprovalCompleteStepSix() throws Exception {
        String studentToken = login("stu001", "123456");
        String advisorToken = login("tea001", "123456");

        MockMultipartFile file = new MockMultipartFile(
                "file", "测试报告.pdf", MediaType.APPLICATION_PDF_VALUE,
                "test report".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/student/artifacts")
                        .file(file)
                        .param("type", "TEST_REPORT")
                        .param("testCaseCount", "50")
                        .param("testPassRate", "96")
                        .param("defectCount", "3")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.typeLabel").value("测试报告"))
                .andExpect(jsonPath("$.data.testCaseCount").value(50))
                .andExpect(jsonPath("$.data.testPassRate").value(96))
                .andExpect(jsonPath("$.data.defectCount").value(3))
                .andReturn();

        long artifactId = dataId(result);
        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[5].status").value("IN_REVIEW"));

        mockMvc.perform(post("/api/advisor/artifacts/" + artifactId + "/review")
                        .header("Authorization", bearer(advisorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "status", "APPROVED",
                                "feedback", "测试覆盖充分",
                                "score", 90
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[5].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.artifacts[0].testCaseCount").value(50));
    }

    @Test
    void testMetricsAreValidated() throws Exception {
        String studentToken = login("stu001", "123456");

        MockMultipartFile report = new MockMultipartFile(
                "file", "测试报告.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/student/artifacts")
                        .file(report)
                        .param("type", "TEST_REPORT")
                        .param("testPassRate", "101")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("通过率必须在 0–100 之间"));

        MockMultipartFile requirement = new MockMultipartFile(
                "file", "需求.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/student/artifacts")
                        .file(requirement)
                        .param("type", "REQUIREMENT")
                        .param("testCaseCount", "5")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只有测试报告可以填写测试统计"));
    }

    private org.springframework.test.web.servlet.ResultActions submitWeekly(
            String token, int weekNo, String content
    ) throws Exception {
        return mockMvc.perform(post("/api/student/weekly-reports")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("weekNo", weekNo, "content", content))));
    }

    private long weeklyId(MvcResult result) throws Exception {
        return dataId(result);
    }

    private long dataId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.path("data").path("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.path("data").path("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 测试清理失败不阻断后续用例。
                }
            });
        }
    }
}
