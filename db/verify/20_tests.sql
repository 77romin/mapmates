-- ERD 추천안 검증 시나리오. 결과의 result 열이 모두 PASS면 통과.
USE mapmates_erd_check;

DROP PROCEDURE IF EXISTS expect_error;
DROP PROCEDURE IF EXISTS expect_true;
DELIMITER //
-- 막혀야 하는 문장을 실행하고, 오류가 나면 PASS
CREATE PROCEDURE expect_error(IN label VARCHAR(120), IN stmt TEXT)
BEGIN
  DECLARE failed INT DEFAULT 0;
  DECLARE msg TEXT DEFAULT NULL;
  DECLARE code INT DEFAULT NULL;
  DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
  BEGIN
    GET DIAGNOSTICS CONDITION 1 msg = MESSAGE_TEXT, code = MYSQL_ERRNO;
    SET failed = 1;
  END;
  SET @stmt = stmt;
  PREPARE s FROM @stmt;
  EXECUTE s;
  DEALLOCATE PREPARE s;
  SELECT label AS test, IF(failed = 1, 'PASS', 'FAIL') AS result, CONCAT(IFNULL(code, ''), ' ', IFNULL(msg, '오류 없이 실행됨')) AS detail;
END//
-- 조건식이 참이면 PASS
CREATE PROCEDURE expect_true(IN label VARCHAR(120), IN ok BOOLEAN, IN detail TEXT)
BEGIN
  SELECT label AS test, IF(ok, 'PASS', 'FAIL') AS result, detail;
END//
DELIMITER ;

-- ===== T01. CHECK 제약의 한계: SET NULL 동작이 걸린 FK 컬럼에는 CHECK를 걸 수 없다 =====
-- (CASCADE 컬럼인 post_id·companion_id에는 10_schema.sql에서 CHECK가 정상 생성됨)
CALL expect_error('T01 E5: SET NULL FK 컬럼(parent_id)에 CHECK 추가는 거부된다',
  'ALTER TABLE comments ADD CONSTRAINT chk_parent CHECK (parent_id IS NULL OR parent_id > 0)');

-- ===== 기본 데이터 =====
INSERT INTO members (email, password_hash, name, nickname, birth_date, gender) VALUES
  ('a@test.com', 'x', '김하늘', '노을수집가', '1990-05-15', 'male'),
  ('b@test.com', 'x', '이바다', '파도타기', '1995-03-02', 'female'),
  ('c@test.com', 'x', '박숲', '숲길산책', '1999-11-20', 'female');
SET @m1 = (SELECT id FROM members WHERE email = 'a@test.com');
SET @m2 = (SELECT id FROM members WHERE email = 'b@test.com');
SET @m3 = (SELECT id FROM members WHERE email = 'c@test.com');
SET @a1 = (SELECT content_id FROM attractions ORDER BY no LIMIT 1);
SET @a2 = (SELECT content_id FROM attractions ORDER BY no LIMIT 1 OFFSET 1);

-- ===== T02~T04. E2: 덤프에 없는 장소 추가(upsert) =====
INSERT INTO attractions (content_id, title, content_type_id, area_code, si_gun_gu_code, latitude, longitude, first_image1)
VALUES (99999001, '테스트 신규 장소', 12, 39, 4, 33.4500000, 126.5700000, 'https://example.com/a.jpg')
ON DUPLICATE KEY UPDATE title = VALUES(title);
INSERT INTO attractions (content_id, title, content_type_id, area_code, si_gun_gu_code, latitude, longitude)
VALUES (99999001, '테스트 신규 장소(수정)', 12, 39, 4, 33.45, 126.57)
ON DUPLICATE KEY UPDATE title = VALUES(title);
CALL expect_true('T02 E2: 같은 content_id 두 번 upsert해도 한 행', (SELECT COUNT(*) FROM attractions WHERE content_id = 99999001) = 1,
  (SELECT title FROM attractions WHERE content_id = 99999001));
