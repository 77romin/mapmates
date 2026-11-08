SET NAMES utf8mb4;
USE mapmates;
CREATE TABLE notifications (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id BIGINT NOT NULL,
 kind VARCHAR(40) NOT NULL, title VARCHAR(200) NOT NULL, link VARCHAR(255) NOT NULL,
 read_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_notification_member(member_id,read_at,id),
 FOREIGN KEY(member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO schema_migrations(version) VALUES('102_notifications');
