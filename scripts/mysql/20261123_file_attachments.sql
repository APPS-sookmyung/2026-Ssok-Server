-- MySQL 8.x. 백업 후 새 앱 시작 전에 적용한다. DDL은 자동 커밋된다.
-- 기존 링크는 LINK, 기존 분석 상태는 그대로 유지한다. 자동 실행되지 않는다.
SET @file_type_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookmarks' AND column_name = 'type') = 0,
    'ALTER TABLE bookmarks ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT ''LINK''',
    'SELECT ''bookmark type already exists'' AS migration_info'
);
PREPARE file_migration FROM @file_type_sql;
EXECUTE file_migration;
DEALLOCATE PREPARE file_migration;

ALTER TABLE bookmarks MODIFY COLUMN analysis_status ENUM('PENDING','SUCCESS','FAILED','NOT_SUPPORTED') NOT NULL;

CREATE TABLE IF NOT EXISTS stored_files (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    space_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    bookmark_id BIGINT NULL,
    purpose ENUM('BOOKMARK','CHAT_ATTACHMENT') NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_id VARCHAR(1024) NOT NULL,
    object_key VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_file_object_key UNIQUE (object_key),
    CONSTRAINT uk_file_bookmark UNIQUE (bookmark_id),
    INDEX idx_file_space (space_id),
    CONSTRAINT fk_file_space FOREIGN KEY (space_id) REFERENCES spaces(id),
    CONSTRAINT fk_file_bookmark FOREIGN KEY (bookmark_id) REFERENCES bookmarks(id)
);

CREATE TABLE IF NOT EXISTS file_deletion_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    storage_id VARCHAR(1024) NOT NULL,
    object_key VARCHAR(36) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    INDEX idx_file_job_due (next_attempt_at)
);
