# 너랑 갈.지도 (mapmates)

<img src="front/public/logo.webp" alt="너랑 갈.지도 로고" width="300" />

> 너랑 갈 다음 여행을 지도 위에 · Find your route. Meet your mates.

지도 한 장 위에서 경로를 짜고 동행(메이트)을 만나는 여행 플래너입니다.

- **Route**: 관광지 5만 건을 사진 핀으로 탐색하고, 플랜 보드에서 동선을 최적화하고, 자동차·도보·대중교통 경로를 확인합니다.
- **Mates**: 플랜을 공개해 메이트를 모집하고, 수락된 메이트와 같은 플랜을 함께 고칩니다.
- **AI 길잡이**: 조건에 맞는 장소를 AI가 추천하고 서버가 실제 등록된 장소인지 확인해 코스를 만듭니다. 문서 업로드 기반 Q&A(RAG)도 제공합니다.

## 저장소 구성

| 폴더 | 내용 | 안내 |
|---|---|---|
| [`back/`](back) | Spring Boot 4 + MyBatis + MySQL + Spring AI 백엔드 | [back/README.md](back/README.md) |
| [`front/`](front) | Vue 3 + Vite + Bootstrap 5 + Kakao Maps 프론트엔드 | [front/README.md](front/README.md) |
| [`allfiles/`](allfiles) | 설계 산출물, DDL·더미 데이터, FastAPI RAG 서버, 발표 자료 | [allfiles/README.md](allfiles/README.md) |

세 폴더는 각각 별도 저장소로 개발한 뒤 이력을 그대로 합친 것입니다.

## 주요 문서

| 문서 | 위치 |
|---|---|
| 요구사항 정의서 | [allfiles/docs/01_요구사항정의서.md](allfiles/docs/01_요구사항정의서.md) |
| Usecase Diagram | [allfiles/docs/02_유스케이스_다이어그램.md](allfiles/docs/02_유스케이스_다이어그램.md) |
| WBS | [allfiles/docs/03_WBS.md](allfiles/docs/03_WBS.md) |
| ERD | [allfiles/docs/04_ERD.md](allfiles/docs/04_ERD.md) |
| API 설계서 | [allfiles/docs/05_API설계서.md](allfiles/docs/05_API설계서.md) |
| Class Diagram | [allfiles/docs/06_클래스_다이어그램.md](allfiles/docs/06_클래스_다이어그램.md) |
| 화면 정의서 | [allfiles/docs/07_화면정의서.md](allfiles/docs/07_화면정의서.md) |
| 알고리즘 적용 기획서 | [allfiles/docs/08_알고리즘_적용_기획서.md](allfiles/docs/08_알고리즘_적용_기획서.md) |
| AI 고찰 보고서 | [allfiles/docs/09_AI_고찰보고서.md](allfiles/docs/09_AI_고찰보고서.md) |
| 발표 자료 | [allfiles/presentation](allfiles/presentation) |

## 실행 방법

사전 준비: JDK 21, Node 20+, Python 3.12, MySQL 8

```bash
# 1) DB 생성과 데이터 적재
mysql -u<계정> -p --default-character-set=utf8mb4 < back/src/main/resources/sql/schema.sql
mysql -u<계정> -p --default-character-set=utf8mb4 < back/src/main/resources/sql/data.sql

# 2) 백엔드 (http://localhost:8080)
cd back
cp src/main/resources/application-private.properties.example src/main/resources/application-private.properties   # 키 입력
./mvnw spring-boot:run

# 3) AI 서버 (http://localhost:8000)
cd allfiles/ai-server
pip install -r requirements.txt
cp .env.example .env    # 키 입력
uvicorn main:app --port 8000

# 4) 프론트엔드 (http://localhost:5173)
cd front
npm install
cp .env.example .env.local    # 카카오 지도 키 입력
npm run dev
```

필요한 키와 자세한 설명은 각 폴더의 README에 있습니다. API 키는 저장소에 포함되어 있지 않습니다.

## 이전 버전

이 저장소의 이전 프로젝트(2026-11-08까지의 작업)는 커밋 이력과 `db-project` 브랜치에 남아 있습니다.
