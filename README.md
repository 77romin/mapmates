<p align="center">
  <img src="./front/assets/mapMates-logo-v1.png" width="140" height="140" alt="MapMates · 너랑 갈.지도 logo">
</p>

# 너랑 갈.지도 · MapMates — 백엔드·DB 프로젝트

> 3팀 김강민 · 마예은 | SSAFY EnjoyTrip 관통 프로젝트 (Web Backend 5회차 + DB 6회차)

**MapMates**는 지도에서 여행지를 찾고, 일정을 만들고, 함께 갈 동행을 모으는 여행 웹앱이에요.
이전 단계에서 만든 프론트엔드는 데이터를 브라우저 `localStorage`에 저장했어요. 이번 단계에서는 **Servlet MVC 백엔드와 MySQL 데이터베이스**를 붙여 여러 사용자가 데이터를 함께 쓰는 서비스로 바꿔요.

## 저장소 구조

| 경로 | 내용 |
| --- | --- |
| [`front/`](./front) | 프론트엔드 (HTML·CSS·JS). 기존 [77romin/mapmates](https://github.com/77romin/mapmates) 커밋 히스토리를 그대로 가져왔어요. 기능 소개는 [front/README.md](./front/README.md) |
| [`back/`](./back) | 백엔드 (Java 17+, Jakarta Servlet 6.0, Tomcat 10.1, JDBC). 설계는 [back/docs/BACKEND_DESIGN.md](./back/docs/BACKEND_DESIGN.md) |
| [`db/`](./db) | DB 설계 (ERD, DDL, 더미 데이터). `db/ref/`에 SSAFY_TRIP 스키마·관광지 덤프 |
| [`docs/`](./docs) | 팀 문서. 업무 분담은 [docs/WORK_SPLIT.md](./docs/WORK_SPLIT.md) |

## 아키텍처

```
front (정적 HTML/JS) ──fetch JSON──▶ Servlet(Controller) ─▶ Service ─▶ DAO ─▶ MySQL
```

- 프론트 화면은 그대로 두고 **JSON API**를 붙여요. Maven 빌드 시 `front/`가 WAR에 함께 들어가 API와 같은 주소에서 열려요.
- Service가 트랜잭션을 열고 DAO에 Connection을 넘겨요. DAO는 SQL만 담당해요.
- 로그인은 `HttpSession`을 쓰고, 비밀번호는 PBKDF2로 해시해서 저장해요.

## 진행 현황

| 영역 | 요구사항 | 상태 | 담당 |
| --- | --- | --- | --- |
| 백엔드 설계·공통 기반 | – | ✅ 완료 | 김강민 |
| 회원·인증 API | F107, F108 | ✅ 백엔드 완료 / 프론트 연동 예정 | 마예은 (연동) |
| ERD·DDL | DB PJT | ⏳ 진행 예정 | 공동 |
| 관광지 조회 | F101~F103 | ⏳ | 김강민 |
| 여행계획·찜 | F104 | ⏳ | 김강민 |
| 동행 모집·참가 | 추가 기능 | ⏳ | 김강민 |
| 게시판·공지사항·댓글 | F109, F110 | ⏳ | 마예은 |
| 핫플레이스 | F105 | ⏳ | 마예은 |
| 전기차 충전소 | F114 | 프론트 직접 호출 유지 (선택: 서버 캐시) | 마예은 |

자세한 단계별 분담과 의존 관계는 [업무 분담 문서](./docs/WORK_SPLIT.md)를 참고하세요.

## 팀

| GitHub 프로필 사진 | 이름 | 이번 단계 담당 |
| --- | --- | --- |
| <a href="https://github.com/77romin"><img src="https://github.com/77romin.png?size=160" width="80" height="80" alt="김강민 GitHub 프로필 사진"></a> | [김강민](https://github.com/77romin) | **관광지·여행계획·동행·공통 연동** — 백엔드 공통 기반, ERD 병합, 관광지·여행계획·찜·동행 API, 프론트 API 연동 공통 모듈, 길찾기 프록시 |
| <a href="https://github.com/yeeunma"><img src="https://github.com/yeeunma.png?size=160" width="80" height="80" alt="마예은 GitHub 프로필 사진"></a> | [마예은](https://github.com/yeeunma) | **회원 연동·커뮤니티·핫플레이스·충전소** — 회원 API 프론트 연동, 게시판·공지사항·댓글 API, 핫플레이스 사진 업로드, 충전소 |

## 실행 방법

1. MySQL에 회원 임시 테이블 생성: [`back/docs/sql/members-draft.sql`](./back/docs/sql/members-draft.sql) (정식 스키마는 `db/`에 작성 예정)
2. `back/src/main/resources/application.properties.example`을 `application.properties`로 복사하고 DB 계정 입력
3. STS 또는 IntelliJ에서 `back/`을 Maven 프로젝트로 Import → Tomcat 10.1에 추가 → 실행
4. `http://localhost:8080/mapmates/index.html` 접속 (API 확인: `/mapmates/api/auth/me`)

## 산출물

| 산출물 | 위치 |
| --- | --- |
| 개선된 요구사항 목록 | 작성 예정 |
| Usecase Diagram, Class Diagram | 작성 예정 |
| ER 다이어그램, DB Schema (DDL) | `db/` (작성 예정) |
| 소스코드, 실행 화면 캡처 | `back/`, `front/`, 캡처는 작성 예정 |
