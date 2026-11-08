USE mapmates;
ALTER TABLE plans ADD version BIGINT NOT NULL DEFAULT 0;
INSERT INTO schema_migrations(version) VALUES('104_plan_version');
