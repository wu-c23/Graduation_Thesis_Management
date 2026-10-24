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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArtifactAndReviewTest {

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
        jdbcTemplate.update("DELETE FROM design_review");
        jdbcTemplate.update("DELETE FROM artifact");
        jdbcTemplate.update("DELETE FROM project WHERE id = 3");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = 4");
        deleteRecursively(Paths.get(uploadDir));
    }

    @Test
    void studentCanSubmitRequirementFileAndSeeHistory() throws Exception {
        String token = login("stu001", "123456");
        upload(token, "REQUIREMENT", "需求说明书.pdf", "requirement v1".getBytes(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.data.type").value("REQUIREMENT"))
                .andExpect(jsonPath("$.data.typeLabel").value("需求文档"))
                .andExpect(jsonPath("$.data.fileName").value("需求说明书.pdf"))
                .andExpect(jsonPath("$.data.filePath").doesNotExist())
                .andExpect(jsonPath("$.data.downloadable").value(true))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.steps[0].description").value("已提交，等待导师审核"))
                .andExpect(jsonPath("$.data.completedStepCount").value(0))
                .andExpect(jsonPath("$.data.artifacts.length()").value(1));

        mockMvc.perform(get("/api/student/artifacts").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fileName").value("需求说明书.pdf"));
    }

    @Test
    void studentCanSubmitPrototypeByExternalLink() throws Exception {
        String token = login("stu001", "123456");
        mockMvc.perform(multipart("/api/student/artifacts")
                        .param("type", "PROTOTYPE")
                        .param("externalUrl", "https://www.figma.com/file/demo")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("PROTOTYPE"))
                .andExpect(jsonPath("$.data.typeLabel").value("原型"))
                .andExpect(jsonPath("$.data.externalUrl").value("https://www.figma.com/file/demo"))
                .andExpect(jsonPath("$.data.downloadable").value(false))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    void invalidFileTypeShouldBeRejected() throws Exception {
        String token = login("stu001", "123456");
        upload(token, "REQUIREMENT", "notes.txt", "hello".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("只支持 PDF、DOCX、ZIP、PNG、JPG 文件"));
    }

    @Test
    void oversizedFileShouldBeRejected() throws Exception {
        String token = login("stu001", "123456");
        upload(token, "REQUIREMENT", "large.pdf", new byte[2048])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("文件大小超过限制"));
    }

    @Test
    void submissionRequiresExactlyOneSource() throws Exception {
        String token = login("stu001", "123456");

        mockMvc.perform(multipart("/api/student/artifacts")
                        .param("type", "REQUIREMENT")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请上传文件或填写外部链接"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "requirement.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/student/artifacts")
                        .file(file)
                        .param("type", "REQUIREMENT")
                        .param("externalUrl", "https://example.com/doc")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("文件与外部链接只能选择一种方式"));
    }

    @Test
    void invalidExternalUrlShouldBeRejected() throws Exception {
        String token = login("stu001", "123456");
        mockMvc.perform(multipart("/api/student/artifacts")
                        .param("type", "PROTOTYPE")
                        .param("externalUrl", "javascript:alert(1)")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("外部链接必须是 http/https 地址"));

        mockMvc.perform(multipart("/api/student/artifacts")
                        .param("type", "PROTOTYPE")
                        .param("externalUrl", "ftp://example.com/file")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("外部链接必须是 http/https 地址"));
    }

    @Test
    void duplicatePendingSubmissionShouldBeRejected() throws Exception {
        String token = login("stu001", "123456");
        upload(token, "REQUIREMENT", "requirement.pdf", new byte[]{1, 2, 3});

        upload(token, "REQUIREMENT", "requirement-v2.pdf", new byte[]{4, 5, 6})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该材料正在等待导师审核，请勿重复提交"));
    }

    @Test
    void advisorCanReturnAndStudentCanResubmit() throws Exception {
        String studentToken = login("stu001", "123456");
        String advisorToken = login("tea001", "123456");
        long artifactId = artifactId(upload(studentToken, "REQUIREMENT", "requirement.pdf",
                new byte[]{1, 2, 3}).andReturn());

        review(advisorToken, artifactId, "RETURNED", "请补充需求用例", 60)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.feedback").value("请补充需求用例"));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].status").value("RETURNED"))
                .andExpect(jsonPath("$.data.steps[0].description").value("已退回，请修改后重新提交"));

        upload(studentToken, "REQUIREMENT", "requirement-v2.pdf", new byte[]{4, 5, 6})
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/advisor/students").header("Authorization", bearer(advisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students[0].pendingCount").value(1));
    }

    @Test
    void advisorCanApproveAndResubmissionResetsStep() throws Exception {
        String studentToken = login("stu001", "123456");
        String advisorToken = login("tea001", "123456");
        long artifactId = artifactId(upload(studentToken, "REQUIREMENT", "requirement.pdf",
                new byte[]{1, 2, 3}).andReturn());

        review(advisorToken, artifactId, "APPROVED", "符合要求", 90)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedStepCount").value(1));

        // 通过后仍允许重新提交，旧记录保留，步骤回到审核中。
        upload(studentToken, "REQUIREMENT", "requirement-v2.pdf", new byte[]{4, 5, 6})
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedStepCount").value(0))
                .andExpect(jsonPath("$.data.steps[0].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.artifacts.length()").value(2));
    }

    @Test
    void designReviewRequiresApprovedMaterials() throws Exception {
        String studentToken = login("stu001", "123456");
        String advisorToken = login("tea001", "123456");
        long requirementId = artifactId(upload(studentToken, "REQUIREMENT", "requirement.pdf",
                new byte[]{1}).andReturn());
        review(advisorToken, requirementId, "APPROVED", "通过", 88);

        upload(studentToken, "PROTOTYPE", "prototype.png", new byte[]{2});

        designReview(advisorToken, 1L, 90, "PASS", "设计合理")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("需求文档与原型均通过后才能进行设计评审"));
    }

    @Test
    void designReviewCompletesFirstThreeSteps() throws Exception {
        String studentToken = login("stu001", "123456");
        String advisorToken = login("tea001", "123456");

        long requirementId = artifactId(upload(studentToken, "REQUIREMENT", "requirement.pdf",
                new byte[]{1}).andReturn());
        review(advisorToken, requirementId, "APPROVED", "需求通过", 90);

        long prototypeId = artifactId(upload(studentToken, "PROTOTYPE", "prototype.png",
                new byte[]{2}).andReturn());
        review(advisorToken, prototypeId, "APPROVED", "原型通过", 92);

        designReview(advisorToken, 1L, 95, "PASS", "设计合理，可以进入开发")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(95))
                .andExpect(jsonPath("$.data.result").value("PASS"))
                .andExpect(jsonPath("$.data.roundNo").value(1))
                .andExpect(jsonPath("$.data.reviewerName").value("王老师"));

        mockMvc.perform(get("/api/student/project").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedStepCount").value(3))
                .andExpect(jsonPath("$.data.steps[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[2].description").value(containsString("95")))
                .andExpect(jsonPath("$.data.designReviews.length()").value(1));
    }

    @Test
    void invalidDesignReviewShouldBeRejected() throws Exception {
        String studentToken = login("stu001", "123456");
        String advisorToken = login("tea001", "123456");
        long requirementId = artifactId(upload(studentToken, "REQUIREMENT", "requirement.pdf",
                new byte[]{1}).andReturn());
        review(advisorToken, requirementId, "APPROVED", "通过", 88);
        long prototypeId = artifactId(upload(studentToken, "PROTOTYPE", "prototype.png",
                new byte[]{2}).andReturn());
        review(advisorToken, prototypeId, "APPROVED", "通过", 88);

        designReview(advisorToken, 1L, 101, "PASS", "分数越界")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("评分不能大于 100"));

        designReview(advisorToken, 1L, 80, "UNKNOWN", "结论非法")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("评审结论只能是 PASS 或 RETURN"));
    }

    @Test
    void studentCannotDownloadAnotherStudentsFile() throws Exception {
        String ownerToken = login("stu001", "123456");
        long artifactId = artifactId(upload(ownerToken, "REQUIREMENT", "requirement.pdf",
                new byte[]{1, 2, 3}).andReturn());

        String otherToken = login("stu002", "123456");
        mockMvc.perform(get("/api/files/" + artifactId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权下载该材料"));
    }

    @Test
    void advisorCanDownloadOwnStudentFileButNotExternalLink() throws Exception {
        String studentToken = login("stu001", "123456");
        String advisorToken = login("tea001", "123456");
        byte[] content = "file-content".getBytes(StandardCharsets.UTF_8);
        long fileArtifactId = artifactId(upload(studentToken, "REQUIREMENT", "requirement.pdf",
                content).andReturn());

        mockMvc.perform(get("/api/files/" + fileArtifactId)
                        .header("Authorization", bearer(advisorToken)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));

        MvcResult linkResult = mockMvc.perform(multipart("/api/student/artifacts")
                        .param("type", "PROTOTYPE")
                        .param("externalUrl", "https://www.figma.com/file/demo")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        long linkArtifactId = artifactId(linkResult);

        mockMvc.perform(get("/api/files/" + linkArtifactId)
                        .header("Authorization", bearer(advisorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该材料为外部链接，无文件可下载"));
    }

    @Test
    void advisorCannotReviewAnotherAdvisorsProject() throws Exception {
        jdbcTemplate.update("""
                MERGE INTO sys_user (id, username, password_hash, real_name, role)
                KEY (id) VALUES (?, ?, ?, ?, ?)
                """, 4L, "tea002", PASSWORD_HASH, "赵老师", "ADVISOR");
        jdbcTemplate.update("""
                MERGE INTO project (id, title, student_id, advisor_id)
                KEY (id) VALUES (?, ?, ?, ?)
                """, 3L, "其他导师的课题", 2L, 4L);
        jdbcTemplate.update("""
                MERGE INTO artifact (id, project_id, type, status, submitted_at)
                KEY (id) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 900L, 3L, "PROTOTYPE", "SUBMITTED");

        String advisorToken = login("tea001", "123456");

        review(advisorToken, 900L, "APPROVED", "越权审核", 80)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权审核该材料"));

        designReview(advisorToken, 3L, 80, "PASS", "越权评审")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权评审该学生"));
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            String token, String type, String fileName, byte[] content
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
        return mockMvc.perform(multipart("/api/student/artifacts")
                .file(file)
                .param("type", type)
                .header("Authorization", bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions review(
            String token, long artifactId, String status, String feedback, Integer score
    ) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("feedback", feedback);
        if (score != null) {
            body.put("score", score);
        }
        return mockMvc.perform(post("/api/advisor/artifacts/" + artifactId + "/review")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.ResultActions designReview(
            String token, long projectId, int score, String result, String comment
    ) throws Exception {
        return mockMvc.perform(post("/api/advisor/projects/" + projectId + "/design-review")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "score", score,
                        "result", result,
                        "comment", comment
                ))));
    }

    private long artifactId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.path("data").path("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return body.path("data").path("token").asText();
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
