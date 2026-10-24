MERGE INTO sys_user (id, username, password_hash, real_name, role) KEY (id) VALUES
(1, 'stu001', '$2b$10$TGJuZlTcc4.6SZP/6tevzepENGEd.fKQB4ePo8T0aykucgfpxUFdi', '张同学', 'STUDENT');

MERGE INTO sys_user (id, username, password_hash, real_name, role) KEY (id) VALUES
(2, 'stu002', '$2b$10$TGJuZlTcc4.6SZP/6tevzepENGEd.fKQB4ePo8T0aykucgfpxUFdi', '李同学', 'STUDENT');

MERGE INTO sys_user (id, username, password_hash, real_name, role) KEY (id) VALUES
(3, 'tea001', '$2b$10$TGJuZlTcc4.6SZP/6tevzepENGEd.fKQB4ePo8T0aykucgfpxUFdi', '王老师', 'ADVISOR');

MERGE INTO project (id, title, student_id, advisor_id) KEY (id) VALUES
(1, '基于 Spring Boot 的图书管理系统', 1, 3);

MERGE INTO project (id, title, student_id, advisor_id) KEY (id) VALUES
(2, '校园二手交易平台', 2, 3);
