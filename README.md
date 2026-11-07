# MapMates · 너랑 갈.지도

지도에서 관광지를 발견하고 여행계획을 저장한 뒤 함께할 동행을 모집하는 서비스입니다. 기존 프론트에 **Servlet MVC + JDBC + MySQL**을 연결했습니다. 회원·여행계획·찜·동행·게시글·댓글은 서버에 저장되어 다른 브라우저에서도 공유됩니다.

- 기록용 GitHub: https://github.com/77romin/mapmates
- 제출용 GitLab: https://lab.ssafy.com/s16/a11/project/6.database/3-kkm-mye
- 프론트 화면 안내: [한국어](front/README.md) · [English](front/README-en.md)
- [요구사항·Usecase·Class Diagram·ERD](docs/DESIGN.md)
- [DB 스키마](db/schema.sql) · [초기 데이터](db/seed.sql) · [AI 검증 기록](db/AI_REVIEW.md)
- [검증 결과와 실행 화면](docs/VALIDATION.md)

## 실행

Docker Desktop 실행 후 저장소 루트에서:

```sh
docker compose up -d --build
```

http://localhost:8080/mapmates/index.html 에 접속합니다. 첫 빌드는 이미지·Maven 의존성을 다운로드하며 DB 초기화에는 관광지 50,915행을 적재합니다. 기존 MySQL이 필요하지 않고 호스트에 DB 포트를 공개하지 않습니다. 데이터는 Docker 볼륨에 보존됩니다. 종료는 `docker compose down`으로 합니다.

시연 계정은 `traveler1@mapmates.test` ~ `traveler10@mapmates.test`, 비밀번호는 `travel1234`입니다. 10번은 운영자입니다. 초기 데이터는 회원 10명, 계획·동행 각 1개, 게시글 2개, 댓글 1개입니다. 프론트 단독 실행의 체험 데이터와 별개입니다.

## 카카오맵과 외부 API

루트 `.env`에 로컬 키를 넣습니다. `.env`는 커밋하지 않습니다.

```dotenv
MAPMATES_KAKAO_JS_KEY=발급받은_JavaScript_키
MAPMATES_KAKAO_REST_KEY=발급받은_모빌리티_REST_키
MAPMATES_WEATHER_API_KEY=기상청_키
MAPMATES_EV_CHARGER_API_KEY=충전소_키
```

설정 변경 후 `docker compose up -d web`으로 반영합니다. 카카오 JavaScript SDK 도메인에는 **`http://localhost:8080`**, **`http://127.0.0.1:8080`**을 등록합니다. `https://`와 `http://`는 다르며 `/mapmates` 경로는 도메인 등록에 넣지 않습니다. 카카오맵 서비스 활성화도 필요합니다.

`/mapmates/config.js`는 브라우저용 설정만 제공합니다. 모빌리티 REST 키는 서버에서만 사용합니다. 관광지 검색은 동봉한 관광정보 DB를 사용하므로 TourAPI 키 없이 동작합니다. 지도·날씨·충전 현황 등 외부 서비스는 해당 키와 API 사용 권한이 필요합니다.

## 주요 기능

- 지역·유형·검색어·주변 관광지 조회와 찜
- 회원가입·로그인·프로필·비밀번호 변경·탈퇴
- 1~14일 여행계획, 방문 시각·메모·예산, 일정 저장·수정·삭제
- 계획 공유와 참가·취소·정원 검사·모집 마감 및 재개
- 여행 게시판·공지·뉴스, 댓글·대댓글, 작성자·관리자 권한
- 전체 회원 계획의 지도 범위별 인기 장소 TOP 10
- 기존 동선 최적화, 지도 비교, 날씨·충전소·로드뷰 화면

필수 요구사항은 관광지 조회(F101~F103), 회원·인증(F107~F108)입니다. 선택 기능의 사진 등록형 핫플은 현재 서비스의 계획 기반 인기 장소로 변경했습니다. 뉴스 자동 크롤링은 제외하며 운영자가 직접 뉴스 게시글을 작성합니다.

## 구조와 수동 실행

```text
back/       Java 17 · Servlet 6 · JDBC · Maven WAR
front/      기존 HTML/CSS/JavaScript 화면과 단독 데모
db/         13개 테이블 DDL · 관광정보 덤프 · 초기 데이터 · 검증
docs/       설계 · 검증 · 화면 캡처
scripts/    HTTP·브라우저 통합 테스트
```

Docker 없이 실행하려면 새 MySQL 8 DB에 `db/schema.sql`, `USE ssafy_trip;`를 `USE mapmates;`로 바꾼 관광지 덤프, `db/init/03-clean.sql`, `db/seed.sql` 순으로 적재합니다. **schema.sql은 새 DB 구축용이며 기존 서비스 DB에 재실행하지 않습니다.**

환경변수 `MAPMATES_DB_URL`, `MAPMATES_DB_USER`, `MAPMATES_DB_PASSWORD`를 설정하거나 `back/src/main/resources/application.properties.example`을 복사해 설정합니다. Java 17 이상과 Maven으로 `mvn -f back/pom.xml verify` 후 `back/target/mapmates.war`를 Tomcat 10.1에 배포합니다.

프론트만 확인하려면 `node front/dev-server.js`를 사용합니다. 이 경우 브라우저 데모 모드이며 서버에 데이터를 저장하지 않습니다. 서버 모드는 DB 장애를 데모 데이터로 숨기지 않습니다.

## 검증

```sh
python3 scripts/test_api.py
# 별도 Playwright 설치 필요. 기존 모듈은 PLAYWRIGHT_MODULE로 지정 가능
node scripts/test_browser.cjs
node scripts/test_map.cjs
node front/tests/algorithms.cjs
```

API 테스트는 별도 검증 계정을 만들고 종료 시 삭제합니다. 브라우저 테스트는 시연 계정으로 생성한 계획·게시글을 정리합니다. 기존 DB 검증은 별도 `mapmates_erd_check` 스키마에서 실행합니다.
