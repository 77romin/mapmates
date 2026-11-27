# 너랑 갈.지도 (mapmates) — Backend

> 너랑 갈 다음 여행을 지도 위에 · Find your route. Meet your mates.

Spring Boot 4.1 + MyBatis + MySQL + Spring AI 2.0 기반 REST API 서버다.
설계 문서(요구사항, ERD, API 설계서, 클래스 다이어그램)는 `allfiles` 저장소의 `docs/`에 있다.

## 기술 스택

| 구분 | 사용 |
|---|---|
| 언어·프레임워크 | Java 21, Spring Boot 4.1.2 (Spring MVC) |
| DB | MySQL 8, MyBatis (어노테이션 Mapper) |
| AI | Spring AI 2.0.2 (OpenAI 호환, SSAFY GMS) |
| 인증 | JWT(HS256), 비밀번호 PBKDF2 해시 |
| 외부 데이터 | 한국관광공사 관광지, 기상청 단기예보, 한국환경공단 전기차 충전소, KOPIS 공연, 서울시 문화행사, 뉴스 RSS |

## 패키지 구조

```
com.ssafy.mapmates
├── controller   REST API (Member, Attraction, Plan, Mate, Hotplace, Board, News, Ai)
├── service      비즈니스 로직, 외부 API 연동
├── dao          MyBatis Mapper
├── dto          데이터 객체
├── tool         Spring AI Tool 6종 (관광지 검색, 날씨, 경로, 내 플랜, 문서 검색, 공연 검색)
├── planner      다중 Tool 호출 계획·검증·실행 (TripPlanner)
├── algorithm    동선 최적화, 추천, 거리·일출일몰 계산
├── config       Web, Spring AI, 초기 데이터
└── common       JWT, 인증 인터셉터, 예외 처리
```

## 실행 방법

```bash
# 1) DB 생성과 데이터 적재 (SSAFY 제공 ssafy_trip DB가 있어야 한다)
mysql -ussafy -p --default-character-set=utf8mb4 < src/main/resources/sql/schema.sql
mysql -ussafy -p --default-character-set=utf8mb4 < src/main/resources/sql/data.sql

# (선택) 시연용 더미 데이터: 커뮤니티 글·댓글, 메이트 모집글. 서버를 한 번 실행해 기본 계정이 생긴 뒤에 넣는다
mysql -ussafy -p --default-character-set=utf8mb4 < src/main/resources/sql/dummy_community.sql
mysql -ussafy -p --default-character-set=utf8mb4 < src/main/resources/sql/dummy_mates.sql

# 2) 키 설정
cp src/main/resources/application-private.properties.example src/main/resources/application-private.properties
#   spring.ai.openai.api-key = SSAFY GMS 키
#   datago.service-key       = 공공데이터포털 인증키
#   kopis.service-key        = KOPIS 공연예술통합전산망 서비스 키
#   seoul.api-key            = 서울 열린데이터 광장 인증키

# 3) 실행 (http://localhost:8080)
./mvnw spring-boot:run
```

최초 기동 시 시연용 계정(`admin`, `mate1`, `mate2`)과 공지 1건이 생성된다. (`config/DataInitializer`)
문서 검색 Tool은 AI 서버(`allfiles/ai-server`, 8000번 포트)를 호출한다. AI 서버가 꺼져 있으면 빈 결과로 처리한다.

## 기능

### 기본 기능
- **관광지**: 시도·구군·유형(8종) 필터, 이름·주소 검색, 상세 조회
- **회원**: 가입, 수정, 조회, 탈퇴, 로그인, 로그아웃, 비밀번호 찾기(임시 비밀번호)

