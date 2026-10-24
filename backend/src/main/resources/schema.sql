CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    student_id BIGINT NOT NULL,
    advisor_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_student FOREIGN KEY (student_id) REFERENCES sys_user (id),
    CONSTRAINT fk_project_advisor FOREIGN KEY (advisor_id) REFERENCES sys_user (id)
);

CREATE TABLE IF NOT EXISTS artifact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    file_path VARCHAR(255),
    file_name VARCHAR(255),
    external_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    score INT,
    feedback VARCHAR(1000),
    test_case_count INT,
    test_pass_rate INT,
    defect_count INT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    CONSTRAINT fk_artifact_project FOREIGN KEY (project_id) REFERENCES project (id)
);

-- 兼容第 2 周已创建的文件库：补充材料原始文件名
ALTER TABLE artifact ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
-- 第 4 周：测试报告统计字段
ALTER TABLE artifact ADD COLUMN IF NOT EXISTS test_case_count INT;
ALTER TABLE artifact ADD COLUMN IF NOT EXISTS test_pass_rate INT;
ALTER TABLE artifact ADD COLUMN IF NOT EXISTS defect_count INT;

CREATE TABLE IF NOT EXISTS design_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    score INT NOT NULL,
    comment VARCHAR(1000),
    result VARCHAR(20) NOT NULL,
    round_no INT NOT NULL DEFAULT 1,
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_design_review_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_design_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (id)
);

CREATE TABLE IF NOT EXISTS weekly_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    week_no INT NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    comment VARCHAR(1000),
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    CONSTRAINT fk_weekly_report_project FOREIGN KEY (project_id) REFERENCES project (id)
);

-- 同一课题每周只保留一条周报，退回后原地修改并重新提交
CREATE UNIQUE INDEX IF NOT EXISTS uk_weekly_report_project_week
    ON weekly_report (project_id, week_no);

CREATE TABLE IF NOT EXISTS repo_binding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    repo_url VARCHAR(500) NOT NULL,
    commit_count INT NOT NULL DEFAULT 0,
    latest_commit_at TIMESTAMP,
    last_sync_at TIMESTAMP,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SYNCED',
    CONSTRAINT uk_repo_binding_project UNIQUE (project_id),
    CONSTRAINT fk_repo_binding_project FOREIGN KEY (project_id) REFERENCES project (id)
);
