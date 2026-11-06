# MapMates 백엔드 설계

> Web(Backend) PJT 5회차 명세(Servlet MVC)와 DB PJT 6회차 명세(Service·DAO + MySQL)를 한 번에 만족하도록 설계했다.
> 프론트(`../front`)는 지금 모든 데이터를 브라우저 `localStorage`에 저장한다. 이 백엔드는 그 저장소를 MySQL과 JSON API로 대체한다.

## 1. 기술 선택

| 항목 | 선택 | 이유 |
| --- | --- | --- |
| 언어 / 빌드 | Java 17 이상(로컬 JDK 21), Maven WAR | STS·IntelliJ 어디서든 Import 가능 |
| 웹 | Jakarta Servlet 6.0 (Tomcat 10.1) | 명세의 Servlet MVC. Spring 미사용 |
| 화면 | 기존 정적 HTML/JS 유지 + **JSON API** | JSP로 화면을 다시 만들지 않는다. 프론트는 `fetch`로 API 호출 |
| DB 접근 | 순수 JDBC (`PreparedStatement`) | DB PJT의 DAO 구현 요구사항 |
| DB | MySQL 8 (`mapmates` 스키마) | 관광지 데이터는 `db/ref/SSAFY_TRIP_Dump.sql` 재사용 |
| JSON | Jackson (`jackson-databind`, `jsr310`) | 요청·응답 직렬화 |
| 비밀번호 | PBKDF2-HmacSHA256 (JDK 내장) | 평문 저장 금지, 추가 라이브러리 불필요 |
| 인증 | `HttpSession` + `JSESSIONID` 쿠키 | 수업 범위의 표준 방식 |

**배포 형태**: Maven이 `../front`의 정적 파일을 WAR에 함께 넣는다. 그래서 `http://localhost:8080/mapmates/index.html`과 `/mapmates/api/*`가 같은 출처(origin)가 되어 세션 쿠키와 CORS 문제가 없다. 프론트만 `dev-server.js`(5500 포트)로 띄울 때를 위해 CORS 필터도 둔다.

## 2. 계층 구조 (MVC)

```
Browser (front/*.html, js/*.js)
   │  fetch('/mapmates/api/...')  JSON
   ▼
[Filter]      EncodingFilter → CorsFilter
   ▼
[Controller]  *Servlet (BaseApiServlet 상속)  ── URL·HTTP 메서드 라우팅, 요청 파싱, 로그인 확인
   ▼
[Service]     *Service 인터페이스 / *ServiceImpl ── 검증, 권한, 트랜잭션 경계
   ▼
[DAO]         *Dao 인터페이스 / *DaoImpl     ── SQL 실행만 담당 (Connection을 인자로 받음)
   ▼
[DB]          MySQL  (DBUtil이 Connection 발급)
```

규칙

- **Controller는 SQL을 모르고, DAO는 HTTP를 모른다.** Service만 둘을 잇는다.
- **트랜잭션은 Service가 연다.** `Tx.read(...)` / `Tx.write(...)`가 Connection을 만들고 DAO에 넘긴다. 여행계획 + 일정 항목 저장처럼 여러 테이블을 바꾸는 작업이 한 트랜잭션으로 묶인다.
- Service와 DAO는 **인터페이스 + 구현체 + 싱글톤(`getInstance()`)**. 클래스 다이어그램에서 의존 방향이 명확하다.
- 오류는 `ApiException(status, code, message)`로 던지고, `BaseApiServlet`이 일관된 JSON으로 바꾼다.

## 3. 패키지 구조

