-- 초기 데이터 적재 스크립트
-- 1) 관광지 데이터: SSAFY 제공 ssafy_trip 덤프(한국관광공사 국문 관광정보)를 mapmates 로 복사
USE mapmates;

INSERT INTO contenttypes (content_type_id, content_type_name)
SELECT content_type_id, content_type_name FROM ssafy_trip.contenttypes;

INSERT INTO sidos (sido_code, sido_name)
SELECT sido_code, sido_name FROM ssafy_trip.sidos;

INSERT INTO guguns (sido_code, gugun_code, gugun_name)
SELECT sido_code, gugun_code, MAX(gugun_name) FROM ssafy_trip.guguns GROUP BY sido_code, gugun_code;

INSERT INTO attractions (no, content_id, title, content_type_id, area_code, si_gun_gu_code, first_image1, first_image2,
                         map_level, latitude, longitude, tel, addr1, addr2, homepage, overview)
SELECT no, content_id, title, content_type_id, area_code, si_gun_gu_code, first_image1, first_image2,
       map_level, latitude, longitude, tel, addr1, addr2, homepage, overview
FROM ssafy_trip.attractions;

-- 2) 더미 회원/게시글은 서버 최초 기동 시 DataInitializer 가 생성한다.
--    (admin / admin1234, mate1 / mate1234, mate2 / mate1234)

-- 제공 덤프의 콘텐츠 타입명 오류 보정 (39 = 음식점)
UPDATE contenttypes SET content_type_name = '음식점' WHERE content_type_id = 39;
