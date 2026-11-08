USE mapmates;
ALTER TABLE members ADD auth_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE password_reset_tokens (
 token_hash CHAR(64) PRIMARY KEY,member_id BIGINT NOT NULL,
 expires_at DATETIME NOT NULL,used_at DATETIME NULL,
 delivery_status ENUM('pending','sent','failed') NOT NULL DEFAULT 'pending',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_reset_member(member_id), FOREIGN KEY(member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO schema_migrations(version) VALUES('105_secure_recovery');
