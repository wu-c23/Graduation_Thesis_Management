-- MySQL 8.0 种子数据（密码均为 123456，BCrypt 存储）
USE thesis_platform;

INSERT INTO sys_user (id, username, password_hash, real_name, role) VALUES
(1, 'stu001', '$2b$10$TGJuZlTcc4.6SZP/6tevzepENGEd.fKQB4ePo8T0aykucgfpxUFdi', '张同学', 'STUDENT'),
(2, 'stu002', '$2b$10$TGJuZlTcc4.6SZP/6tevzepENGEd.fKQB4ePo8T0aykucgfpxUFdi', '李同学', 'STUDENT'),
(3, 'tea001', '$2b$10$TGJuZlTcc4.6SZP/6tevzepENGEd.fKQB4ePo8T0aykucgfpxUFdi', '王老师', 'ADVISOR')
ON DUPLICATE KEY UPDATE
    username = VALUES(username),
    password_hash = VALUES(password_hash),
    real_name = VALUES(real_name),
    role = VALUES(role);

INSERT INTO project (id, title, student_id, advisor_id) VALUES
(1, '基于 Spring Boot 的图书管理系统', 1, 3),
(2, '校园二手交易平台', 2, 3)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    student_id = VALUES(student_id),
    advisor_id = VALUES(advisor_id);
