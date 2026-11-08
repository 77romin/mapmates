USE mapmates;
-- Equality filters followed by the stable pagination order avoid a filesort.
ALTER TABLE attractions
  ADD INDEX idx_attractions_area_type_content (area_code, content_type_id, content_id),
  DROP INDEX idx_attractions_area_type;
-- Refresh estimates after the tourism dump is loaded, not before its INSERTs.
ANALYZE TABLE attractions;
INSERT INTO schema_migrations(version) VALUES ('107_catalog_paging_index');