INSERT INTO attractions (content_id, title, content_type_id, area_code, si_gun_gu_code, latitude, longitude)
VALUES (99999002, '지역코드 없는 장소', 12, NULL, NULL, 37.5, 127.0);
CALL expect_true('T03 E2: 지역코드 없이(NULL) 추가 가능', (SELECT COUNT(*) FROM attractions WHERE content_id = 99999002) = 1, 'TourAPI 응답에 지역코드가 없을 때 대비');
CALL expect_error('T04 발견: 시도에 없는 구군 조합은 복합 FK가 막는다',
  'INSERT INTO attractions (content_id, title, content_type_id, area_code, si_gun_gu_code) VALUES (99999003, ''잘못된 구군'', 12, 1, 99)');

-- ===== T05~T10. E3: 여행계획 제약 =====
INSERT INTO plans (owner_id, title, start_date, end_date, capacity, budget) VALUES (@m1, '제주 2박 3일', '2026-10-12', '2026-10-14', 4, 200000);
SET @p1 = LAST_INSERT_ID();
INSERT INTO plan_items (plan_id, content_id, day_no, visit_time, sort_order) VALUES
  (@p1, @a1, 1, '09:30', 1), (@p1, @a2, 1, '11:20', 2), (@p1, 99999001, 2, '10:00', 1);
-- owner_id는 실제 회원(@m2)을 써서 FK가 아니라 CHECK 때문에 막히는지 본다.
CALL expect_error('T05 종료일이 시작일보다 빠르면 거부', 'INSERT INTO plans (owner_id, title, start_date, end_date) VALUES (@m2, ''x'', ''2026-10-14'', ''2026-10-12'')');
CALL expect_error('T06 15일 이상 일정 거부', 'INSERT INTO plans (owner_id, title, start_date, end_date) VALUES (@m2, ''x'', ''2026-10-01'', ''2026-10-15'')');
CALL expect_error('T07 정원 11명 거부', 'INSERT INTO plans (owner_id, title, start_date, end_date, capacity) VALUES (@m2, ''x'', ''2026-10-01'', ''2026-10-02'', 11)');
CALL expect_error('T08 E2: 없는 장소로 일정 추가 거부', CONCAT('INSERT INTO plan_items (plan_id, content_id, day_no, sort_order) VALUES (', @p1, ', 12345, 1, 9)'));
CALL expect_error('T09 같은 날 같은 순서 중복 거부', CONCAT('INSERT INTO plan_items (plan_id, content_id, day_no, sort_order) VALUES (', @p1, ', ', @a1, ', 1, 1)'));
CALL expect_error('T10 0일차 거부', CONCAT('INSERT INTO plan_items (plan_id, content_id, day_no, sort_order) VALUES (', @p1, ', ', @a1, ', 0, 5)'));

-- ===== T11~T12. 찜 =====
INSERT INTO favorites (member_id, content_id) VALUES (@m1, @a1), (@m2, @a1), (@m2, 99999001);
CALL expect_error('T11 같은 장소 중복 찜 거부', CONCAT('INSERT INTO favorites (member_id, content_id) VALUES (', @m1, ', ', @a1, ')'));
CALL expect_error('T12 덤프에 없는 장소 찜 거부', CONCAT('INSERT INTO favorites (member_id, content_id) VALUES (', @m1, ', 12345)'));

-- ===== T13~T14. E4: 동행 =====
INSERT INTO companions (plan_id) VALUES (@p1);
SET @c1 = LAST_INSERT_ID();
INSERT INTO companion_participants (companion_id, member_id) VALUES (@c1, @m2), (@c1, @m3);
CALL expect_error('T13 계획 하나에 모집글 두 개 거부', CONCAT('INSERT INTO companions (plan_id) VALUES (', @p1, ')'));
CALL expect_error('T14 같은 사람 중복 참가 거부', CONCAT('INSERT INTO companion_participants (companion_id, member_id) VALUES (', @c1, ', ', @m2, ')'));

-- ===== T15~T17. E5: 댓글 대상 규칙(CHECK chk_comments_target) =====
INSERT INTO posts (owner_id, board, category, title, content) VALUES
  (@m2, 'travel', '제주', '렌터카 없이 제주 동쪽 2박 3일 다녀온 동선', '버스와 택시를 섞어서 이동한 실제 동선을 정리해봤어요. 월정리와 비자림을 들렀어요.');
