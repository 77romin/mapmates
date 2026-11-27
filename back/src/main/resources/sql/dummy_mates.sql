-- 메이트 모집(플랜·방문지·모집글·참여자·신청) 시연용 더미 데이터
-- 먼저 dummy_community.sql 을 실행해 더미 회원을 만든 뒤 실행한다.
-- 실행: mysql -ussafy -p --default-character-set=utf8mb4 < dummy_mates.sql
-- 다시 실행해도 중복되지 않도록 같은 제목의 플랜을 먼저 지운다. (방문지·모집글·신청은 함께 삭제된다)
USE mapmates;

DELETE FROM plans WHERE title IN ('겨울 바다 보러 속초 1박 2일', '부산 토박이와 함께하는 당일 투어', '뚜벅이로 다니는 제주 동쪽 2박 3일', '북한산 백운대 당일 산행', '경주 야경 출사 1박 2일', '전주 먹방 당일치기', '서울 궁궐 산책과 북촌 한 바퀴');

-- 겨울 바다 보러 속초 1박 2일
INSERT INTO plans (member_id, title, start_date, end_date, budget, memo, is_public, created_at)
SELECT id, '겨울 바다 보러 속초 1박 2일', '2026-12-05', '2026-12-06', 250000, NULL, 1, '2026-11-26 22:00:00' FROM members WHERE user_id = 'trip_sora';
SET @plan_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role, joined_at) SELECT @plan_id, id, 'OWNER', '2026-11-26 22:00:00' FROM members WHERE user_id = 'trip_sora';
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 0, 61644, '갯배 타고 건너가기', 5000); -- 아바이마을
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 1, 61709, '일몰 전에 도착', 0); -- 영금정
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 2, 100754, '닭강정, 오징어순대', 30000); -- 속초관광수산시장 회센터
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 2, 3, 61580, '아침 산책', 0); -- 속초해수욕장
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 2, 4, 61522, '케이블카 or 울산바위', 15000); -- 설악산 울산바위
INSERT INTO mate_posts (plan_id, member_id, title, content, capacity, tags, status, created_at)
SELECT @plan_id, id, '12월 첫 주 속초, 바다 보고 맛있는 거 먹어요', '바다 보고 사진 찍고 맛있는 것 먹는 느긋한 일정입니다.\n\n아바이마을에서 갯배 타고, 영금정에서 일몰 보고, 저녁은 중앙시장에서 해결하려고 해요. 둘째 날은 날씨 보고 설악산 케이블카나 울산바위 중에 정할게요.\n\n숙박과 교통비는 인원수로 나누고 식비는 각자 내는 걸로 생각하고 있습니다. 플랜은 같이 고쳐 나가요!', 4, '바다,사진,맛집', 'OPEN', '2026-11-26 22:00:00' FROM members WHERE user_id = 'trip_sora';
SET @post_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role) SELECT @plan_id, id, 'MATE' FROM members WHERE user_id = 'hiker_lee';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '같이 가고 싶어요!', 'ACCEPTED' FROM members WHERE user_id = 'hiker_lee';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '속초중앙시장 닭강정 먹으러 가고 싶어요. 운전 가능합니다!', 'PENDING' FROM members WHERE user_id = 'foodie_park';

