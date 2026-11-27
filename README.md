# 너랑 갈.지도 (mapmates) — 산출물

> 너랑 갈 다음 여행을 지도 위에 · Find your route. Meet your mates.

지도 한 장 위에서 경로를 짜고 동행(메이트)을 만나는 여행 플래너다. 이 저장소에는 설계 산출물, AI 서버, 발표 자료를 둔다.

| 저장소 | 내용 |
|---|---|
| `back` | Spring Boot 4 + MyBatis + Spring AI 백엔드 |
| `front` | Vue 3 + Bootstrap 5 프론트엔드 |
| `allfiles` (여기) | 산출물 문서, DDL, FastAPI RAG 서버, 발표 자료 |

## 산출물 목록

| 구분 | 파일 |
|---|---|
| 요구사항 정의서 | [docs/01_요구사항정의서.md](docs/01_요구사항정의서.md) |
| Usecase Diagram | [docs/02_유스케이스_다이어그램.md](docs/02_유스케이스_다이어그램.md) |
| WBS | [docs/03_WBS.md](docs/03_WBS.md) |
| ERD | [docs/04_ERD.md](docs/04_ERD.md) · DDL [sql/schema.sql](sql/schema.sql) · 초기 데이터 [sql/data.sql](sql/data.sql) |
| API 설계서 | [docs/05_API설계서.md](docs/05_API설계서.md) |
| Class Diagram | [docs/06_클래스_다이어그램.md](docs/06_클래스_다이어그램.md) |
| 화면 정의서 | [docs/07_화면정의서.md](docs/07_화면정의서.md) · 와이어프레임 [docs/wireframes](docs/wireframes) |
| 알고리즘 적용 기획서 | [docs/08_알고리즘_적용_기획서.md](docs/08_알고리즘_적용_기획서.md) |
| AI 고찰 보고서 (RAG, Spring AI) | [docs/09_AI_고찰보고서.md](docs/09_AI_고찰보고서.md) |
| 발표 자료 | [presentation/너랑갈지도_발표.pptx](presentation/너랑갈지도_발표.pptx) |
| 시연 영상 | `presentation/` 에 추가 예정 |
| AI 서버 | [ai-server](ai-server) |

다이어그램은 Mermaid로 작성해 GitLab에서 바로 렌더링된다.

## 명세별 충족 현황

| 명세 | 핵심 요구 | 반영 위치 |
|---|---|---|
| 01 Java | 관광지 목록·조건 검색·상세, XML 파싱, 축제·상권 | 지도 탐색·상세 화면, `NewsService`(RSS XML 파싱), 주변 축제·상권 탭 |
| 02 ChatBot | 챗봇 UI, fetch, 문서 업로드, 벡터 검색, RAG, 고찰 | `front` ChatWidget(문서 Q&A), `ai-server`, 고찰 보고서 1부 |
| 03 Front | 관광지 조회, 회원, 계획, 핫플, 게시판, 반응형, 폼 검증, 로컬 스토리지 | `front` 전체 |
| 04 Algorithm | 알고리즘 2개 이상 + 기획서 | 동선 최적화, 메이트 추천, 중간 집결지 + 기획서 |
| 05 Backend | MVC 백엔드, 요구사항·Usecase·Class Diagram | `back`, 문서 01·02·06 |
| 06 DB | ERD, DDL, 초기 데이터, Service·DAO | 문서 04, `sql/`, `back` |
| 07 Spring | Spring + REST API + MyBatis | `back` |
| 09 Spring AI | Tool 구조, 단일·다중 Tool, Planner, 고찰 | `back`의 `tool`·`planner`, 고찰 보고서 2부 |

01(Swing)·05(Servlet/JSP)가 지정한 기술은 별도 버전을 만들지 않고, 기능 요구사항을 최종 웹 애플리케이션에 반영했다.

## 전체 실행 방법

사전 준비: JDK 21, Node 20+, Python 3.12, MySQL 8 (`ssafy` 계정), SSAFY 제공 `ssafy_trip` 관광지 DB