SET @post1 = LAST_INSERT_ID();
INSERT INTO posts (owner_id, board, title, content) VALUES (@m1, 'notice', '서비스 점검 안내', '10월 20일 새벽 점검이 있어요.');
SET @post2 = LAST_INSERT_ID();
CALL expect_error('T15 E5: 게시글·동행 둘 다 지정하면 거부', CONCAT('INSERT INTO comments (owner_id, post_id, companion_id, content) VALUES (', @m1, ', ', @post1, ', ', @c1, ', ''x'')'));
CALL expect_error('T16 E5: 대상이 없으면 거부', CONCAT('INSERT INTO comments (owner_id, content) VALUES (', @m1, ', ''x'')'));
INSERT INTO comments (owner_id, companion_id, content) VALUES (@m3, @c1, '렌터카는 같이 빌리나요?');
SET @cc1 = LAST_INSERT_ID();
CALL expect_error('T17 E5: 수정으로 대상 두 개 만들기 거부', CONCAT('UPDATE comments SET post_id = ', @post1, ' WHERE id = ', @cc1));

-- ===== T18~T24. E6: 회원 탈퇴 =====
-- m1의 최상위 댓글에 m2가 답글 → m1 탈퇴 시 답글은 남고 부모만 NULL
INSERT INTO comments (owner_id, post_id, content) VALUES (@m1, @post1, '좋은 동선이네요!');
SET @k1 = LAST_INSERT_ID();
INSERT INTO comments (owner_id, post_id, parent_id, reply_to_member_id, content) VALUES (@m2, @post1, @k1, @m1, '감사해요. 버스 시간표도 올릴게요.');
SET @k2 = LAST_INSERT_ID();
INSERT INTO hotplaces (owner_id, content_type_id, title, image_path, latitude, longitude) VALUES (@m1, 12, '월정리의 오후', 'uploads/hotplaces/1.jpg', 33.5565, 126.7958);

DELETE FROM members WHERE id = @m1;

CALL expect_true('T18 E6: 탈퇴 회원의 계획·일정 삭제', (SELECT COUNT(*) FROM plans WHERE id = @p1) = 0 AND (SELECT COUNT(*) FROM plan_items WHERE plan_id = @p1) = 0, '');
CALL expect_true('T19 E6: 그 계획의 모집글·참가자·질문 댓글 삭제', (SELECT COUNT(*) FROM companions WHERE id = @c1) = 0 AND (SELECT COUNT(*) FROM companion_participants WHERE companion_id = @c1) = 0 AND (SELECT COUNT(*) FROM comments WHERE id = @cc1) = 0, '다른 회원이 단 동행 질문도 함께 사라짐');
CALL expect_true('T20 E6: 탈퇴 회원의 공지·핫플·찜 삭제', (SELECT COUNT(*) FROM posts WHERE id = @post2) = 0 AND (SELECT COUNT(*) FROM hotplaces) = 0 AND (SELECT COUNT(*) FROM favorites WHERE member_id = @m1) = 0, '');
CALL expect_true('T21 E6: 탈퇴 회원의 댓글 삭제', (SELECT COUNT(*) FROM comments WHERE id = @k1) = 0, '');
CALL expect_true('T22 E6: 다른 회원의 답글은 남고 부모만 NULL', (SELECT COUNT(*) FROM comments WHERE id = @k2 AND parent_id IS NULL AND reply_to_member_id IS NULL) = 1, '프론트처럼 최상위 댓글로 보임');
CALL expect_true('T23 E6: 다른 회원의 게시글·찜은 유지', (SELECT COUNT(*) FROM posts WHERE id = @post1) = 1 AND (SELECT COUNT(*) FROM favorites WHERE member_id = @m2) = 2, '');
CALL expect_error('T24 찜·일정에 쓰인 관광지는 삭제 거부(RESTRICT)','DELETE FROM attractions WHERE content_id = 99999001');

