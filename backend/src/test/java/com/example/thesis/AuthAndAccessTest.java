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
class AuthAndAccessTest {

    private static final String PASSWORD_HASH =
            "$2b$10$TGJuZlTcc4.6SZP/6tevzepENGEd.fKQB4ePo8T0aykucgfpxUFdi";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpExtraAdvisorData() {
        jdbcTemplate.update("DELETE FROM project WHERE id = 3");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = 4");
    }

    @Test
    void loginShouldReturnTokenAndSafeUserInfo() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("stu001", "123456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(28800))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.username").value("stu001"))
                .andExpect(jsonPath("$.data.user.realName").value("张同学"))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordShouldReturnGenericUnauthorizedMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("stu001", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/student/project"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void invalidTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/student/project")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("登录状态已失效，请重新登录"));
    }

    @Test
    void currentUserShouldComeFromValidatedToken() throws Exception {
        String token = login("tea001", "123456");
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("tea001"))
                .andExpect(jsonPath("$.data.realName").value("王老师"))
                .andExpect(jsonPath("$.data.role").value("ADVISOR"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void studentShouldSeeOwnProjectAndInitialStepState() throws Exception {
        String token = login("stu001", "123456");
        mockMvc.perform(get("/api/student/project")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(1))
                .andExpect(jsonPath("$.data.title").value("基于 Spring Boot 的图书管理系统"))
                .andExpect(jsonPath("$.data.student.username").value("stu001"))
                .andExpect(jsonPath("$.data.advisor.username").value("tea001"))
                .andExpect(jsonPath("$.data.completedStepCount").value(0))
                .andExpect(jsonPath("$.data.currentStep").value(1))
                .andExpect(jsonPath("$.data.allCompleted").value(false))
                .andExpect(jsonPath("$.data.steps.length()").value(6))
                .andExpect(jsonPath("$.data.steps[0].key").value("REQUIREMENT"))
                .andExpect(jsonPath("$.data.steps[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.steps[2].description").value("需求与原型通过后可评审"));
    }

    @Test
    void studentCannotAccessAdvisorApi() throws Exception {
        String token = login("stu001", "123456");
        mockMvc.perform(get("/api/advisor/students")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("当前账号无权访问导师接口"));
    }

    @Test
    void advisorCannotAccessStudentApi() throws Exception {
        String token = login("tea001", "123456");
        mockMvc.perform(get("/api/student/project")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("当前账号无权访问学生接口"));
    }

    @Test
    void advisorShouldSeeOnlyOwnStudents() throws Exception {
        String token = login("tea001", "123456");
        mockMvc.perform(get("/api/advisor/students")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(2))
                .andExpect(jsonPath("$.data.students.length()").value(2))
                .andExpect(jsonPath("$.data.students[0].studentName").value("张同学"))
                .andExpect(jsonPath("$.data.students[1].studentName").value("李同学"))
                .andExpect(jsonPath("$.data.students[0].pendingCount").value(0))
                .andExpect(jsonPath("$.data.pendingWeeklyReportCount").value(0));
    }

    @Test
    void advisorShouldNotSeeAnotherAdvisorsStudent() throws Exception {
        jdbcTemplate.update("""
                MERGE INTO sys_user (id, username, password_hash, real_name, role)
                KEY (id) VALUES (?, ?, ?, ?, ?)
                """, 4L, "tea002", PASSWORD_HASH, "赵老师", "ADVISOR");
        jdbcTemplate.update("""
                MERGE INTO project (id, title, student_id, advisor_id)
                KEY (id) VALUES (?, ?, ?, ?)
                """, 3L, "其他导师的课题", 1L, 4L);

        String token = login("tea001", "123456");
        mockMvc.perform(get("/api/advisor/students")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(2))
                .andExpect(jsonPath("$.data.students.length()").value(2));

        mockMvc.perform(get("/api/advisor/students/3")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权查看该学生"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.path("data").path("token").asText();
    }

    private String loginBody(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", password
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
