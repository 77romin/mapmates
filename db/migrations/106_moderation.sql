USE mapmates;
CREATE TABLE member_blocks (
 blocker_id BIGINT NOT NULL,blocked_id BIGINT NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(blocker_id,blocked_id),CHECK(blocker_id<>blocked_id),
 FOREIGN KEY(blocker_id) REFERENCES members(id) ON DELETE CASCADE,
 FOREIGN KEY(blocked_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE reports (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,reporter_id BIGINT NULL,
 target_type ENUM('member','post','comment','companion') NOT NULL,target_id BIGINT NOT NULL,
 target_label VARCHAR(200) NOT NULL,reason VARCHAR(500) NOT NULL,
 status ENUM('open','reviewed','dismissed') NOT NULL DEFAULT 'open',
 reviewer_id BIGINT NULL,review_note VARCHAR(500) NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,reviewed_at DATETIME NULL,
 UNIQUE KEY uk_report(reporter_id,target_type,target_id),INDEX idx_report_status(status,id),
 FOREIGN KEY(reporter_id) REFERENCES members(id) ON DELETE SET NULL,
 FOREIGN KEY(reviewer_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO schema_migrations(version) VALUES('106_moderation');