```bash
# 1) DB 생성 + 관광지 데이터 적재 (back 저장소)
mysql -ussafy -p --default-character-set=utf8mb4 < back/src/main/resources/sql/schema.sql
mysql -ussafy -p --default-character-set=utf8mb4 < back/src/main/resources/sql/data.sql

# 2) 백엔드 (http://localhost:8080)
cd back
cp src/main/resources/application-private.properties.example src/main/resources/application-private.properties   # 키 입력
./mvnw spring-boot:run

# 3) AI 서버 (http://localhost:8000)
cd allfiles/ai-server
pip install -r requirements.txt
cp .env.example .env    # GMS_KEY 입력
uvicorn main:app --port 8000

# 4) 프론트엔드 (http://localhost:5173)
cd front
npm install
cp .env.example .env.local    # VITE_KAKAO_MAP_KEY 입력
npm run dev
```

필요한 키: SSAFY GMS 키, 공공데이터포털 인증키(기상청 단기예보, 한국환경공단 전기차 충전소), KOPIS 서비스 키, 서울 열린데이터 광장 인증키, 카카오 지도 JavaScript 키(Web 플랫폼에 `http://localhost:5173` 등록).

시연용 계정은 서버 최초 기동 시 자동 생성된다: `admin`(관리자), `mate1`, `mate2`. 비밀번호는 `back`의 `DataInitializer`에 있다.

## AI 서버 (ai-server)

FastAPI + ChromaDB 기반 RAG 서버다. 문서를 문단 단위로 청킹하고 임베딩해 벡터 DB에 저장하며, 유사도 0.30 이상인 청크만 근거로 답한다.

```
ai-server/
├── main.py            # /chat, /upload, /search, /documents, /health
├── requirements.txt
├── .env.example       # GMS 키, 모델, 청크 크기, 유사도 기준
├── data/              # 업로드 문서 (기본: 여행_준비_가이드.md)
└── chroma_data/       # 벡터 DB (실행 시 생성, git 제외)
```

## 검증 결과 (2026-11-27, 로컬)

| 항목 | 결과 |
|---|---|
| 백엔드 컴파일·기동 | 성공 (Spring Boot 4.1.2, 약 3초) |
| 프론트엔드 빌드 | 성공 (`vite build`) |
| API 통합 호출 | 플랜 생성 → 모집글 → 신청 → 수락 → 중간 집결지, 게시글·댓글, 권한 오류(401·403·404·409) 응답 확인 |
| 동선 최적화 | 부산 8곳 87.4km → 68.9km (Held-Karp) |
| 날씨·충전소 | 기상청·한국환경공단 실데이터 응답 확인 |
| 뉴스 수집 | RSS 30건 수집·저장 확인 |
| Spring AI | simple, single, multi, planner 4개 모드 응답 확인 |
| RAG | 문서 참고 응답 2건, 일반 응답 1건 구분 확인 |
| 이동 수단별 경로 | 자동차 51.6km·81분, 도보 53.9km 실제 도로 경로 표시, 대중교통 구간 링크 확인 |
| 공연 정보 | KOPIS 공연 목록·상세·시설 실데이터 응답, 공연장 주변 장소 추천, AI 공연 검색 Tool 확인 |
| 지도 | 탐색 핀, 플랜 번호 핀·경로선, 충전소 핀, 핫플 위치 선택·등록 확인 |
| 화면 | 로그인, 회원가입, 지도 탐색·상세, 플랜 보드, 메이트, 공연 상세, 핫플 등록, AI 길잡이 동작 확인 |

확인하지 못한 것

- 서울시 문화행사: 개발 PC의 네트워크에서 `openapi.seoul.go.kr`에 연결되지 않아(모든 포트 타임아웃) 실제 응답을 검증하지 못했다. 연결 실패 시 5초 안에 안내 문구가 표시되는 것까지 확인했다.
- 게시판 화면, 사진 첨부 업로드, 모바일 화면 세부는 직접 눌러 보지 않았다.
- 자동화 테스트 코드는 없다.

알려진 데이터 특성: 제공 관광지 덤프 50,915건 중 94건은 좌표가 국내 범위를 벗어나 있어 지도 표시에서 제외한다.
