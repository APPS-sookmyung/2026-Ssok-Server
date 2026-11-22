-- MySQL 8.x: 백업 후 구버전 Spring의 쓰기를 중단한 상태에서 실행한다.
-- Flyway가 설치되지 않은 프로젝트이므로 자동 실행되지 않는다.
-- DDL은 자동 커밋된다. 새 앱의 Hibernate ddl-auto=update보다 먼저 적용해야 한다.
-- MEMBER 값은 역할에만 해당한다. space_members 테이블·memberId 이름은 유지한다.
ALTER TABLE space_members
    MODIFY COLUMN role ENUM('OWNER', 'MEMBER', 'EDITOR', 'VIEWER') NOT NULL;

UPDATE space_members SET role = 'EDITOR' WHERE role = 'MEMBER';

ALTER TABLE space_members
    MODIFY COLUMN role ENUM('OWNER', 'EDITOR', 'VIEWER') NOT NULL;

SELECT role, COUNT(*) AS member_count FROM space_members GROUP BY role;
