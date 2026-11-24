-- MySQL 8.x. 적용 전 백업 및 기존 스키마 확인. DDL은 자동 커밋된다.
-- 기존 members를 변경하지 않고 초대 테이블만 추가한다.
CREATE TABLE IF NOT EXISTS space_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    space_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    role ENUM('OWNER','EDITOR','VIEWER') NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status ENUM('PENDING','ACCEPTED','CANCELLED') NOT NULL,
    delivery ENUM('PENDING','SENT','FAILED') NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_invitation_token_hash UNIQUE (token_hash),
    INDEX idx_invitation_space_email (space_id, email),
    CONSTRAINT fk_invitation_space FOREIGN KEY (space_id) REFERENCES spaces(id)
);
