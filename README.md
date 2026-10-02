# 너랑 갈.지도

지도에서 여행지를 찾고, 일정을 만들고, 함께 갈 동행을 만나는 지도 기반 여행 서비스입니다.

팀원: 김강민, 마예은

## 주요 기능

- 한국관광공사 TourAPI 기반 관광지·숙박·음식점·문화시설·여행코스 검색
- 지도 마커, 카테고리 필터, 장소 상세정보와 찜 기능
- 여행 장소 추가, 순서 변경, 경로·거리·시간 요약, 일정 저장
- 기상청 단기예보, 일출·일몰, 전기차 충전소 정보
- 핫플레이스 등록 및 개인 여행 기록
- 회원 프로필과 로컬 로그인 데이터 구조
- 공지사항·여행정보 게시판 CRUD
- 여행 지역·테마·모집 상태 기반 동행 모집 및 신청
- PC·태블릿·모바일 반응형 UI

API 키가 없는 경우에도 프로젝트 전체를 확인할 수 있도록 목업 데이터가 자동으로 표시됩니다.

## 실행 방법

ES Module을 사용하므로 파일을 직접 더블클릭하지 말고 로컬 웹 서버로 실행해야 합니다.

VS Code의 Live Server 확장을 사용하거나, 별도 패키지 설치 없이 다음 명령을 실행합니다.

```bash
node dev-server.js
```

브라우저에서 `http://localhost:5500`을 엽니다.

## API 설정

1. `config.example.js`를 복사해 `config.js`를 생성합니다.
2. 발급받은 키를 해당 항목에 입력합니다.
3. `config.js`는 `.gitignore`에 포함되어 있으므로 저장소에 커밋되지 않습니다.

```js
window.APP_CONFIG = {
  KAKAO_JS_KEY: "",
  KAKAO_REST_KEY: "",
  TOUR_API_KEY: "",
  WEATHER_API_KEY: "",
  EV_CHARGER_API_KEY: "",
  SGIS_CONSUMER_KEY: "",
  SGIS_CONSUMER_SECRET: "",
  API_PROXY_URL: "",
};
```

카카오 JavaScript 키에는 `http://localhost:5500` 도메인을 등록해야 합니다. REST 키와 SGIS Secret은 공개 저장소나 운영 웹 브라우저 코드에 직접 포함하지 말고 JavaScript 기반 프록시를 통해 호출하는 것을 권장합니다.

## 데이터 출처

- 한국관광공사 국문 관광정보 서비스_GW
- SGIS 데이터 OpenAPI
- Kakao Maps JavaScript API 및 Kakao Mobility
- 기상청 단기예보 조회서비스
- 한국환경공단 전기자동차 충전소 정보
- Sunrise-Sunset.org

## 저장 방식

현재 과제용 프론트엔드 버전은 여행 일정, 찜, 동행 신청, 게시글, 핫플레이스, 프로필을 브라우저 `localStorage`에 저장합니다. 브라우저 저장소를 초기화하면 데이터가 기본 상태로 돌아갑니다.

실제 서비스로 확장할 때는 Supabase 또는 별도 서버에서 회원 인증과 데이터베이스를 처리해야 합니다.

## 업무 분담 제안

### 김강민

- 공통 애플리케이션 구조와 상태 관리
- 카카오맵, TourAPI, 날씨·충전소 API 어댑터
- 여행 일정, 경로 계산, 로컬 저장 기능
- 통합 및 API 오류 테스트

### 마예은

- 디자인 시스템과 반응형 UI
- 장소 카드·상세 패널·회원 화면
- 커뮤니티·동행 찾기·핫플레이스 UI
- 접근성 및 사용성 테스트

기능 브랜치 병합 전에는 작성하지 않은 영역을 상대 팀원이 교차 검토합니다.

## 디렉터리

```text
.
├─ index.html
├─ config.example.js
├─ css/
│  └─ style.css
└─ js/
   ├─ api.js
   ├─ app.js
   └─ data.js
```
