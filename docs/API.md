# JSON API

기본 경로는 `/mapmates/api`다. 세션 쿠키를 사용하며 브라우저에서는 같은 출처의 `fetch`를 사용한다. POST/PUT 요청은 `Content-Type: application/json`이 필요하다. ID는 DB BIGINT이며, 프론트의 소유자·계획·댓글 ID는 문자열로 비교한다. 관광지 ID는 TourAPI 콘텐츠 번호다.

| 메서드 | 경로 | 권한·동작 |
|---|---|---|
| POST | `/members` | 가입 후 세션 생성 |
| GET | `/members/check-email?email=`, `/members/check-nickname?nickname=` | 중복 확인 |
| GET/PUT/DELETE | `/members/me` | 본인 조회·프로필 수정·탈퇴 |
| PUT | `/members/me/password` | 현재 비밀번호 확인 후 변경 |
| GET | `/members/{id}` | 공개 프로필 |
| POST | `/auth/login`, `/auth/logout`, `/auth/password-reset` | 로그인·로그아웃·정보 확인식 재설정 |
| GET | `/auth/me` | `{loggedIn,member}` |
| GET | `/areas`, `/areas/{code}/guguns`, `/content-types` | 지역·유형 기준 데이터 |
| GET | `/attractions` | `areaCode,sigunguCode,contentTypeId,keyword,page,size` |
| GET | `/attractions/nearby` | `lat,lng,radius`(km, 0.1~50), `contentTypeId,page,size` |
| GET | `/attractions/{id}` | 관광지 상세 |
| GET/POST | `/plans` | 본인 계획 목록·생성 |
| GET/PUT/DELETE | `/plans/{id}` | 조회는 작성자·동행 참가자, 변경은 작성자 |
| GET/POST | `/favorites` | 찜 조회·추가 |
| DELETE | `/favorites/{contentId}` | 찜 해제 |
| GET/POST | `/companions` | 공개 모집 목록·본인 계획 공유 |
| GET/PUT/DELETE | `/companions/{id}` | 공개 조회·작성자 수정·삭제 |
| POST | `/companions/{id}/close` | 작성자가 직접 모집 마감 |
| POST | `/companions/{id}/participants` | 참가, 중복 요청은 기존 참가 유지 |
| DELETE | `/companions/{id}/participants/me` | 본인 참가 취소 |
| GET/POST | `/posts` | 공개 목록·회원 글 작성, 공지·뉴스는 관리자 |
| GET/PUT/DELETE | `/posts/{id}` | 공개 상세(조회수 증가)·작성자/관리자 변경 |
| GET/POST | `/posts/{id}/comments`, `/companions/{id}/comments` | 공개 댓글 조회·회원 댓글/답글 작성 |
| PUT/DELETE | `/comments/{id}` | 수정은 댓글 작성자, 삭제는 댓글/대상 작성자 또는 관리자 |
| GET | `/popular-places?south=&north=&west=&east=` | 전체 계획 기반 회원 수·계획 수 TOP 10 |
| GET | `/kakao-directions?origin=&destination=&waypoints=` | 서버 REST 키로 고정 Kakao 호스트 프록시 |

관광지·동행·게시글 목록은 `{items,page,size,total}`를 반환한다. 계획·찜·댓글·인기 장소 목록은 배열이다. 페이지는 1부터, 크기는 1~100이다. 동행 필터는 `region,theme,status=open|closed`, 게시글은 `board,category,keyword,sort=latest|views|comments`를 지원한다. `status`는 직접 마감 여부이며 응답 카드의 상태는 정원 도달도 반영한다. 게시글 키워드는 제목·내용·분류·작성자에 적용된다.

## 여행계획 저장

```json
{
  "title": "서울 산책",
  "startDate": "2026-11-14",
  "endDate": "2026-11-14",
  "capacity": 4,
  "budget": 30000,
  "region": "서울",
  "theme": "문화",
  "description": "함께 걸어요",
  "items": [{
    "day": 1,
    "time": "10:00",
    "memo": "출발",
    "place": {
      "id": 125452,
      "title": "청계산",
      "lat": 37.4416867721,
      "lng": 127.0543676446,
      "image": "",
      "address": "서울",
      "contentTypeId": 12
    }
  }]
}
```

생성은 `201`과 저장한 계획을 반환한다. PUT은 일정 전체를 원자적으로 교체한다. 응답은 기존 화면용 `{id,ownerId,trip,tripSchedule,itinerary,itineraryPlaces}` 구조다. 공유는 `POST /companions {"planId":1}`로 하며 같은 계획 재공유 시 기존 모집글을 반환한다. 동행 수정 본문은 `title,description,capacity,closed`다. 여행 기간·지역·테마·방문 일정은 원본 계획에서 수정한다.

게시글 본문은 `{board,category,title,content}`, 댓글은 `{content,parentId}`다. 답글의 멘션은 서버에서 부모 댓글 작성자를 참조해 결정한다. 다른 게시글·동행의 댓글을 부모로 지정할 수 없다.

빈 성공은 `204`, 오류는 `{code,message}`다. 입력 오류 400, 로그인 필요 401, 권한 없음 403, 대상 없음 404, 지원하지 않는 방식 405, 중복·만석·업무 규칙 충돌 409, 서버 오류 500을 사용한다. 키 미설정 길찾기는 503이다. SQL·비밀번호 해시·서버 키는 오류 응답에 포함하지 않는다.
