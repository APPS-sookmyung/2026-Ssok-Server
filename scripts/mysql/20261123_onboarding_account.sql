-- MySQL 8.x. 백업 후 새 앱 시작 전에 적용. DDL 자동 커밋, 자동 실행되지 않는다.
-- 기존 회원은 설문 미완료(NULL)이며 기존 세션 없는 JWT는 새 버전에서 재로그인이 필요하다.
SET @onboarding_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='onboarding_completed_at')=0,
    'ALTER TABLE users ADD COLUMN onboarding_completed_at DATETIME(6) NULL',
    'SELECT ''onboarding column already exists'' AS migration_info'
);
PREPARE account_migration FROM @onboarding_sql;
EXECUTE account_migration;
DEALLOCATE PREPARE account_migration;
ALTER TABLE users MODIFY COLUMN profile_image VARCHAR(1000) NULL;
ALTER TABLE stored_files MODIFY COLUMN uploader_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS user_onboarding_purposes (
    user_id BIGINT NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    CONSTRAINT uk_user_onboarding_purpose UNIQUE(user_id,purpose),
    CONSTRAINT fk_onboarding_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    refresh_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    INDEX idx_auth_session_user(user_id),
    CONSTRAINT fk_auth_session_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS account_vector_deletion_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    space_id BIGINT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_account_vector_space UNIQUE(space_id)
);
