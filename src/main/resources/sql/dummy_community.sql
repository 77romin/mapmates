-- 커뮤니티(공지사항·공유 게시판) 시연용 더미 데이터
-- 실행: mysql -ussafy -p --default-character-set=utf8mb4 < dummy_community.sql
-- 다시 실행해도 중복되지 않도록, 같은 제목의 글을 먼저 지우고 넣는다. (댓글은 함께 삭제된다)
-- 더미 회원의 비밀번호는 시연 계정 mate1 과 같다.
USE mapmates;

-- 1) 글쓴이로 쓸 더미 회원
INSERT INTO members (user_id, password, name, email, role, tags, home_lat, home_lng)
SELECT 'trip_sora', m.password, '한소라', 'sora@mapmates.kr', 'USER', '바다,사진,휴양', 37.5172, 127.0473 FROM members m
WHERE m.user_id = 'mate1' AND NOT EXISTS (SELECT 1 FROM members WHERE user_id = 'trip_sora');
INSERT INTO members (user_id, password, name, email, role, tags, home_lat, home_lng)
SELECT 'jeju_min', m.password, '오민재', 'minjae@mapmates.kr', 'USER', '휴양,맛집,바다', 33.4996, 126.5312 FROM members m
WHERE m.user_id = 'mate1' AND NOT EXISTS (SELECT 1 FROM members WHERE user_id = 'jeju_min');
INSERT INTO members (user_id, password, name, email, role, tags, home_lat, home_lng)
SELECT 'busan_sky', m.password, '김하늘', 'sky@mapmates.kr', 'USER', '바다,맛집,도시', 35.1796, 129.0756 FROM members m
WHERE m.user_id = 'mate1' AND NOT EXISTS (SELECT 1 FROM members WHERE user_id = 'busan_sky');
INSERT INTO members (user_id, password, name, email, role, tags, home_lat, home_lng)
SELECT 'hiker_lee', m.password, '이준호', 'junho@mapmates.kr', 'USER', '등산,사진,역사', 37.6584, 127.0126 FROM members m
WHERE m.user_id = 'mate1' AND NOT EXISTS (SELECT 1 FROM members WHERE user_id = 'hiker_lee');
INSERT INTO members (user_id, password, name, email, role, tags, home_lat, home_lng)
SELECT 'foodie_park', m.password, '박서연', 'seoyeon@mapmates.kr', 'USER', '맛집,도시,사진', 37.5563, 126.9236 FROM members m
WHERE m.user_id = 'mate1' AND NOT EXISTS (SELECT 1 FROM members WHERE user_id = 'foodie_park');
INSERT INTO members (user_id, password, name, email, role, tags, home_lat, home_lng)
SELECT 'photo_choi', m.password, '최유진', 'yujin@mapmates.kr', 'USER', '사진,역사,휴양', 35.8714, 128.6014 FROM members m
WHERE m.user_id = 'mate1' AND NOT EXISTS (SELECT 1 FROM members WHERE user_id = 'photo_choi');

-- 2) 같은 제목의 기존 글 삭제
DELETE FROM boards WHERE title IN ('[안내] 메이트 모집 기능 이용 수칙', '[업데이트] 플랜 보드에 동선 최적화가 추가되었습니다', '[업데이트] 이동 수단별 경로 보기 (자동차 · 도보 · 대중교통)', '[안내] 공연·행사 메뉴가 열렸습니다', '[점검] 11월 30일(월) 새벽 서비스 점검 안내', '강릉 1박 2일 다녀왔어요 (안목해변 - 오죽헌 - 중앙시장)', '전주 한옥마을 근처 현지인 맛집 물어봅니다', '설악산 울산바위 코스, 초보도 갈 만한가요?', '부산 사는 사람이 추천하는 당일치기 동선', '제주 동쪽 2박 3일, 렌터카 없이 가능할까요', '경주 야경 사진 찍기 좋은 시간대 공유', '전기차로 강원도 여행하시는 분들 충전은 어떻게 하세요?', '메이트 구해서 여수 다녀온 후기', '울산바위 다녀왔습니다 (초보 후기)', 'AI 길잡이한테 코스 짜 달라고 해 봤는데요', '대구 근대골목 투어 코스 정리', '겨울 제주 옷차림 질문드려요', '서울에서 공연 보고 밥 먹을 곳 찾을 때 팁', '12월 첫 주 속초 같이 가실 분 (메이트 모집글 올렸어요)', '이번 주말 수도권 날씨 어떤가요');