```
back/
├── pom.xml
├── docs/
│   ├── BACKEND_DESIGN.md          ← 이 문서
│   └── sql/members-draft.sql      ← 회원 테이블 임시 DDL (정식 스키마는 DB PJT에서 db/에 작성)
└── src/main/
    ├── resources/
    │   └── application.properties.example   ← 복사해서 application.properties로 사용 (git 제외)
    ├── webapp/WEB-INF/web.xml
    └── java/com/mapmates/
        ├── common/
        │   ├── config/AppConfig.java         설정 파일 + 환경변수 로딩
        │   ├── db/DBUtil.java                Connection 발급
        │   ├── db/Tx.java                    트랜잭션 템플릿
        │   ├── db/DataAccessException.java
        │   ├── exception/ApiException.java   HTTP 상태 + 오류 코드
        │   ├── filter/EncodingFilter.java
        │   ├── filter/CorsFilter.java
        │   ├── security/PasswordHasher.java
        │   ├── security/SessionUser.java     세션에 담는 로그인 정보
        │   └── web/
        │       ├── BaseApiServlet.java       경로 패턴 라우터 + 예외 처리
        │       ├── ApiRequest.java           pathVar / query / body / 로그인 사용자
        │       ├── JsonUtil.java
        │       ├── PageResult.java           목록 응답 {items, page, size, total}
        │       └── Validator.java            공통 입력 검증
        ├── member/      (구현 완료 — 다른 도메인의 기준 예시)
        │   ├── controller/AuthServlet.java   /api/auth/*
        │   ├── controller/MemberServlet.java /api/members/*
        │   ├── service/MemberService(.java|Impl.java)
        │   ├── dao/MemberDao(.java|Impl.java)
        │   └── dto/Member, MemberResponse, SignupRequest, LoginRequest,
        │           MemberUpdateRequest, PasswordChangeRequest, PasswordResetRequest
        ├── attraction/  관광지·지역·콘텐츠타입 조회 (SSAFY_TRIP 테이블)
        ├── plan/        내 여행계획 + 일정 항목 + 찜(favorite)
        ├── companion/   동행 모집글 + 참가자
        ├── board/       게시글 (travel / news / notice)
        ├── comment/     게시글·동행 댓글, 대댓글
        └── hotplace/    회원 핫플레이스
```

각 도메인은 `member/`와 같은 `controller / service / dao / dto` 4개 하위 패키지를 가진다.

## 4. 도메인과 프론트 데이터의 대응

프론트 `js/shared.js`의 `state` 구조에서 그대로 뽑았다. DB 테이블 이름은 DB PJT에서 ERD를 그리며 확정한다.

| 프론트 state | 백엔드 도메인 | 예상 테이블 |
| --- | --- | --- |
| `members`, `user`, `loggedIn` | member | `members` |
| (TourAPI 실시간 조회) | attraction | `sidos`, `guguns`, `contenttypes`, `attractions` (SSAFY_TRIP 재사용) |
| `plans[]` = `{trip, tripSchedule[], itineraryPlaces{}}` | plan | `plans`, `plan_items` |
| `favorites[]` | plan | `favorites` |
| `companions[]`, `participants[]` | companion | `companions`, `companion_participants` |
| `posts[]` (`board`: travel / news / notice) | board | `posts` |
| `postComments{}`, `companionComments{}` (`parentId`, soft delete) | comment | `comments` (target_type + target_id) |
| `hotplaces[]` | hotplace | `hotplaces` |
| `drafts`, `routeMode`, `pendingGuestTrip` | (서버 저장 안 함) | 작성 중 임시 상태라 계속 `localStorage` 사용 |

설계 시 결정해 둔 점

- **ID 타입**: 프론트는 `"demo-1"`, UUID, `Date.now()`를 섞어 쓴다. 서버는 전부 `BIGINT AUTO_INCREMENT`로 통일하고, 프론트 연동 단계에서 서버 ID를 그대로 쓰도록 바꾼다.
- **비정규화 필드 제거**: 프론트는 글·댓글·참가자마다 `nickname`, `photo`를 복사해 두고 `syncUserProfile()`로 맞춘다. 서버는 `owner_id`만 저장하고 조회할 때 `JOIN members`로 채운다. 이 동기화 코드가 필요 없어진다.
- **동행 일정**: 프론트는 모집글 안에 일정을 복사(`schedule`, `places`)한다. 서버는 `companions.plan_id`로 원본 계획을 참조한다. 모집 후 계획을 고치면 모집글에도 반영된다.
- **일정 속 장소**: TourAPI에서 실시간으로 가져온 장소는 `attractions` 덤프에 없을 수 있다. `plan_items`에 `content_id`(nullable) + 제목·좌표·이미지 스냅샷을 함께 저장한다.
- **정원 `people`**: 프론트는 `"1/4명"` 문자열이다. 서버는 `capacity INT`만 저장하고 현재 인원은 참가자 수를 센다.
- **회원 탈퇴**: 프론트 `clearUserContent()`와 같은 결과가 나오도록 FK `ON DELETE CASCADE`로 처리한다. 대댓글이 달린 댓글은 내용만 지우는(soft delete) 현재 규칙을 유지한다.