-- 부산 토박이와 함께하는 당일 투어
INSERT INTO plans (member_id, title, start_date, end_date, budget, memo, is_public, created_at)
SELECT id, '부산 토박이와 함께하는 당일 투어', '2026-12-12', '2026-12-12', 80000, NULL, 1, '2026-11-25 19:30:00' FROM members WHERE user_id = 'busan_sky';
SET @plan_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role, joined_at) SELECT @plan_id, id, 'OWNER', '2026-11-25 19:30:00' FROM members WHERE user_id = 'busan_sky';
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 0, 58727, '오전 10시 전 도착', 0); -- 부산 감천문화마을
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 1, 87493, '점심', 20000); -- 부산 자갈치시장
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 2, 58950, '해안 산책로', 0); -- 흰여울문화마을
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 3, 58619, '광안대교 야경', 0); -- 광안리해수욕장
INSERT INTO mate_posts (plan_id, member_id, title, content, capacity, tags, status, created_at)
SELECT @plan_id, id, '부산 사는 사람이 안내하는 당일 코스 (대중교통)', '부산 토박이가 지하철과 버스로 다니는 당일 코스를 안내합니다.\n\n감천문화마을은 사람 없는 오전에 먼저 가고, 자갈치에서 점심 먹고 영도 흰여울문화마을로 넘어가요. 저녁은 광안리에서 야경 보면서 마무리합니다.\n\n많이 걷는 일정이라 편한 신발 신고 오세요. 식비와 교통비만 각자 준비하시면 됩니다.', 3, '바다,맛집,도시', 'OPEN', '2026-11-25 19:30:00' FROM members WHERE user_id = 'busan_sky';
SET @post_id = LAST_INSERT_ID();
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '부산은 처음이라 현지 분이랑 다니면 좋을 것 같아요. 신청합니다.', 'PENDING' FROM members WHERE user_id = 'jeju_min';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '흰여울문화마을 사진 찍고 싶었어요!', 'PENDING' FROM members WHERE user_id = 'photo_choi';

-- 뚜벅이로 다니는 제주 동쪽 2박 3일
INSERT INTO plans (member_id, title, start_date, end_date, budget, memo, is_public, created_at)
SELECT id, '뚜벅이로 다니는 제주 동쪽 2박 3일', '2026-12-18', '2026-12-20', 400000, NULL, 1, '2026-11-24 21:10:00' FROM members WHERE user_id = 'jeju_min';
SET @plan_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role, joined_at) SELECT @plan_id, id, 'OWNER', '2026-11-24 21:10:00' FROM members WHERE user_id = 'jeju_min';
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 0, 69483, '오후 입장, 일몰', 5000); -- 성산일출봉 [유네스코 세계자연유산]
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 1, 69478, NULL, 0); -- 섭지코지
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 2, 2, 69414, '오전 일찍', 3000); -- 비자림
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 2, 3, 69621, '카페에서 쉬기', 15000); -- 월정리해변(월정리해수욕장)
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 3, 4, 69364, NULL, 4000); -- 만장굴 (제주도 국가지질공원)
INSERT INTO mate_posts (plan_id, member_id, title, content, capacity, tags, status, created_at)
SELECT @plan_id, id, '렌터카 없이 버스로 제주 동쪽 돌아요', '면허가 없어서 버스로만 다니는 제주 여행입니다. 같이 걸어 다닐 분 찾아요.\n\n첫날은 성산일출봉과 섭지코지, 둘째 날은 비자림과 월정리 해변, 마지막 날은 만장굴 보고 공항으로 갑니다. 숙소는 성산 1박, 세화 1박으로 나눠 잡을 예정이에요.\n\n버스 배차가 길어서 일정은 느슨하게 짰습니다. 서두르지 않는 분이면 좋겠어요.', 4, '휴양,바다,사진', 'OPEN', '2026-11-24 21:10:00' FROM members WHERE user_id = 'jeju_min';
SET @post_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role) SELECT @plan_id, id, 'MATE' FROM members WHERE user_id = 'photo_choi';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '같이 가고 싶어요!', 'ACCEPTED' FROM members WHERE user_id = 'photo_choi';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '겨울 제주 바다 사진 찍고 싶어요. 걷는 거 좋아합니다.', 'PENDING' FROM members WHERE user_id = 'trip_sora';

