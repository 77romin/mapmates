# API 설계서

- Base URL: `http://localhost:8080/api` (백엔드), `http://localhost:8000` (AI 서버)
- 형식: 요청·응답 모두 JSON (`Content-Type: application/json; charset=utf-8`). 핫플레이스 등록만 `multipart/form-data`
- 인증: 로그인 응답의 `token`을 `Authorization: Bearer {token}` 헤더로 전달
- 인증 규칙: **조회(GET)는 비로그인 허용**, 변경 요청(POST·PUT·DELETE)과 `/plans/**`, `/members/me`는 로그인 필수. `/auth/**`, `/ai/**`, `/news`는 항상 허용
- 오류 응답: `{ "message": "사유" }` + HTTP 상태 코드

| 상태 | 의미 |
|---|---|
| 400 | 입력값 오류, 요청 형식 오류 |
| 401 | 로그인 필요, 아이디·비밀번호 불일치 |
| 403 | 권한 없음(작성자·모집자·관리자 아님, 비공개 플랜) |
| 404 | 대상 없음 |
| 409 | 중복(이미 사용 중인 아이디 등) |
| 502 | 외부 AI 응답 실패 |

## 1. 회원 · 인증

| Method | URL | 설명 | 인증 | 요청 | 응답 |
|---|---|---|---|---|---|
| POST | `/auth/join` | 회원가입 | - | `userId, password, name, email, tags?` | Member |
| POST | `/auth/login` | 로그인 | - | `userId, password` | `{ token, member }` |
| POST | `/auth/logout` | 로그아웃 | - | - | `{ message }` |
| POST | `/auth/find-password` | 비밀번호 찾기(임시 비밀번호 발급) | - | `userId, email` | `{ message, tempPassword }` |
| GET | `/members/me` | 내 정보 조회 | O | - | Member |
| PUT | `/members/me` | 내 정보 수정 | O | `name, email, tags, homeLat, homeLng, password?` | Member |
| DELETE | `/members/me` | 회원 탈퇴 | O | - | `{ message }` |

검증: 아이디 영문·숫자·밑줄 4~20자, 비밀번호 8자 이상. 응답의 Member에는 비밀번호가 포함되지 않는다.

## 2. 관광지

| Method | URL | 설명 | 주요 파라미터 | 응답 |
|---|---|---|---|---|
| GET | `/attractions/sidos` | 시도 목록 | - | `[{ code, name }]` |
| GET | `/attractions/guguns` | 구군 목록 | `sidoCode` | `[{ code, name }]` |
| GET | `/attractions/content-types` | 콘텐츠 유형 목록 | - | `[{ code, name }]` |
| GET | `/attractions` | 관광지 검색 | `sidoCode?, gugunCode?, contentTypeId?, keyword?, page=1, size=30` | `{ items, total, page, size }` |
| GET | `/attractions/{no}` | 상세 | - | Attraction(overview, homepage 포함) |
| GET | `/attractions/{no}/nearby` | 주변 장소 | `kind=festival\|store\|stay\|all, radius=5(km)` | `[Attraction + distance]` |
| GET | `/attractions/{no}/weather` | 단기예보 | - | `{ available, forecasts:[{ time, TMP, SKY, PTY, POP, summary }], message? }` |
| GET | `/attractions/{no}/sun` | 일출·일몰 | - | `{ sunrise, sunset }` |
| GET | `/attractions/{no}/chargers` | 전기차 충전소 | `radius=3(km)` | `{ available, stations:[{ name, addr, latitude, longitude, distance, total, availableCount }] }` |

콘텐츠 유형: 12 관광지, 14 문화시설, 15 축제공연행사, 25 여행코스, 28 레포츠, 32 숙박, 38 쇼핑, 39 음식점

## 3. 여행 계획

| Method | URL | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/plans` | 내 플랜(작성 + 메이트 참여) | - | `[Plan]` |
| GET | `/plans/{id}` | 플랜 상세 | - | Plan + `items` + `members` |
| POST | `/plans` | 플랜 생성 | `title, startDate?, endDate?, budget?, memo?, isPublic?, items:[{ attractionNo, dayNo, cost?, memo? }]` | Plan |
| PUT | `/plans/{id}` | 플랜 수정(참여자 누구나) | 위와 동일 | Plan |
| DELETE | `/plans/{id}` | 플랜 삭제(작성자) | - | `{ message }` |
| POST | `/plans/optimize` | 동선 최적화 | `{ attractionNos:[no...] }` | `{ method, beforeKm, afterKm, order, attractions }` |
| GET | `/plans/{id}/meeting-point` | 메이트 중간 집결지 | - | `{ latitude, longitude, memberCount, nearby }` |

`items` 배열의 순서가 방문 순서(`seq`)로 저장된다. `method`는 `HELD_KARP_DP`(12곳 이하) 또는 `NEAREST_NEIGHBOR_2OPT`.

## 4. 메이트

| Method | URL | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/mates` | 모집글 목록 | - | `[MatePost]` (로그인 시 `matchRate`, `myStatus` 포함, 일치도순 정렬) |
| GET | `/mates/{id}` | 모집글 상세 | - | `{ post, items, members, applications? }` (applications는 모집자에게만) |
| POST | `/mates` | 모집글 작성 | `planId, title, content?, capacity, tags?` | MatePost |
| DELETE | `/mates/{id}` | 모집글 삭제(작성자) | - | `{ message }` |
| POST | `/mates/{id}/apply` | 참여 신청 | `message?` | `{ message }` |
| POST | `/mates/applications/{applicationId}/accept` | 신청 수락(모집자) | - | `{ message }` |
| POST | `/mates/applications/{applicationId}/reject` | 신청 거절(모집자) | - | `{ message }` |

