# ERD 추천안 검증 스크립트

1단계 토의용 ERD 추천안(E1~E10)을 실제 MySQL에서 확인하는 스크립트예요.
SSAFY_TRIP 덤프(관광지 50,915행)를 임시 스키마 `mapmates_erd_check`에 넣고, 추천안 DDL을 적용한 뒤 시나리오 테스트와 실행 계획을 출력해요.

> 결정이 확정되면 `10_schema.sql`을 `db/schema.sql`의 출발점으로 쓰고, 이 폴더는 AI 검증 기록(`db/AI_REVIEW.md`)의 근거 자료로 남겨요.

## 파일

| 파일 | 내용 |
| --- | --- |
| `build_base.py` | `db/ref`의 SSAFY_TRIP 스키마·덤프를 `mapmates_erd_check` 이름으로 바꿔 `00_base.sql` 생성 (git 제외) |
| `10_schema.sql` | 추천안 DDL — 덤프 테이블 보완 + 우리 테이블 9개 |
| `20_tests.sql` | 시나리오 테스트 T01~T26, 주요 화면 쿼리 Q1~Q6 `EXPLAIN` |
| `30_spatial_compare.sql` | E10 대안 비교: B-tree 복합 인덱스 vs SPATIAL 인덱스 |
| `run_all.sql` | 위 파일을 순서대로 실행 |
| `results/2026-11-06_mysql-8.0.46.txt` | 실행 결과 (26개 모두 PASS) |

## 실행 방법 (PowerShell)

```powershell
cd db/verify
python build_base.py ../ref .
mysql -u <사용자> -p --default-character-set=utf8mb4 -t -e "source run_all.sql" > results/my-result.txt
```

- 데이터베이스를 만들 수 있는 계정이 필요해요. 1~2분 걸려요.
- `result` 열이 모두 `PASS`면 통과예요. 확인 명령: `Select-String "FAIL" results/my-result.txt`
- 끝나면 임시 스키마를 지워요: `mysql -u <사용자> -p -e "DROP DATABASE mapmates_erd_check;"`

## 검증 결과 요약 (MySQL 8.0.46)

| 결정 | 확인한 것 | 테스트 |
| --- | --- | --- |
| E1 | `(시도, 구군)` 복합 FK가 잘못된 조합을 막음. 덤프 50,915행 모두 만족 | T04 |
| E2 | 없는 장소 upsert, 지역코드 NULL 허용, 없는 장소로 일정·찜 거부, 쓰인 관광지 삭제 거부 | T02 T03 T08 T12 T24 |
| E3 | 기간 역전·15일 이상·정원 11명·0일차·같은 날 순서 중복 거부 | T05~T10 |
| E4 | 계획 하나에 모집글 하나, 중복 참가 거부 | T13 T14 |
| E5 | CHECK가 ‘게시글·동행 중 정확히 하나’를 강제. SET NULL FK 컬럼에는 CHECK 불가(오류 3823) | T01 T15~T17 |
| E6 | 탈퇴 시 본인 데이터 삭제, 남의 답글은 부모만 NULL로 남음, 남의 글 유지 | T18~T23 |
| E6 | 게시글 삭제 시 댓글·답글 함께 삭제 (CASCADE + 자기참조 SET NULL 동시) | T25 |
| E10 | 한국 범위 밖 좌표 94행 정리 | T26 |
| 프론트 대조 | 분류 없는 게시글 거부, 조회수가 올라도 `updated_at`은 NULL 유지(‘수정됨’ 오표시 방지), 글 수정 시에만 채워짐 | T27~T30 |
| 프론트 대조 | 핫플 수정 시 `updated_at` 갱신, 게시글 검색이 닉네임·분류까지 찾음 | T31 T32 T32b |

| 쿼리 | 사용 인덱스 | 읽는 행(추정) |
| --- | --- | --- |
| Q1 지역+종류별 관광지 | `idx_attractions_area_type` | 588 |
| Q2 지도 주변 관광지 | `idx_attractions_lat_lng` (없으면 전체 51,766행) | 838 |
| Q3 게시판 최신순 + 댓글 수 | `idx_posts_board_created`, `idx_comments_post` | 206 |
| Q4 한글 검색 | `ft_posts_title_content` (ngram FULLTEXT) | – |
| Q5 인기 장소 | `idx_plan_items_content` | 40 |
| Q6 모집 중 동행 + 인원 | `idx_companions_status` | 1 |
| E10-B SPATIAL 비교 | `sp_attractions_location` | 324 (실제 결과 85행은 같음) |

‘읽는 행’은 옵티마이저 추정치라 실행할 때마다 조금씩 달라요.

## 검증하면서 처음 추천안에서 바뀐 점

- **E4:** `companions.owner_id`, 참가자 `host` 행 제거 (`plans.owner_id`로 알 수 있는 값이라 3NF 위반)
- **E5:** 정적 검토 때는 “CASCADE 컬럼에 CHECK 불가”로 예상하고 트리거를 대안으로 냈지만, 실행해 보니 CHECK가 정상 동작했어요. 그래서 처음 추천안(CHECK)을 그대로 유지해요.
- **E1:** 덤프 원본 오류 두 가지 보완 — 구군 FK를 복합 FK로 교체, `contenttypes` 39번 이름 ‘관광지’ → ‘음식점’
- **E10 추가:** 지도 주변 조회 인덱스, 좌표 이상치 정리

## 프론트 화면 대조로 보완한 점 (posts·comments·hotplaces, 마예은)

| 대상 | 프론트 | 보완 |
| --- | --- | --- |
| `posts.updated_at` | 값이 있으면 상세 화면에 ‘(수정됨)’ 표시 (`post-detail.js`) | `ON UPDATE` 제거, `NULL DEFAULT NULL`. 조회수 +1(E9)로는 바뀌지 않고 글 수정 SQL에서만 `updated_at = NOW()` |
| `posts.category` | 글쓰기에서 필수 입력, 최대 10자 | `NULL` → `NOT NULL` |
| `posts.board` 권한 | 일반 회원은 travel만, news·notice는 관리자만 (`board.js`) | DDL 변경 없음. Service 규칙으로 명시 |
| 게시글 검색 | 제목·내용·작성자 닉네임·분류 검색 (`community.js`) | FULLTEXT(제목·내용) + `nickname LIKE` + `category LIKE` 형태로 검색 SQL 작성 (T32) |
| `hotplaces.updated_at` | API 명세에 수정(PUT) 있음 | 다른 테이블처럼 `ON UPDATE CURRENT_TIMESTAMP` 컬럼 추가 |
| `comments` | 내용 500자, 2단계 답글, 답글 있으면 내용만 삭제 | 변경 없음 (DDL과 일치) |