-- 북한산 백운대 당일 산행
INSERT INTO plans (member_id, title, start_date, end_date, budget, memo, is_public, created_at)
SELECT id, '북한산 백운대 당일 산행', '2026-11-29', '2026-11-29', 30000, NULL, 1, '2026-11-27 07:20:00' FROM members WHERE user_id = 'hiker_lee';
SET @plan_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role, joined_at) SELECT @plan_id, id, 'OWNER', '2026-11-27 07:20:00' FROM members WHERE user_id = 'hiker_lee';
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 0, 56941, '우이동 들머리 9시 집결', 0); -- 북한산 백운대(우이동)
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 1, 56803, '하산길에 들르기', 0); -- 도선사(서울)
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 2, 57240, '뒤풀이', 15000); -- 우이령 숲속문화마을(구 우이동 먹거리마을)
INSERT INTO mate_posts (plan_id, member_id, title, content, capacity, tags, status, created_at)
SELECT @plan_id, id, '이번 주말 북한산 백운대 같이 오르실 분', '토요일 오전에 북한산 백운대 다녀옵니다.\n\n우이동에서 9시에 모여 백운대까지 올라갔다가 도선사 쪽으로 내려옵니다. 왕복 4시간 정도 예상하고, 초보도 올 수 있는 속도로 갑니다.\n\n정상부에 얼음이 있을 수 있어서 아이젠은 꼭 챙겨 주세요. 하산 후 우이동에서 파전에 막걸리 한잔합니다.', 5, '등산,사진', 'OPEN', '2026-11-27 07:20:00' FROM members WHERE user_id = 'hiker_lee';
SET @post_id = LAST_INSERT_ID();
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '초보인데 따라가도 될까요? 아이젠 챙기겠습니다.', 'PENDING' FROM members WHERE user_id = 'trip_sora';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '서울 올라가는 김에 신청해 봅니다.', 'PENDING' FROM members WHERE user_id = 'busan_sky';

-- 경주 야경 출사 1박 2일
INSERT INTO plans (member_id, title, start_date, end_date, budget, memo, is_public, created_at)
SELECT id, '경주 야경 출사 1박 2일', '2026-12-05', '2026-12-06', 200000, NULL, 1, '2026-11-20 20:00:00' FROM members WHERE user_id = 'photo_choi';
SET @plan_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role, joined_at) SELECT @plan_id, id, 'OWNER', '2026-11-20 20:00:00' FROM members WHERE user_id = 'photo_choi';
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 0, 64098, '일몰 30분 전 입장', 3000); -- 경주 동궁과 월지
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 1, 64143, '완전히 어두워진 뒤', 0); -- 경주 첨성대
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 2, 65014, '징검다리 포인트', 0); -- 월정교
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 2, 3, 64111, '오전', 6000); -- 경주 불국사 [유네스코 세계유산]
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 2, 4, 64151, '점심, 카페', 25000); -- 경주 황리단길
INSERT INTO mate_posts (plan_id, member_id, title, content, capacity, tags, status, created_at)
SELECT @plan_id, id, '경주 야경 찍으러 가요 (삼각대 있는 분 환영)', '경주 야경 사진 찍으러 갑니다.\n\n동궁과 월지는 조명이 켜지고 하늘이 아직 파랄 때가 가장 예뻐서 일몰 30분 전에 들어가 자리 잡을 거예요. 그다음 첨성대, 월정교 순서로 돕니다. 둘째 날은 불국사 보고 황리단길에서 점심 먹고 해산합니다.\n\n사진 찍는 속도에 맞춰 천천히 움직입니다. 카메라가 없어도 괜찮아요.', 3, '사진,역사', 'CLOSED', '2026-11-20 20:00:00' FROM members WHERE user_id = 'photo_choi';
SET @post_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role) SELECT @plan_id, id, 'MATE' FROM members WHERE user_id = 'trip_sora';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '같이 가고 싶어요!', 'ACCEPTED' FROM members WHERE user_id = 'trip_sora';
INSERT INTO plan_members (plan_id, member_id, role) SELECT @plan_id, id, 'MATE' FROM members WHERE user_id = 'hiker_lee';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '같이 가고 싶어요!', 'ACCEPTED' FROM members WHERE user_id = 'hiker_lee';

