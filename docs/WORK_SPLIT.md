# 너랑 갈.지도 업무 분담 — 백엔드·DB 단계

> 프론트엔드 단계의 분담은 [front/docs/WORK_SPLIT.md](../front/docs/WORK_SPLIT.md)에 있다.
> 이번 단계는 Web(Backend) PJT 5회차와 DB PJT 6회차를 함께 진행한다. 설계는 [back/docs/BACKEND_DESIGN.md](../back/docs/BACKEND_DESIGN.md)를 따른다.

## 원칙

- **도메인 단위로 끝까지 맡는다.** 한 사람이 맡은 도메인의 테이블 설계 → DAO → Service → Servlet → 프론트 연동까지 책임진다.
- 프론트 단계의 역할을 이어간다. 김강민은 지도·여행계획·동행, 마예은은 회원·커뮤니티·충전소를 맡는다.
- 새 도메인은 이미 구현된 `back/src/main/java/com/mapmates/member/`와 같은 `controller / service / dao / dto` 구조와 규칙을 따른다.
- 공통 코드(`back/.../common/`, `db/schema.sql`, `front/js/shared.js`) 변경은 김강민이 관리하고, 변경이 필요하면 먼저 합의한다.
- 기능 브랜치는 `feature/도메인-이름` 형식으로 만들고 작은 단위로 커밋한다.
- 병합 순서는 **ERD 확정 → 공통 연동 모듈 → 각 도메인 → 통합 테스트**다.

## 진행 현황

| 항목 | 상태 |
| --- | --- |
| 백엔드 설계 문서, Maven WAR 골격 | 완료 |
| 공통 기반 (설정, DB·트랜잭션, 라우터, 필터, 비밀번호 해시, 검증) | 완료 |
| 회원·인증 API (F107, F108) | 완료 — 프론트 연동 남음 |
| ERD·DDL, 나머지 도메인, 프론트 연동, 산출물 | 아래 분담대로 진행 |

## 1단계: 공동 — ERD 확정 전

| 작업 | 산출물 | 담당 |
| --- | --- | --- |
| 개선된 요구사항 목록 (F101~F114 + 동행 등 추가 기능) | 요구사항 표 | 공동 |
| 각자 맡은 도메인의 테이블 초안 작성 → 하나의 ERD로 합치기 | ERD, `db/schema.sql` | 공동 (병합은 김강민) |
| 생성형 AI로 스키마 검증하고 개선점 반영 (DB 명세 요구사항) | 검증 기록 | 공동 |

## 2단계: 도메인별 구현

| 영역 | 요구사항 | 테이블 | 백엔드 (DAO·Service·Servlet) | 프론트 연동 | 담당 |
| --- | --- | --- | --- | --- | --- |
| 관광지 조회 | F101~F103 | `attractions`, `sidos`, `guguns`, `contenttypes` | 지역·콘텐츠별 조회, 주변 조회 | `app.js`, `api.js` | 김강민 |
| 여행계획·찜 | F104 | `plans`, `plan_items`, `favorites` | 계획과 일정을 한 트랜잭션으로 저장 | `planner.js`, `map-workspace.js` | 김강민 |
| 동행 | 추가 기능 | `companions`, `companion_participants` | 모집, 참가·취소, 정원 확인, 마감 | `companions.js`, `companion-detail.js` | 김강민 |
| 길찾기 프록시 | 추가 기능 | – | `dev-server.js` 프록시를 `ProxyServlet`으로 이전 | `route-*.js` | 김강민 |
| 회원 프론트 연동 | F107, F108 | `members` (구현 완료) | 완료 | `signup.js`, `mypage.js`, `account.js` | 마예은 |
| 게시판·공지사항 | F109, F110 | `posts` | 목록·검색·정렬, 조회수, 공지는 관리자만 작성 | `community.js`, `post-*.js` | 마예은 |
| 댓글·대댓글 | F110 | `comments` | 게시글·동행 댓글, 대댓글이 있으면 내용만 삭제(soft delete) | `comments.js` | 마예은 |
| 핫플레이스 | F105 | `hotplaces` | 사진 업로드(multipart) | 핫플레이스 화면 | 마예은 |
| 전기차 충전소 | F114 | (선택) 캐시 테이블 | (선택) 서버 캐시 프록시 | 기존 유지 | 마예은 |

## 3단계: 연동·마무리

| 작업 | 담당 |
| --- | --- |
| `shared.js`의 `loadState`/`saveState`를 API 호출 모듈로 바꾸는 공통 틀 (마예은의 연동 작업보다 먼저) | 김강민 |
| Usecase Diagram, Class Diagram | 김강민: 여행·동행 / 마예은: 회원·커뮤니티 |
| 더미 데이터 스크립트 (프론트 데모 데이터를 SQL로 변환: 회원 10, 게시글 50, 계획 40, 동행 40) | 각자 맡은 테이블 |
| README, 실행 화면 캡처 | 공동 |

## 김강민 — 관광지·여행계획·동행·공통 연동

- 담당 패키지: `back/.../attraction`, `back/.../plan`, `back/.../companion`, `common/` 관리
- 담당 테이블: `attractions`·`sidos`·`guguns`·`contenttypes`(SSAFY_TRIP 재사용), `plans`, `plan_items`, `favorites`, `companions`, `companion_participants`
- ERD 병합과 `db/schema.sql` 관리
- 프론트 API 호출 공통 모듈과 `js/shared.js` 전환
- 카카오 길찾기 프록시를 백엔드로 이전

## 마예은 — 회원 연동·커뮤니티·핫플레이스·충전소

- 담당 패키지: `back/.../board`, `back/.../comment`, `back/.../hotplace`
- 담당 테이블: `posts`, `comments`, `hotplaces`, (선택) 충전소 캐시 테이블
- 구현 완료된 회원·인증 API를 `signup.js`, `mypage.js`, `account.js`에 연결
- 공지사항 관리자 권한, 게시글 조회수·정렬, 댓글·대댓글 soft delete
- 핫플레이스 사진 업로드와 저장 경로 관리

## 의존 관계와 주의점

- **동행 댓글:** `comments`가 김강민의 `companions`를 참조한다. 그래서 1단계에서 ERD를 함께 확정한 뒤 구현을 시작한다.
- **프론트 연동 순서:** 김강민의 API 호출 공통 모듈이 먼저 병합되어야 마예은이 같은 방식으로 회원·게시판을 연결할 수 있다.
- **회원 탈퇴:** 작성자 FK는 `ON DELETE CASCADE`로 맞춰 탈퇴 시 데이터가 함께 정리되게 한다.

## 공동 검토 항목

- `db/schema.sql`: PK·FK·INDEX·DEFAULT 설정과 정규화
- `back/.../common/`: 라우터, 트랜잭션, 오류 응답 형식
- 로그인 상태 유지(세션)와 작성자·관리자 권한 확인
- PR 병합 전 Maven 빌드, Tomcat 실행 후 주요 API 호출, 브라우저 주요 흐름 확인

## 2026-11-08 통합 완료

이 문서의 단계별 표는 초기 업무 분담 기록입니다. 최종 구현은 [설계](DESIGN.md), [검증](VALIDATION.md)을 따릅니다. GitLab의 마예은 스키마 보완 커밋 f778489를 보존해 통합했습니다.