## 5. API 명세

공통 규칙

- 기본 경로 `/{context}/api`. 요청·응답 본문은 `application/json; charset=UTF-8`.
- 성공: 데이터를 그대로 반환. 생성은 `201`, 본문 없는 성공은 `204`.
- 목록: `{"items": [...], "page": 1, "size": 20, "total": 134}`
- 실패: `{"code": "NOT_FOUND", "message": "게시글을 찾을 수 없어요."}`

| 상태 | code | 상황 |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | 입력값 검증 실패, JSON 형식 오류 |
| 401 | `UNAUTHORIZED` | 로그인 필요, 로그인 실패 |
| 403 | `FORBIDDEN` | 작성자·관리자가 아님 |
| 404 | `NOT_FOUND` | 대상 없음, 지원하지 않는 경로 |
| 405 | `METHOD_NOT_ALLOWED` | 경로는 있으나 메서드가 다름 |
| 409 | `CONFLICT` | 이메일·닉네임 중복, 정원 초과 |
| 500 | `INTERNAL_ERROR` | 서버 오류 (상세 내용은 서버 로그에만) |

🔒 = 로그인 필요, 👑 = 관리자(`role = admin`)

### 회원·인증 (F107, F108) — 구현 완료

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/members` | 회원가입 |
| GET | `/api/members/check-email?email=` | 이메일 사용 가능 여부 `{available}` |
| GET | `/api/members/check-nickname?nickname=` | 닉네임 사용 가능 여부 |
| GET | `/api/members/me` 🔒 | 내 정보 조회 |
| PUT | `/api/members/me` 🔒 | 내 정보 수정 (이름·닉네임·성별·사진·소개) |
| PUT | `/api/members/me/password` 🔒 | 비밀번호 변경 (현재 비밀번호 확인) |
| DELETE | `/api/members/me` 🔒 | 회원 탈퇴 (비밀번호 확인 후 세션 종료) |
| GET | `/api/members/{id}` | 다른 회원 공개 프로필 (닉네임·사진·소개) |
| POST | `/api/auth/login` | 로그인 → 세션 생성 |
| POST | `/api/auth/logout` | 로그아웃 → 세션 종료 |
| GET | `/api/auth/me` | 현재 로그인 상태 `{loggedIn, member}` |
| POST | `/api/auth/password-reset` | 비밀번호 찾기: 이메일·이름·생년월일 일치 시 새 비밀번호 설정 |

검증 규칙은 프론트(`signup.js`)와 같다: 비밀번호 영문+숫자 8자 이상, 만 14세 이상, 이메일·닉네임 중복 불가.
비밀번호 찾기는 메일 서버가 없어 본인 정보 확인 방식이다(프론트와 동일). 실제 서비스라면 메일 인증 토큰으로 바꿔야 한다.

### 관광지 (F101 ~ F103)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/areas` | 시도 목록 |
| GET | `/api/areas/{sidoCode}/guguns` | 구군 목록 |
| GET | `/api/content-types` | 관광지·숙박·음식점·문화시설·공연·코스·쇼핑 등 |
| GET | `/api/attractions?areaCode&sigunguCode&contentTypeId&keyword&page&size` | 지역·콘텐츠별 조회 |
| GET | `/api/attractions/nearby?lat&lng&radius&contentTypeId` | 지도 주변 조회 (현재 `locationBasedList2` 대체) |
| GET | `/api/attractions/{contentId}` | 상세 |

### 여행계획 (F104)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/plans` 🔒 | 내 계획 목록 |
| POST | `/api/plans` 🔒 | 계획 저장 (계획 + 일정 항목을 한 트랜잭션으로) |
| GET | `/api/plans/{id}` 🔒 | 상세 (본인 또는 공유된 동행 참가자) |
| PUT | `/api/plans/{id}` 🔒 | 수정 (일정 항목 전체 교체) |
| DELETE | `/api/plans/{id}` 🔒 | 삭제 |
| GET / POST / DELETE | `/api/favorites`, `/api/favorites/{contentId}` 🔒 | 찜 목록·추가·해제 |