-- 전주 먹방 당일치기
INSERT INTO plans (member_id, title, start_date, end_date, budget, memo, is_public, created_at)
SELECT id, '전주 먹방 당일치기', '2026-12-13', '2026-12-13', 70000, NULL, 1, '2026-11-23 12:45:00' FROM members WHERE user_id = 'foodie_park';
SET @plan_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role, joined_at) SELECT @plan_id, id, 'OWNER', '2026-11-23 12:45:00' FROM members WHERE user_id = 'foodie_park';
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 0, 91232, '콩나물국밥으로 시작', 9000); -- 전주 남부시장
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 1, 66833, NULL, 3000); -- 경기전
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 2, 67655, NULL, 0); -- 전주전동성당
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 3, 67650, '길거리 간식', 20000); -- 전주 한옥마을
INSERT INTO mate_posts (plan_id, member_id, title, content, capacity, tags, status, created_at)
SELECT @plan_id, id, '전주에서 하루 종일 먹기만 할 분', '전주에서 먹는 게 목적인 당일 여행입니다.\n\n아침은 남부시장 콩나물국밥으로 시작해서 경기전과 전동성당을 가볍게 보고, 한옥마을에서 길거리 간식을 차례로 먹습니다. 저녁은 막걸리 골목에서 마무리해요.\n\n용산에서 KTX로 같이 내려가실 분이면 더 좋습니다. 식비는 각자 냅니다.', 4, '맛집,도시,역사', 'OPEN', '2026-11-23 12:45:00' FROM members WHERE user_id = 'foodie_park';
SET @post_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role) SELECT @plan_id, id, 'MATE' FROM members WHERE user_id = 'busan_sky';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '같이 가고 싶어요!', 'ACCEPTED' FROM members WHERE user_id = 'busan_sky';
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '전동성당 사진도 찍고 싶어요. 대구에서 바로 넘어갈게요.', 'PENDING' FROM members WHERE user_id = 'photo_choi';

-- 서울 궁궐 산책과 북촌 한 바퀴
INSERT INTO plans (member_id, title, start_date, end_date, budget, memo, is_public, created_at)
SELECT id, '서울 궁궐 산책과 북촌 한 바퀴', '2026-12-06', '2026-12-06', 50000, NULL, 1, '2026-11-26 10:15:00' FROM members WHERE user_id = 'mate2';
SET @plan_id = LAST_INSERT_ID();
INSERT INTO plan_members (plan_id, member_id, role, joined_at) SELECT @plan_id, id, 'OWNER', '2026-11-26 10:15:00' FROM members WHERE user_id = 'mate2';
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 0, 56664, '수문장 교대식 10시', 3000); -- 경복궁
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 1, 92668, '점심', 10000); -- 삼청동수제비
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 2, 56938, NULL, 0); -- 북촌한옥마을
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 3, 57341, '후원 예약 필요', 8000); -- 창덕궁과 후원 [유네스코 세계유산]
INSERT INTO plan_items (plan_id, day_no, seq, attraction_no, memo, cost) VALUES (@plan_id, 1, 4, 57285, '전통찻집', 10000); -- 인사동
INSERT INTO mate_posts (plan_id, member_id, title, content, capacity, tags, status, created_at)
SELECT @plan_id, id, '토요일 궁궐 산책, 걷는 거 좋아하는 분', '경복궁에서 시작해 북촌을 거쳐 창덕궁까지 걸어서 도는 일정입니다.\n\n10시 수문장 교대식에 맞춰 경복궁에서 만나고, 삼청동에서 점심 먹고 북촌한옥마을을 지나 창덕궁으로 갑니다. 마지막은 인사동 찻집에서 쉬어요.\n\n전부 걸어서 이동하니 편한 신발 필수입니다. 한복 입고 오시면 궁궐 입장료가 무료예요.', 4, '역사,맛집,도시', 'OPEN', '2026-11-26 10:15:00' FROM members WHERE user_id = 'mate2';
SET @post_id = LAST_INSERT_ID();
INSERT INTO mate_applications (post_id, member_id, message, status) SELECT @post_id, id, '삼청동수제비 좋아해요. 신청합니다!', 'PENDING' FROM members WHERE user_id = 'foodie_park';
