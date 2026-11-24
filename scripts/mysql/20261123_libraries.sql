-- MySQL 8.x. 백업 후 새 앱 시작 전에 적용. 기존 북마크는 미분류(NULL)로 유지한다.
-- DDL은 자동 커밋되며 자동 실행되지 않는다. 동일 스크립트 재실행 가능.
CREATE TABLE IF NOT EXISTS libraries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    space_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    name_key VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_library_space_name UNIQUE (space_id, name_key),
    CONSTRAINT fk_library_space FOREIGN KEY (space_id) REFERENCES spaces(id)
);

SET @library_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookmarks' AND column_name = 'library_id') = 0,
    'ALTER TABLE bookmarks ADD COLUMN library_id BIGINT NULL',
    'SELECT ''library_id already exists'' AS migration_info'
);
PREPARE library_migration FROM @library_column_sql;
EXECUTE library_migration;
DEALLOCATE PREPARE library_migration;

SET @library_fk_sql = IF(
    (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND table_name = 'bookmarks' AND column_name = 'library_id' AND referenced_table_name = 'libraries') = 0,
    'ALTER TABLE bookmarks ADD CONSTRAINT fk_bookmark_library FOREIGN KEY (library_id) REFERENCES libraries(id) ON DELETE SET NULL',
    'SELECT ''library foreign key already exists'' AS migration_info'
);
PREPARE library_migration FROM @library_fk_sql;
EXECUTE library_migration;
DEALLOCATE PREPARE library_migration;
