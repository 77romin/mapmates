# 너랑 갈.지도 (mapmates) — Frontend

<img src="public/logo.webp" alt="너랑 갈.지도 로고" width="280" />

> 너랑 갈 다음 여행을 지도 위에 · Find your route. Meet your mates.

Vue 3 + Vite + Pinia + Bootstrap 5 + Kakao Maps 기반 SPA다.
화면 정의서와 와이어프레임은 `allfiles` 저장소의 `docs/07_화면정의서.md`에 있다.

## 실행 방법

```bash
npm install
cp .env.example .env.local     # VITE_KAKAO_MAP_KEY 입력
npm run dev                    # http://localhost:5173
```

- 카카오 개발자 콘솔 > 앱 > 플랫폼 > Web 에 `http://localhost:5173` 을 등록해야 지도가 표시된다.
- 백엔드(`back`, 8080)와 AI 서버(`allfiles/ai-server`, 8000)가 실행 중이어야 한다. 주소는 `.env.local`의 `VITE_API_BASE`, `VITE_AI_SERVER`로 바꿀 수 있다.

## 폴더 구조

```
src/
├── api/http.js              axios 인스턴스 (토큰 첨부, 오류 메시지 통일)
├── stores/
│   ├── auth.js              로그인 상태 (토큰·회원 정보)
│   └── draft.js             담은 장소 (로컬 스토리지 보관)
├── router/index.js          라우트, 로그인 필요 화면 가드
├── components/
│   ├── NavBar.vue           상단 메뉴 (반응형)
│   ├── KakaoMap.vue         지도 (사진 핀, 번호 핀, 경로선, 클릭 위치 선택)
│   ├── AttractionDetail.vue 관광지 상세 패널
│   ├── AttractionPopup.vue  장소 정보 팝업 (공연장 주변 장소 보기·담기)
│   └── ChatWidget.vue       AI 길잡이 (여행 도우미 / 문서 Q&A)
└── views/
    ├── ExploreView.vue      지도 탐색
    ├── PlanListView.vue     내 플랜
    ├── PlanEditView.vue     플랜 보드
    ├── MatesView.vue        메이트
    ├── CultureView.vue      공연·행사
    ├── HotplaceView.vue     핫플레이스
    ├── BoardView.vue        공지사항·공유 게시판
    ├── NewsView.vue         여행 뉴스
    └── Login / Join / FindPassword / MyPage
```

## 화면과 기능

| 화면 | 기능 |
|---|---|
| 지도 탐색 | 시도·구군·유형 필터, 검색, 목록·지도 핀 연동, 담기 |
| 관광지 상세 | 사진, 소개, 날씨, 일출·일몰, 주변 축제·상권, 전기차 충전소 |
| 플랜 보드 | 일자 탭, 드래그앤드롭 순서 변경, 경비·예산, 동선 최적화, 이동 수단별 경로(직선·자동차·도보·대중교통), 중간 집결지 |
| 메이트 | 모집 카드(취향 일치 %), 상세, 참여 신청, 수락·거절, 모집 작성 |
| 공연·행사 | 전국 공연(KOPIS)·서울시 문화행사 목록, 상세(시설 정보·지도), 주변 장소 정보 팝업에서 담기 |
| 핫플레이스 | 지도 클릭으로 위치 선택, 사진 업로드·미리보기, 지도 핀 |
| 커뮤니티 | 공지·게시판 목록·상세·작성·수정·삭제, 검색, 댓글 |
| 여행 뉴스 | 키워드별 뉴스 목록 |
| 회원 | 로그인(아이디 저장), 회원가입, 비밀번호 찾기, 마이페이지(취향 태그, 출발지, 탈퇴) |
| AI 길잡이 | 여행 도우미(사용한 도구 표시, 추천 장소 담기), 문서 Q&A(업로드, 문서 참고 여부·출처) |

## 구현 포인트

- **반응형**: Bootstrap 그리드와 미디어 쿼리. 768px 미만에서는 지도가 위, 패널이 아래로 배치된다.
- **폼 유효성 검사**: 회원가입은 항목별 실시간 검증(`is-valid` / `is-invalid`), 플랜·핫플·게시글은 제출 시 검증하고 사유를 표시한다.
- **로컬 스토리지**: 담은 장소(`mm_draft`), 로그인 토큰, 아이디 저장.
- **동적 DOM·지도**: Kakao Maps CustomOverlay로 핀을 그리고 선택·번호·경로선을 상태에 따라 갱신한다.
- **fetch + async/await**: 문서 Q&A와 업로드는 fetch로 FastAPI 서버를 직접 호출하고, 네트워크 오류와 서버 오류를 구분해 안내한다.
- **상태 표시**: 모든 목록에 로딩·빈 상태·오류 화면과 다음 행동 버튼이 있다.
- **접근성**: 라벨 연결, 아이콘 버튼 `aria-label`, 포커스 링, 상태 메시지 `role`.
- **사진 없는 장소**: 유형별 아이콘(음식점, 숙박, 관광지 등)으로 자리를 채우고, 상세·팝업을 열어 서버가 사진을 찾으면 목록 썸네일도 함께 갱신한다.
- **디자인 토큰과 모션**: 기준 문서는 [`DESIGN.md`](DESIGN.md), 값은 `src/style.css`의 `:root` 변수. 모션은 순차 등장·줄어드는 헤더·메뉴 밑줄 3종(`src/motion.js`, `src/motion.css`)이며 [모션 UI 메뉴판](https://motion-ui.altolab.ai/)의 데모 코드를 바탕으로 했다.
- **보안**: 서버에서 온 HTML(관광지 소개)은 태그를 제거해 텍스트로만 출력하고, 외부 링크는 http(s)만 허용한다.
