# 너랑 갈.지도 업무 분담

## 원칙

- 페이지 단위로 담당 파일을 나누고 상대 담당자의 HTML/JS를 직접 수정하지 않는다.
- 공통 저장 구조(`js/shared.js`)와 공통 스타일(`css/style.css`) 변경은 김강민이 관리하고, 변경 필요 시 먼저 이슈로 합의한다.
- 기능 브랜치는 `feature/기능명-이름` 형식으로 만들고 작은 단위로 커밋한다.
- 병합 순서는 공통 모듈 → 각 서브페이지 → 통합 테스트 순서로 진행한다.

## 김강민 — 지도·여행계획·API

- 담당 페이지: `index.html`, `planner.html`
- 담당 스크립트: `js/app.js`, `js/planner.js`, `js/api.js`
- 담당 서버: `dev-server.js`
- 지도 탐색, 관광지 필터, 날씨, 충전소, 핫플 레이어
- 여행지 일정 추가, 여행 시각·메모, 시각 자동 정렬
- 직선/카카오모빌리티 차량 경로 전환
- 일자별 단기예보와 추후 기상청 중기예보 연동
- 여행계획에서 동행 모집글 발행

## 마예은 — 회원·동행·커뮤니티

- 담당 페이지: `signup.html`, `mypage.html`, `companions.html`, `companion-detail.html`, `community.html`, `post-detail.html`, `post-write.html`
- 담당 스크립트: `js/signup.js`, `js/mypage.js`, `js/companions.js`, `js/companion-detail.js`, `js/community.js`, `js/post-detail.js`, `js/post-write.js`
- 회원가입과 프로필 사진 등록
- 닉네임·프로필 사진 수정, 비밀번호 변경, 회원탈퇴
- 동행 목록/상세, 참가자 프로필과 성별 테두리 표시
- 모집글 소유자 수정 권한
- 커뮤니티 목록/상세/글쓰기와 댓글 권한

## 공동 검토 항목

- `js/shared.js`: 사용자·여행계획·동행·게시글 저장 구조
- 반응형 화면과 접근성
- 페이지 간 링크와 로그인 상태 유지
- PR 병합 전 `node --check`, `git diff --check`, 브라우저 주요 흐름 확인