### 추가 기능
- **여행 계획**: 일자별 방문지·경비·메모·예산 저장, 순서 변경, 이동 수단별 경로(자동차·도보 실제 도로, 대중교통 길찾기 링크)
- **핫플레이스**: 사진·좌표·방문일·유형·설명 등록
- **메이트**: 모집글, 참여 신청, 수락·거절, 공동 편집, 취향 일치도, 중간 집결지
- **부가정보**: 날씨, 일출·일몰, 전기차 충전소, 주변 축제·상권
- **공연·행사**: KOPIS 공연 목록·상세·시설, 서울시 문화행사, 공연장 주변 장소 추천

### 심화 기능
- **공지사항·게시판**: CRUD, 검색, 댓글 (공지는 관리자만 작성)
- **여행 뉴스**: 키워드별 RSS 수집 후 DB 저장·조회
- **알고리즘**: 동선 최적화(Held-Karp / 최근접 이웃 + 2-opt), 코사인 유사도 추천, 기하 중앙값
- **AI 길잡이**: 단일·다중 Tool 호출, Planner

## Spring AI 구조

| 역할 | 구현 |
|---|---|
| Client | `AiController`, `AiService` |
| Tool | `tool` 패키지 — 로직은 Service에 위임 |
| Resource | `planner/StepResult` (Tool 실행 결과) |
| Prompt | `resources/prompts/system.txt`, `planner.txt` |
| Planner | `planner/TripPlanner` — 계획(LLM) → 검증(서버) → 실행(서버) |

`POST /api/ai/chat` 의 `mode`로 호출 방식을 고른다.

| mode | 동작 |
|---|---|
| `simple` | LLM만 호출 |
| `single` | `tool`로 지정한 Tool 하나만 제공 (단일 Tool 검증) |
| `multi` | 모든 Tool 제공, LLM이 연쇄 호출 |
| `planner` | Planner가 순서를 정하고 서버가 실행, 결과를 Prompt에 통합 (기본) |

```bash
curl -X POST http://localhost:8080/api/ai/chat -H "Content-Type: application/json" \
  -d '{"message":"Busan 2-day course with weather","mode":"planner"}'
```

장소 추천 단계는 "AI가 조건에 맞는 장소를 추천 → 서버가 DB에 등록된 장소인지 확인 → 부족하면 주변 실제 장소에서 보충" 순서로 동작한다. 그래서 질문의 조건(동행, 일수, 분위기)에 따라 결과가 달라지고, 답에 쓰이는 장소는 모두 DB에 있는 장소다.

Planner 실행 예 — "이번 주말 부산 1박2일 코스 짜줘. 날씨도 알려줘"

```
SEARCH_ATTRACTIONS (region=부산, AI 추천 → DB 확인)
   └ 결과의 첫 장소 좌표 → GET_WEATHER (lat=35.0230, lng=128.8169)
   └ 결과의 번호 목록   → OPTIMIZE_ROUTE (attractionNos=[58599, 58600, ...])
→ 세 결과를 Prompt에 넣어 최종 응답 생성
```

고찰(단일/다중 Tool 비교, 장단점, AI Server 분리 설계)은 `allfiles/docs/09_AI_고찰보고서.md`에 있다.

## 주요 API

전체 목록은 `allfiles/docs/05_API설계서.md` 참고.

| 영역 | 예 |
|---|---|
| 인증 | `POST /api/auth/join`, `/login`, `/find-password` |
| 관광지 | `GET /api/attractions?sidoCode=6&contentTypeId=12`, `/{no}`, `/{no}/weather`, `/{no}/chargers`, `/{no}/nearby?kind=festival` |
| 플랜 | `GET·POST /api/plans`, `PUT·DELETE /api/plans/{id}`, `POST /api/plans/optimize`, `GET /api/plans/{id}/meeting-point` |
| 메이트 | `GET·POST /api/mates`, `POST /api/mates/{id}/apply`, `POST /api/mates/applications/{id}/accept` |
| 커뮤니티 | `GET·POST /api/boards`, `POST /api/boards/{id}/comments`, `GET /api/news` |
| AI | `POST /api/ai/chat` |
