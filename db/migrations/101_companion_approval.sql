SET NAMES utf8mb4;
USE mapmates;
CREATE TABLE IF NOT EXISTS schema_migrations(version VARCHAR(100) PRIMARY KEY, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE companions ADD join_policy ENUM('instant','approval') NOT NULL DEFAULT 'instant';
CREATE TABLE companion_requests (
 companion_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 status ENUM('pending','approved','rejected','cancelled') NOT NULL DEFAULT 'pending',
 introduction VARCHAR(500) NOT NULL DEFAULT '',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(companion_id,member_id), INDEX idx_requests_member(member_id,status),
 FOREIGN KEY(companion_id) REFERENCES companions(id) ON DELETE CASCADE,
 FOREIGN KEY(member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO schema_migrations(version) VALUES('101_companion_approval');