## 5. 핫플레이스

| Method | URL | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/hotplaces` | 목록 | - | `[Hotplace]` |
| POST | `/hotplaces` | 등록 (multipart) | `title, description?, placeType?, visitedDate?, latitude, longitude, image?` | Hotplace |
| PUT | `/hotplaces/{id}` | 수정(작성자) | `title, description, placeType, visitedDate` | Hotplace |
| DELETE | `/hotplaces/{id}` | 삭제(작성자·관리자) | - | `{ message }` |

이미지는 jpg·png·gif·webp, 10MB 이하. 저장된 파일은 `/uploads/{파일명}`으로 제공된다.

## 6. 커뮤니티 · 뉴스

| Method | URL | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| GET | `/boards` | 목록 | `type=NOTICE\|FREE, keyword?, page=1, size=10` | `{ items, total, page, size }` |
| GET | `/boards/{id}` | 상세(조회수 증가) | - | Board + `comments` |
| POST | `/boards` | 작성 (NOTICE는 관리자만) | `type, title, content` | Board |
| PUT | `/boards/{id}` | 수정(작성자·관리자) | `title, content` | Board |
| DELETE | `/boards/{id}` | 삭제(작성자·관리자) | - | `{ message }` |
| POST | `/boards/{id}/comments` | 댓글 작성 | `content` | Board + `comments` |
| DELETE | `/boards/comments/{commentId}` | 댓글 삭제 | - | `{ message }` |
| GET | `/news` | 여행 뉴스 | `keyword=국내 여행, size=20` | `[News]` |

뉴스는 같은 키워드를 1시간 이내에 수집한 적이 없으면 새로 수집해 DB에 저장한 뒤 조회한다.

## 6-1. 공연 · 문화행사

| Method | URL | 설명 | 주요 파라미터 | 응답 |
|---|---|---|---|---|
| GET | `/culture/performances` | 공연 목록 (KOPIS, 오늘부터 30일) | `sidoCode?, keyword?, page=1, size=20` | `{ items:[{ id, title, startDate, endDate, venue, poster, area, genre, state }], page, hasNext }` |
| GET | `/culture/performances/{id}` | 공연 상세 + 공연 시설 | - | `{ title, startDate, endDate, venue, cast, runtime, age, price, poster, genre, state, schedule, facility:{ name, address, tel, seats, homepage, latitude, longitude } }` |
| GET | `/culture/facilities/{id}` | 공연 시설 상세 | - | facility |
| GET | `/culture/seoul-events` | 서울시 문화행사 | `keyword?, page=1, size=20` | `{ items:[{ title, category, district, period, place, target, fee, isFree, image, homepage, latitude, longitude }], total, page, hasNext }` |
| GET | `/culture/nearby` | 좌표 주변 관광지·문화시설·음식점 | `latitude, longitude, radius=2(km)` | `[Attraction + distance]` |

키가 설정되지 않으면 503, 외부 API 호출에 실패하면 502를 반환한다. 응답은 30분(목록)·60분(상세) 캐시한다.

## 7. AI 길잡이 (Spring AI)

| Method | URL | 설명 |
|---|---|---|
| POST | `/ai/chat` | AI 응답 생성 |

요청

```json
{ "message": "이번 주말 부산 1박2일 코스 짜줘. 날씨도 알려줘", "mode": "planner" }
```

| mode | 동작 |
|---|---|
| `planner` (기본) | Planner가 Tool 순서를 정하고 서버가 실행, 결과를 Prompt에 넣어 응답 (서버 주도) |
| `multi` | 모든 Tool을 LLM에 제공, LLM이 필요한 Tool을 연쇄 호출 (LLM 주도) |
| `single` | `tool`(attraction, weather, route, plan, document, culture) 하나만 제공 |
| `simple` | Tool 없이 LLM만 호출 |

응답

```json
{
  "mode": "planner",
  "answer": "부산 1박 2일 추천 코스입니다. ...",
  "steps": [
    { "tool": "SEARCH_ATTRACTIONS", "input": "region=부산, keyword=, type=관광지, count=6" },
    { "tool": "GET_WEATHER", "input": "lat=35.0230, lng=128.8169 (가덕도)" },
    { "tool": "OPTIMIZE_ROUTE", "input": "attractionNos=[58599, 58600, ...]" }
  ],
  "places": [ { "no": 58599, "title": "가덕도", "type": "관광지", "address": "...", "latitude": 35.02, "longitude": 128.81 } ],
  "route": { "method": "HELD_KARP_DP", "beforeKm": 60.2, "afterKm": 45.1, "order": [58599, 58600] },
  "plan": { "steps": ["SEARCH_ATTRACTIONS", "GET_WEATHER", "OPTIMIZE_ROUTE"], "region": "부산", "count": 6 }
}
```

## 8. AI 서버 (FastAPI RAG)

| Method | URL | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| POST | `/chat` | RAG 챗봇 | `{ message }` | `{ answer, used_documents, notice, sources:[{ source, score, preview }] }` |
| POST | `/upload` | 문서 업로드 (multipart) | `file` (txt, md, pdf / 10MB 이하) | `{ filename, chunks, message }` |
| POST | `/search` | 벡터 검색 (Spring의 문서 검색 Tool이 호출) | `{ query, top_k=3 }` | `{ results:[{ text, source, chunk, score }] }` |
| GET | `/documents` | 문서 목록 | - | `{ documents, chunks }` |
| GET | `/health` | 상태 확인 | - | `{ status, chunks, key_configured }` |

`used_documents`가 `false`이면 `notice`에 "일반 생성형 AI 답변"임을 명시한다.