-- ===== T25. 게시글 삭제 시 댓글·답글이 함께 삭제 (CASCADE + 자기참조 SET NULL 동시 발생) =====
INSERT INTO comments (owner_id, post_id, content) VALUES (@m3, @post1, '최상위');
SET @r1 = LAST_INSERT_ID();
INSERT INTO comments (owner_id, post_id, parent_id, content) VALUES (@m2, @post1, @r1, '답글');
DELETE FROM posts WHERE id = @post1;
CALL expect_true('T25 게시글 삭제 시 댓글과 답글 모두 삭제', (SELECT COUNT(*) FROM comments WHERE post_id = @post1 OR id IN (@r1, @k2)) = 0, '');

-- ===== 조회 쿼리 실행 계획 (주요 화면) =====
-- 더미 게시글·일정을 조금 넣어 실행 계획이 의미 있게 나오게 한다.
INSERT INTO posts (owner_id, board, category, title, content)
SELECT @m2, ELT(1 + (n % 3), 'travel', 'news', 'travel'), '제주', CONCAT('제주 여행 후기 ', n), CONCAT('비자림과 월정리 해변 산책 코스 ', n)
FROM (SELECT a.no AS n FROM attractions a LIMIT 300) t;
INSERT INTO plans (owner_id, title, start_date, end_date) VALUES (@m2, '부산 여행', '2026-11-01', '2026-11-02');
SET @p2 = LAST_INSERT_ID();
INSERT INTO plan_items (plan_id, content_id, day_no, sort_order) SELECT @p2, content_id, 1, ROW_NUMBER() OVER () FROM attractions WHERE area_code = 6 LIMIT 40;
ANALYZE TABLE attractions, posts, plan_items, comments;

SELECT 'Q1 지역+종류별 관광지 (F101~F103)' AS query;
EXPLAIN SELECT no, content_id, title FROM attractions WHERE area_code = 39 AND content_type_id = 12 LIMIT 20;
SELECT 'Q2 지도 주변 관광지 (위도·경도 범위)' AS query;
EXPLAIN SELECT content_id, title FROM attractions WHERE latitude BETWEEN 33.40 AND 33.50 AND longitude BETWEEN 126.50 AND 126.60;
SELECT 'Q3 게시판 최신순 목록 + 댓글 수' AS query;
EXPLAIN SELECT p.id, p.title, m.nickname, (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS comment_count
FROM posts p JOIN members m ON m.id = p.owner_id WHERE p.board = 'travel' ORDER BY p.created_at DESC LIMIT 20;
SELECT 'Q4 게시글 검색 (ngram FULLTEXT)' AS query;
EXPLAIN SELECT id, title FROM posts WHERE MATCH(title, content) AGAINST('비자림' IN BOOLEAN MODE);
SELECT COUNT(*) AS q4_hits FROM posts WHERE MATCH(title, content) AGAINST('비자림' IN BOOLEAN MODE);
SELECT 'Q5 인기 장소 (F205)' AS query;
EXPLAIN SELECT pi.content_id, a.title, COUNT(*) AS cnt FROM plan_items pi JOIN attractions a ON a.content_id = pi.content_id
GROUP BY pi.content_id, a.title ORDER BY cnt DESC LIMIT 10;
SELECT 'Q6 모집 중 동행 목록 + 현재 인원' AS query;
EXPLAIN SELECT c.id, p.title, p.capacity, 1 + COUNT(cp.member_id) AS current_people
FROM companions c JOIN plans p ON p.id = c.plan_id LEFT JOIN companion_participants cp ON cp.companion_id = c.id
WHERE c.status = 'open' GROUP BY c.id, p.title, p.capacity ORDER BY c.created_at DESC LIMIT 20;

-- ===== T26. E10: 좌표 이상치 정리 확인 =====
CALL expect_true('T26 E10: 한국 범위 밖 좌표가 남아 있지 않음',
  (SELECT COUNT(*) FROM attractions WHERE latitude IS NOT NULL AND NOT (latitude BETWEEN 33 AND 39 AND longitude BETWEEN 124 AND 132)) = 0,
  (SELECT CONCAT('좌표 없는 관광지 ', COUNT(*), '행') FROM attractions WHERE latitude IS NULL));

SELECT 'done' AS step;
