-- [E10 대안 B] SPATIAL 인덱스와 B-tree 복합 인덱스 비교. 20_tests.sql 다음에 실행한다.
-- 검증 스키마에 location 컬럼을 추가하므로 정식 스키마에는 E10-B를 고른 경우에만 반영한다.
USE mapmates_erd_check;

SELECT 'E10-A B-tree (latitude, longitude)' AS plan;
EXPLAIN SELECT content_id, title FROM attractions
WHERE latitude BETWEEN 33.40 AND 33.50 AND longitude BETWEEN 126.50 AND 126.60;
SELECT COUNT(*) AS rows_in_box FROM attractions
WHERE latitude BETWEEN 33.40 AND 33.50 AND longitude BETWEEN 126.50 AND 126.60;

-- 축 순서 주의: POINT(x, y)는 (경도, 위도), SRID 4326의 WKT 문자열은 (위도 경도) 순서다.
ALTER TABLE attractions ADD COLUMN location POINT SRID 4326 NULL;
UPDATE attractions SET location = ST_SRID(POINT(longitude, latitude), 4326) WHERE latitude IS NOT NULL;
-- SPATIAL INDEX는 NOT NULL 컬럼에만 만들 수 있어 좌표 없는 행은 (0, 0)으로 채운다.
UPDATE attractions SET location = ST_SRID(POINT(0, 0), 4326) WHERE location IS NULL;
ALTER TABLE attractions MODIFY location POINT SRID 4326 NOT NULL, ADD SPATIAL INDEX sp_attractions_location (location);
ANALYZE TABLE attractions;

SELECT 'E10-B SPATIAL INDEX + MBRContains' AS plan;
EXPLAIN SELECT content_id, title FROM attractions
WHERE MBRContains(ST_GeomFromText('POLYGON((33.40 126.50, 33.50 126.50, 33.50 126.60, 33.40 126.60, 33.40 126.50))', 4326), location);
SELECT COUNT(*) AS rows_in_box_spatial FROM attractions
WHERE MBRContains(ST_GeomFromText('POLYGON((33.40 126.50, 33.50 126.50, 33.50 126.60, 33.40 126.60, 33.40 126.50))', 4326), location);

SELECT 'spatial compare done' AS step;
