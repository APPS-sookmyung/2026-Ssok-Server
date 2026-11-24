-- MySQL 8.x. 백업 후 새 앱 시작 전에 실행. DDL 자동 커밋, 자동 실행되지 않음.
SET @last_login_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'last_login_at') = 0,
    'ALTER TABLE users ADD COLUMN last_login_at DATETIME(6) NULL',
    'SELECT ''last_login_at already exists'' AS migration_info'
);
PREPARE notification_migration FROM @last_login_sql;
EXECUTE notification_migration;
DEALLOCATE PREPARE notification_migration;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    actor_id BIGINT NULL,
    space_id BIGINT NULL,
    bookmark_id BIGINT NULL,
    invitation_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    CONSTRAINT uk_notification_event UNIQUE (recipient_id, event_key),
    INDEX idx_notification_recipient_read (recipient_id, read_at, created_at),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
);
