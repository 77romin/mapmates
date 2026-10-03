# 너랑 갈.지도 업무 분담

## 원칙

- 페이지 단위로 담당 파일을 나누고 상대 담당자의 HTML/JS를 직접 수정하지 않는다.
- 공통 저장 구조(`js/shared.js`)와 공통 스타일(`css/style.css`) 변경은 김강민이 관리하고, 변경 필요 시 먼저 이슈로 합의한다.
- 기능 브랜치는 `feature/기능명-이름` 형식으로 만들고 작은 단위로 커밋한다.
- 병합 순서는 공통 모듈 → 각 서브페이지 → 통합 테스트 순서로 진행한다.

## 김강민 — 기획·지도·내 여행·외부 API 통합·동행

- 기획: 서비스 구성과 사용자 흐름 설계
- 담당 페이지: `index.html`, `planner.html`, `companions.html`, `companion-detail.html`
- 담당 스크립트: `js/app.js`, `js/map-workspace.js`, `js/planner.js`, `js/trip-map.js`, `js/companions.js`, `js/companion-detail.js`, `js/companion-utils.js`
- 외부 API 통합: `js/api.js`의 카카오맵·공공데이터 관광정보 API·날씨·차량 경로 연동, `dev-server.js`의 개발 서버와 차량 경로 프록시
- 지도 탐색과 주변 여행지 검색, 여행지 필터, 날씨, 여행계획 기반 핫플 표시
- 내 여행 카드 목록, 날짜별 장소 추가, 방문 시각·메모 편집과 일정 자동 정렬
- 직선/차량 경로 전환, 일자별 단기·중기예보 확인
- 공통 상태 관리(`js/shared.js`)와 통합 테스트
- 동행 목록·상세, 참가자 현황과 참가·취소 권한, 계획 공유와 모집 마감
- 서브페이지 반응형 화면과 접근성

## 마예은 — 회원·커뮤니티·전기차 충전소

- 담당 페이지: `signup.html`, `mypage.html`, `community.html`, `post-detail.html`, `post-write.html`
- 담당 스크립트: `js/signup.js`, `js/mypage.js`, `js/account.js`, `js/community.js`, `js/board.js`, `js/post-detail.js`, `js/post-write.js`, `js/comments.js`
- 회원가입, 로그인, 프로필 사진 등록
- 닉네임·프로필 관리, 비밀번호 변경과 회원탈퇴
- 커뮤니티 게시글 목록·작성·수정·삭제와 작성자 권한
- 댓글·대댓글 작성·수정·삭제 구현, 동행 상세 질문과 답변의 공통 댓글 기능
- 전기차 충전소 API 연동과 지역별 조회, 캐시, 충전소 마커와 충전 가능 대수·충전기 상태 표시
- 충전소 관련 구현은 `js/api.js`와 `js/app.js`에서 담당 기능 범위에 따라 관리

## 공동 검토 항목

- `js/shared.js`: 사용자·여행계획·동행·게시글 저장 구조
- 반응형 화면과 접근성
- 페이지 간 링크와 로그인 상태 유지
- PR 병합 전 `node --check`, `git diff --check`, 브라우저 주요 흐름 확인