### 동행 (팀 확장 기능)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/companions?region&theme&status&page` | 모집글 목록 |
| POST | `/api/companions` 🔒 | 내 계획(`planId`)으로 모집글 등록 |
| GET | `/api/companions/{id}` | 상세 (일정·참가자 포함) |
| PUT / DELETE | `/api/companions/{id}` 🔒 | 작성자만 수정·삭제 |
| POST | `/api/companions/{id}/close` 🔒 | 모집 마감 |
| POST | `/api/companions/{id}/participants` 🔒 | 참가 (정원 초과 시 409) |
| DELETE | `/api/companions/{id}/participants/me` 🔒 | 참가 취소 |

### 커뮤니티 (F109, F110)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/posts?board&category&keyword&sort=latest\|views\|comments&page` | 목록 |
| POST | `/api/posts` 🔒 | 작성 (`board=notice`는 👑) |
| GET | `/api/posts/{id}` | 상세 (조회수 +1) |
| PUT / DELETE | `/api/posts/{id}` 🔒 | 작성자·관리자만 |
| GET / POST | `/api/posts/{id}/comments` | 댓글 목록·작성 🔒 (`parentId`로 대댓글) |
| GET / POST | `/api/companions/{id}/comments` | 동행 댓글 목록·작성 🔒 |
| PUT / DELETE | `/api/comments/{id}` 🔒 | 수정·삭제 (대댓글 있으면 soft delete) |

### 핫플레이스 (F105)

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/hotplaces` | 목록 |
| POST | `/api/hotplaces` 🔒 | 등록 (`multipart/form-data` 사진 업로드) |
| GET / PUT / DELETE | `/api/hotplaces/{id}` | 상세·수정·삭제 🔒 |

### 외부 API

날씨(기상청), 전기차 충전소, 일출·일몰, 카카오 길찾기는 **1차로 지금처럼 프론트에서 직접 호출**한다. 키 노출이 문제되는 카카오 모빌리티 길찾기(`dev-server.js`의 `/api/kakao-directions` 프록시)만 백엔드 `ProxyServlet`으로 옮긴다. 시간이 남으면 날씨·충전소도 서버 캐시 프록시로 옮긴다.

## 6. 공통 처리 흐름 예시 — 로그인

```
POST /api/auth/login {"email","password"}
 → EncodingFilter, CorsFilter
 → AuthServlet (BaseApiServlet 라우터가 "POST /login" 매칭)
     req.body(LoginRequest.class)
 → MemberServiceImpl.login(request)
     Tx.read(conn -> memberDao.findByEmail(conn, email))
     PasswordHasher.matches(password, member.passwordHash)  실패 시 401
 → AuthServlet: 세션 고정 공격 방지를 위해 기존 세션 무효화 → 새 세션에 SessionUser 저장
 → 200 MemberResponse (password_hash 제외)
```

## 7. 구현 순서

1. **(완료) 공통 기반 + 회원·인증** — 다른 도메인이 따라 할 기준 코드.
2. **DB PJT: ERD·DDL 확정** — 4장 표를 기준으로 테이블 설계. 정식 DDL은 `db/`에 둔다.
3. 관광지 → 여행계획·찜 → 동행 → 게시판·댓글 → 핫플레이스 순으로 `member/`와 같은 구조로 구현.
4. 프론트 연동 — `js/shared.js`의 `loadState/saveState`를 API 호출 모듈(`js/server-api.js`)로 단계별 교체.
5. 산출물 — 개선된 요구사항, Usecase·Class Diagram, ERD, DDL, 실행 화면.

## 8. 실행 방법

1. MySQL에 회원 임시 테이블 생성: `docs/sql/members-draft.sql` 실행.
2. `src/main/resources/application.properties.example`을 `application.properties`로 복사하고 DB 계정 입력.
   (또는 환경변수 `MAPMATES_DB_URL`, `MAPMATES_DB_USER`, `MAPMATES_DB_PASSWORD`)
3. STS/IntelliJ에서 Maven 프로젝트로 Import → Tomcat 10.1 서버에 추가 → 실행.
4. `http://localhost:8080/mapmates/index.html` 접속, API는 `http://localhost:8080/mapmates/api/auth/me`.