-- 3) 게시글과 댓글
INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'NOTICE', id, '[안내] 메이트 모집 기능 이용 수칙', '안녕하세요, 너랑 갈.지도 운영팀입니다.\n\n메이트 모집 기능을 안전하게 이용하실 수 있도록 기본 수칙을 안내드립니다.\n\n1. 모집글에는 여행 기간, 모집 인원, 여행 스타일을 정확히 적어 주세요.\n2. 처음 만나는 메이트와는 사람이 많은 공공장소에서 먼저 만나시길 권합니다.\n3. 경비 분담 방식은 출발 전에 미리 정해 주세요.\n4. 금전 거래를 요구하거나 개인 연락처를 무리하게 요구하는 글은 신고해 주세요.\n\n즐겁고 안전한 여행 되시길 바랍니다.', 312, '2026-11-05 10:00:00' FROM members WHERE user_id = 'admin';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'NOTICE', id, '[업데이트] 플랜 보드에 동선 최적화가 추가되었습니다', '플랜 보드에 ''동선 최적화'' 버튼이 생겼습니다.\n\n하루 일정에 담은 장소들의 방문 순서를 총 이동 거리가 가장 짧아지도록 다시 정렬해 드립니다. 첫 번째 장소는 출발지로 고정되니, 숙소를 맨 위에 두고 눌러 보세요.\n\n최적화 전후 거리도 함께 보여 드립니다.', 248, '2026-11-12 09:30:00' FROM members WHERE user_id = 'admin';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'NOTICE', id, '[업데이트] 이동 수단별 경로 보기 (자동차 · 도보 · 대중교통)', '플랜 보드에서 이동 수단을 선택해 경로를 볼 수 있습니다.\n\n- 자동차, 도보: 실제 도로를 따라가는 경로와 예상 거리·시간\n- 대중교통: 구간별 카카오맵 길찾기 연결\n\n방문 순서를 바꾸면 경로도 바로 다시 계산됩니다.', 187, '2026-11-19 14:00:00' FROM members WHERE user_id = 'admin';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'NOTICE', id, '[안내] 공연·행사 메뉴가 열렸습니다', '여행지에서 볼 수 있는 공연 정보를 ''공연·행사'' 메뉴에서 확인하실 수 있습니다.\n\n공연을 누르면 공연장 위치와 함께 주변 2km 안의 음식점, 관광지를 추천해 드립니다. 마음에 드는 곳은 바로 플랜에 담아 보세요.\n\n공연 정보는 공연예술통합전산망(KOPIS)에서 제공받습니다.', 133, '2026-11-24 11:00:00' FROM members WHERE user_id = 'admin';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'NOTICE', id, '[점검] 11월 30일(월) 새벽 서비스 점검 안내', '보다 안정적인 서비스를 위해 아래 시간에 점검을 진행합니다.\n\n- 일시: 11월 30일(월) 02:00 ~ 04:00\n- 영향: 점검 중에는 로그인과 플랜 저장이 일시 중단됩니다.\n\n작성 중인 플랜은 점검 전에 저장해 주세요. 이용에 불편을 드려 죄송합니다.', 96, '2026-11-26 17:20:00' FROM members WHERE user_id = 'admin';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '강릉 1박 2일 다녀왔어요 (안목해변 - 오죽헌 - 중앙시장)', '지난 주말에 강릉 다녀온 후기 남겨요.\n\n첫날은 안목해변 카페거리에서 시작했는데, 오전 10시 전에 가니까 주차가 수월했어요. 점심은 초당 순두부 마을에서 먹고 오죽헌으로 넘어갔습니다. 오죽헌은 생각보다 넓어서 1시간 반은 잡으셔야 해요.\n\n저녁은 중앙시장에서 닭강정이랑 호떡으로 해결했고, 둘째 날은 경포호 한 바퀴 자전거로 돌았어요. 플랜 보드에서 동선 최적화 눌렀더니 오죽헌이랑 경포대 순서가 바뀌던데, 실제로 그 순서가 훨씬 덜 돌아가더라고요.\n\n겨울 바다 보러 가실 분들 추천합니다.', 154, '2026-11-06 21:14:00' FROM members WHERE user_id = 'trip_sora';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '안목해변 주차 팁 감사해요. 저도 다음 달에 가려고 하는데 일요일 오후는 많이 막히나요?', '2026-11-06 22:03:00' FROM members WHERE user_id = 'busan_sky';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '일요일 3시 이후로는 서울 방향이 많이 막혔어요. 점심 먹고 바로 출발하시는 걸 추천해요.', '2026-11-06 22:31:00' FROM members WHERE user_id = 'trip_sora';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '중앙시장 닭강정은 어느 집에서 드셨어요? 갈 때마다 줄 긴 곳만 가게 되네요.', '2026-11-07 09:12:00' FROM members WHERE user_id = 'foodie_park';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '전주 한옥마을 근처 현지인 맛집 물어봅니다', '다음 주에 부모님 모시고 전주에 갑니다.\n\n한옥마을 안쪽 식당들은 가격대가 좀 있다고 들어서, 걸어서 10~15분 거리 안에 있는 현지 분들 가시는 곳 추천 부탁드려요. 비빔밥이나 콩나물국밥이면 좋겠고, 어른들이 계셔서 좌식보다는 테이블 있는 곳이면 더 좋습니다.\n\n지도 탐색에서 음식점만 필터 걸어 보니 후보가 너무 많네요.', 203, '2026-11-08 12:40:00' FROM members WHERE user_id = 'foodie_park';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '남부시장 쪽 콩나물국밥집들이 아침 일찍 열고 테이블석도 있어요. 한옥마을에서 걸어서 10분 정도입니다.', '2026-11-08 13:22:00' FROM members WHERE user_id = 'photo_choi';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '부모님 모시고 가시면 점심 시간대는 피하세요. 11시 반 전에 가시면 대기 없이 드실 수 있습니다.', '2026-11-08 15:47:00' FROM members WHERE user_id = 'hiker_lee';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '두 분 다 감사합니다. 남부시장 쪽으로 플랜에 담아 뒀어요!', '2026-11-08 18:05:00' FROM members WHERE user_id = 'foodie_park';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '설악산 울산바위 코스, 초보도 갈 만한가요?', '등산 경력은 동네 뒷산 정도인데, 이번에 속초 가는 김에 울산바위까지 올라가 보려고 합니다.\n\n신흥사에서 출발하는 코스가 왕복 4시간 정도라고 하던데 계단이 많다는 얘기가 있어서요. 11월 말이면 아이젠도 챙겨야 할까요?\n\n다녀오신 분들 조언 부탁드립니다.', 121, '2026-11-10 07:55:00' FROM members WHERE user_id = 'hiker_lee';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '흔들바위까지는 편하고 그 뒤 계단 구간이 좀 힘들어요. 천천히 가면 초보도 충분히 가능합니다.', '2026-11-10 09:30:00' FROM members WHERE user_id = 'trip_sora';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '11월 말이면 정상부 계단에 살얼음이 있을 수 있어요. 아이젠은 가방에 넣어 가시는 걸 추천합니다.', '2026-11-10 11:02:00' FROM members WHERE user_id = 'photo_choi';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '감사합니다. 아이젠 챙겨서 다녀와서 후기 남길게요.', '2026-11-10 12:15:00' FROM members WHERE user_id = 'hiker_lee';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '부산 사는 사람이 추천하는 당일치기 동선', '부산 토박이라 주변에서 자주 물어봐서 정리해 봅니다. 차 없이 지하철과 버스로 다니는 기준이에요.\n\n오전: 감천문화마을 (10시 전에 가야 한적합니다)\n점심: 자갈치시장이나 국제시장 먹자골목\n오후: 영도 흰여울문화마을 - 해안 산책로\n저녁: 광안리에서 광안대교 야경\n\n해운대까지 넣으면 이동 시간이 너무 길어져서 과감하게 뺐습니다. 해운대는 다음 날 오전에 따로 가시는 걸 추천해요.', 176, '2026-11-11 19:20:00' FROM members WHERE user_id = 'busan_sky';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '흰여울문화마을에서 광안리까지는 대중교통으로 얼마나 걸리나요?', '2026-11-11 20:11:00' FROM members WHERE user_id = 'jeju_min';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '버스 한 번 갈아타고 50분 정도 걸려요. 플랜 보드에서 대중교통 누르면 구간별로 카카오맵 길찾기가 연결됩니다.', '2026-11-11 20:40:00' FROM members WHERE user_id = 'busan_sky';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '이 동선 그대로 플랜에 담았습니다. 다음 주에 다녀올게요!', '2026-11-12 08:25:00' FROM members WHERE user_id = 'foodie_park';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '감천문화마을은 진짜 오전에 가야 하더라고요. 공감합니다.', '2026-11-12 10:14:00' FROM members WHERE user_id = 'trip_sora';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '제주 동쪽 2박 3일, 렌터카 없이 가능할까요', '면허가 없어서 제주를 버스로만 다녀 보려고 합니다.\n\n성산일출봉, 섭지코지, 비자림, 월정리 정도를 생각하고 있는데 버스 배차 간격이 길다고 해서 걱정이에요. 숙소를 성산 쪽에 2박 잡는 게 나을지, 하루는 세화 쪽으로 옮기는 게 나을지도 고민입니다.\n\n뚜벅이로 다녀오신 분 계신가요?', 142, '2026-11-13 22:05:00' FROM members WHERE user_id = 'jeju_min';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '저는 성산 1박, 세화 1박으로 다녀왔어요. 201번 버스가 해안을 따라 다녀서 생각보다 괜찮았습니다. 비자림만 택시 탔어요.', '2026-11-14 08:40:00' FROM members WHERE user_id = 'photo_choi';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '숙소를 나눠 잡는 쪽으로 정해야겠네요. 감사합니다!', '2026-11-14 09:15:00' FROM members WHERE user_id = 'jeju_min';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '경주 야경 사진 찍기 좋은 시간대 공유', '지난주 경주에 사진 찍으러 다녀왔습니다.\n\n동궁과 월지는 일몰 30분 전에 들어가서 자리 잡는 걸 추천해요. 조명이 켜지고 하늘이 아직 파랄 때가 가장 예쁩니다. 요즘은 5시 10분쯤 해가 지니까 4시 40분에는 입장하세요.\n\n첨성대는 그보다 조금 늦게, 완전히 어두워진 뒤가 좋았고요. 월정교는 다리 건너편 징검다리 쪽에서 찍으면 물에 비친 모습까지 담깁니다.\n\n관광지 상세에 일출·일몰 시각이 나와서 계획 짜기 편했어요.', 98, '2026-11-15 16:30:00' FROM members WHERE user_id = 'photo_choi';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '월정교 징검다리 포인트는 몰랐네요. 삼각대는 사용 가능한가요?', '2026-11-15 18:12:00' FROM members WHERE user_id = 'hiker_lee';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '평일에는 괜찮았는데 주말에는 사람이 많아서 눈치가 좀 보였어요.', '2026-11-15 19:03:00' FROM members WHERE user_id = 'photo_choi';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '전기차로 강원도 여행하시는 분들 충전은 어떻게 하세요?', '이번에 전기차로 바꾸고 처음 장거리 갑니다. 서울에서 속초, 강릉 찍고 돌아오는 일정이에요.\n\n고속도로 휴게소 급속 충전기는 주말에 줄이 길다는 얘기를 들어서, 숙소나 관광지 근처에서 충전하는 걸로 계획을 짜 보려고 합니다. 관광지 상세 화면에 주변 충전소와 사용 가능 대수가 나와서 미리 찍어 두고 있어요.\n\n배터리 몇 퍼센트쯤에서 충전하시는지 경험 공유 부탁드립니다.', 167, '2026-11-17 13:10:00' FROM members WHERE user_id = 'trip_sora';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '저는 30% 아래로 내려가기 전에 무조건 충전해요. 겨울에는 주행 가능 거리가 눈에 띄게 줄어듭니다.', '2026-11-17 14:02:00' FROM members WHERE user_id = 'busan_sky';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '속초는 해수욕장 공영주차장 쪽 충전기가 비교적 한가했어요.', '2026-11-17 16:45:00' FROM members WHERE user_id = 'jeju_min';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '30% 기준으로 잡아야겠네요. 두 분 감사합니다.', '2026-11-17 17:20:00' FROM members WHERE user_id = 'trip_sora';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '메이트 구해서 여수 다녀온 후기', '혼자 가기 심심해서 메이트 모집글 올려 보고 여수 다녀왔습니다.\n\n취향 태그를 맛집, 바다로 걸어 놨더니 비슷한 분들이 신청해 주셔서 세 명이서 다녀왔어요. 출발 전에 플랜을 같이 고칠 수 있어서, 각자 가고 싶은 식당을 하나씩 넣고 동선 최적화로 순서를 정했습니다.\n\n경비는 숙박이랑 교통만 나눠 내고 식비는 각자 냈는데 이 방식이 제일 깔끔하더라고요. 처음 만나는 분들이라 걱정했는데 여수엑스포역에서 먼저 만나 인사하고 출발하니 금방 편해졌습니다.\n\n낭만포차 거리는 생각보다 자리가 없으니 일찍 가세요.', 231, '2026-11-18 20:48:00' FROM members WHERE user_id = 'foodie_park';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '메이트 여행 궁금했는데 후기 감사합니다. 저도 다음 산행은 모집글 올려 봐야겠어요.', '2026-11-18 21:30:00' FROM members WHERE user_id = 'hiker_lee';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '경비 분담 방식 참고할게요. 식비 각자가 제일 편한 것 같아요.', '2026-11-19 08:10:00' FROM members WHERE user_id = 'busan_sky';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '여수 밤바다 케이블카는 타 보셨나요? 야경 사진 찍으러 가 보려고요.', '2026-11-19 12:33:00' FROM members WHERE user_id = 'photo_choi';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '케이블카는 해 지기 직전에 타는 걸 추천해요. 올라갈 때 노을, 내려올 때 야경을 다 볼 수 있어요.', '2026-11-19 13:01:00' FROM members WHERE user_id = 'foodie_park';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '울산바위 다녀왔습니다 (초보 후기)', '지난번에 질문드렸던 울산바위 다녀왔습니다.\n\n신흥사 주차장에서 8시에 출발해서 정상까지 2시간 10분 걸렸어요. 흔들바위까지는 정말 산책 수준이고, 그 뒤 계단이 800개가 넘는다는데 체감은 그 이상이었습니다.\n\n조언 주신 대로 아이젠을 챙겨 갔는데 정상 직전 그늘진 계단에 얼음이 있어서 실제로 썼어요. 안 챙겼으면 돌아올 뻔했습니다.\n\n정상에서 보는 동해와 속초 시내가 정말 좋았습니다. 초보도 갈 수 있지만 무릎 보호대는 꼭 챙기세요.', 88, '2026-11-20 06:40:00' FROM members WHERE user_id = 'hiker_lee';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '무사히 다녀오셨네요! 사진도 올려 주세요.', '2026-11-20 08:22:00' FROM members WHERE user_id = 'trip_sora';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '아이젠 챙기신 게 신의 한 수였네요. 고생하셨습니다.', '2026-11-20 10:05:00' FROM members WHERE user_id = 'photo_choi';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, 'AI 길잡이한테 코스 짜 달라고 해 봤는데요', '궁금해서 AI 길잡이한테 ''부모님이랑 부산 당일치기 코스 짜줘''라고 물어봤습니다.\n\n부산 사는 입장에서 봐도 동선이 꽤 그럴듯하게 나왔어요. 실제 등록된 장소만 추천해 주고, 답변 아래에 장소별로 담기 버튼이 있어서 바로 플랜으로 옮길 수 있는 게 편했습니다. 음식점도 한 곳 끼워 주더라고요.\n\n다만 같은 질문을 다시 하면 다른 코스가 나오니까, 마음에 드는 코스가 나오면 바로 전체 담기 해 두세요.', 109, '2026-11-21 15:25:00' FROM members WHERE user_id = 'busan_sky';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '저는 ''한 곳만 바꿔줘''라고 이어서 물어보니까 나머지는 그대로 두고 바꿔 주더라고요.', '2026-11-21 16:10:00' FROM members WHERE user_id = 'jeju_min';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '데이트 코스 물어봤더니 성수동 쪽으로 짜 줬어요. 꽤 쓸 만합니다.', '2026-11-21 17:42:00' FROM members WHERE user_id = 'foodie_park';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '대구 근대골목 투어 코스 정리', '대구 사는 사람으로서 근대골목 코스 정리해 봅니다. 전부 걸어서 2시간 정도면 됩니다.\n\n청라언덕 - 3.1만세운동길 - 계산성당 - 이상화 고택 - 약령시 - 진골목\n\n계산성당은 오후 햇빛이 스테인드글라스로 들어올 때가 예뻐요. 점심은 진골목 육개장이나 약령시 근처 따로국밥 추천합니다.\n\n마지막에 서문시장 야시장까지 가시면 하루 코스로 딱 좋습니다. 야시장은 금, 토, 일만 열어요.', 74, '2026-11-22 11:15:00' FROM members WHERE user_id = 'photo_choi';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '근대골목 좋죠. 스탬프 투어도 같이 하면 재미있습니다.', '2026-11-22 13:40:00' FROM members WHERE user_id = 'hiker_lee';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '겨울 제주 옷차림 질문드려요', '12월 초에 제주 갑니다. 서울보다 따뜻하다고는 하는데 바람이 세다고 해서요.\n\n패딩까지 필요한지, 아니면 코트에 경량 패딩 정도면 되는지 궁금합니다. 오름도 하나 올라갈 예정이에요.\n\n최근에 다녀오신 분 계시면 알려 주세요.', 131, '2026-11-23 18:50:00' FROM members WHERE user_id = 'jeju_min';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '기온보다 바람이 문제예요. 오름 올라가실 거면 바람막이는 꼭 챙기세요.', '2026-11-23 19:30:00' FROM members WHERE user_id = 'trip_sora';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '저는 지난주에 경량 패딩에 바람막이 겹쳐 입었는데 딱 좋았습니다. 목도리 하나 있으면 든든해요.', '2026-11-23 21:04:00' FROM members WHERE user_id = 'busan_sky';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '바람막이 챙기겠습니다. 감사해요!', '2026-11-24 07:48:00' FROM members WHERE user_id = 'jeju_min';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '서울에서 공연 보고 밥 먹을 곳 찾을 때 팁', '공연·행사 메뉴 써 보신 분 계신가요?\n\n대학로에서 연극 예매하고, 공연 상세 화면 아래에 뜨는 주변 장소 목록에서 식당을 골랐어요. 장소를 누르면 사진이랑 주소, 지금 날씨까지 뜨는 팝업이 나와서 거기서 바로 담았습니다.\n\n공연 시작 2시간 전에 식당 가는 걸로 플랜을 짜니까 시간이 딱 맞았어요. 공연장 근처는 공연 직전에 식당이 붐비니 조금 일찍 움직이세요.', 67, '2026-11-25 12:20:00' FROM members WHERE user_id = 'foodie_park';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '주변 장소가 2km 안으로 나와서 걸어갈 만한 곳만 보이는 게 좋더라고요.', '2026-11-25 14:08:00' FROM members WHERE user_id = 'photo_choi';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '12월 첫 주 속초 같이 가실 분 (메이트 모집글 올렸어요)', '12월 5일부터 6일까지 속초 1박 2일 메이트 구합니다.\n\n바다 보고 사진 찍고 맛있는 것 먹는 느긋한 일정이에요. 아바이마을, 영금정, 속초중앙시장 정도 생각하고 있고 플랜은 같이 고쳐 나가려고 합니다.\n\n메이트 메뉴에 모집글 올려 뒀으니 관심 있으신 분은 신청해 주세요. 정원은 저 포함 4명입니다.', 52, '2026-11-26 22:10:00' FROM members WHERE user_id = 'trip_sora';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '울산바위 한 번 더 가고 싶었는데 일정이 맞네요. 신청했습니다.', '2026-11-26 22:45:00' FROM members WHERE user_id = 'hiker_lee';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '속초중앙시장 닭강정 생각나네요. 저도 신청할게요!', '2026-11-27 09:20:00' FROM members WHERE user_id = 'foodie_park';

INSERT INTO boards (type, member_id, title, content, hit, created_at)
SELECT 'FREE', id, '이번 주말 수도권 날씨 어떤가요', '토요일에 북한산 가려고 하는데 비 소식이 있는지 궁금합니다.\n\n관광지 상세에서 날씨를 보니 강수확률이 30% 정도로 나오던데, 이 정도면 그냥 가도 될까요?', 23, '2026-11-27 08:05:00' FROM members WHERE user_id = 'hiker_lee';
SET @board_id = LAST_INSERT_ID();
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '30%면 저는 가는 편이에요. 방수 자켓 하나만 챙기세요.', '2026-11-27 08:40:00' FROM members WHERE user_id = 'trip_sora';
INSERT INTO comments (board_id, member_id, content, created_at) SELECT @board_id, id, '산은 날씨가 금방 바뀌니까 출발 전날 저녁에 한 번 더 확인하세요.', '2026-11-27 10:12:00' FROM members WHERE user_id = 'busan_sky';
