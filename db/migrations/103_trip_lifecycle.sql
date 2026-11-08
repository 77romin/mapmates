SET NAMES utf8mb4;
USE mapmates;
ALTER TABLE companions ADD lifecycle ENUM('scheduled','completed','cancelled') NOT NULL DEFAULT 'scheduled';
CREATE TABLE plan_revisions (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,plan_id BIGINT NOT NULL,actor_id BIGINT NULL,
 summary VARCHAR(500) NOT NULL,snapshot JSON NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_revision_plan(plan_id,id), FOREIGN KEY(plan_id) REFERENCES plans(id) ON DELETE CASCADE,
 FOREIGN KEY(actor_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO schema_migrations(version) VALUES('103_trip_lifecycle');
