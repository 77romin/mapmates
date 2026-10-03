<p align="center">
  <img src="./assets/mapMates-logo-v1.png" width="160" height="160" alt="MapMates · 너랑 갈.지도 logo">
</p>

# MapMates · 너랑 갈.지도

[한국어](./README.md) | **English**

> Find your route. Meet your mates.

**MapMates** combines **Map + Mates**: a map for discovering places and people to travel with. It carries the same map-and-companionship idea as the Korean name, **너랑 갈.지도**.

Discover destinations, build your own itinerary, and find travel companions. The main journey is **Introduction → Explore the map → Plan your trip → Share or join a trip → Share your travel stories**.

This is a frontend project built with HTML, CSS, and JavaScript. Accounts, itineraries, posts, and comments are stored in the browser's `localStorage`. The demo includes **10 members, 50 community posts, 40 saved plans, and 40 shared trips**. Cross-browser sharing and server authentication are planned for future backend integration.

## Team

| GitHub profile | Name | Responsibilities |
| --- | --- | --- |
| <a href="https://github.com/77romin"><img src="https://github.com/77romin.png?size=160" width="80" height="80" alt="Gangmin Kim's GitHub profile picture"></a> | [Gangmin Kim (김강민)](https://github.com/77romin) | **Planning, maps, My Trips, external API integration, and companions** — Kakao Maps and public tourism API integration, nearby search, destination filters, weather, itinerary editing, driving routes, shared state management and integration testing, companion lists and details, participation permissions, responsive subpages and accessibility |
| <a href="https://github.com/yeeunma"><img src="https://github.com/yeeunma.png?size=160" width="80" height="80" alt="Yeeun Ma's GitHub profile picture"></a> | [Yeeun Ma (마예은)](https://github.com/yeeunma) | **Accounts, community, and EV charging stations** — Sign-up, login, profile management, posts, comments and replies, charging station information and availability |

See the [work allocation document](./docs/WORK_SPLIT.md) for details (in Korean).

## 1. Introduction — Meet MapMates

First-time visitors automatically see the introduction page. Sunset clouds frame the service overview and its four steps: save places, organize an itinerary, meet companions, and share stories. Continue to the map when ready. On later visits, reopen it with **Introduction (소개 ↗)** at the bottom left of the main page.

![Introduction page with the logo and sunset clouds](./docs/screenshots/guide/01-introduction.png)

## 2. Main Page — Discover Places and Travel Together

The app interface and screenshots are currently in Korean. Korean button labels are included below to help you follow the guide.

### Explore nearby destinations

Move the map or search for a place to see nearby destinations as photo cards and markers. Select a card for details, then use **Add to itinerary (일정 추가)** to save the destination to your trip. The left panel can be collapsed.

![Explore tab with nearby destination cards and map markers](./docs/screenshots/guide/06-main-explore.png)

### Filter destinations

The **Destinations (여행지)** button lets you toggle attractions, restaurants, accommodation, cultural venues, performances/events, shopping, and travel courses. Select **All (전체)** to enable every category; select it again to disable all of them. Cards and markers update to match your selection.

![Destination category filter](./docs/screenshots/guide/07-place-filter.png)

### Check the five-day forecast

Select **Weather (날씨)** to see the five-day forecast for the map center, including daily conditions and minimum/maximum temperatures. Select it again to hide the panel. The number of days available can vary with the Korea Meteorological Administration API response.

![Daily weather conditions and temperatures at the map center](./docs/screenshots/guide/09-weather.png)

### Check EV charger availability

Enable **Charging stations (충전소)** to display stations nearby. Select a lightning marker to see the station name and address, **available chargers**, total chargers, chargers in use, and individual charger status. Stations can be shown at any zoom level. For performance, the app displays up to 80 stations within 20 km of the map center and indicates when only part of the regional data has been retrieved.

![Charging station markers and available charger counts](./docs/screenshots/guide/10-chargers.png)

### Popular places saved in travel plans

Enable **Popular places (핫플)** to see up to **10 places** most frequently saved in itineraries within the current map bounds. The ranking includes the number of distinct members and plans that contain each place. Moving the map updates the results. Select a place for details or to add it to your itinerary. In this frontend demo, rankings use plans stored in the current browser.

![Popular place ranking and numbered map markers](./docs/screenshots/guide/11-hotplaces.png)

### Map types and Roadview

Select the **map icon** at the bottom right to open a vertical menu: standard map, Skyview, Roadview, and terrain. Switch views to inspect the surroundings.

![Vertical map type menu](./docs/screenshots/guide/12-map-types.png)

With **Roadview (로드뷰)** selected, supported roads appear in blue. **Double-click a road** to open a Roadview popup. Drag the panorama to look around and use the road arrows to move to nearby locations.

![Interactive Roadview popup with navigation arrows](./docs/screenshots/guide/15-roadview-popup.png)

### Browse shared trips and join companions

The **Browse (둘러보기)** tab shows photo cards for trips shared on the Find Companions page. Selecting a card displays its description, participant count, daily itinerary, and weather, and moves the map to the route. Switch between straight lines and driving routes, or select **View details (세부내용 보기)** to open the companion detail page.

![Shared trip cards in the Browse tab](./docs/screenshots/guide/16-browse-cards.png)

Select **Join trip (동행하기)** beside the title to participate. The button becomes **Joined (참가 중)**; select it again to cancel. Full trips and trips closed by the organizer do not accept new participants. If login is required, you return to the selected trip after signing in.

![Selected shared trip with the Joined button](./docs/screenshots/guide/18-browse-joined.png)

### View your plans in My Trips

The **My Trips (내 여행)** tab only shows plans created by the signed-in member. Select a card and use the **Straight / Driving route (직선 / 차량경로)** toggle and DAY buttons to inspect the route. Both route types use solid red lines. **Create a new trip (새 여행 작성)** opens a new plan, while **View details (세부내용 보기)** opens the selected plan for editing.

![Selected personal trip with a red straight-line route](./docs/screenshots/guide/20-mine-straight.png)

![Driving route selected for a personal trip](./docs/screenshots/guide/21-mine-car.png)

## 3. My Trips Page — Manage Plans as Cards

### Saved plans and details

Open **My Trips (내 여행)** from the top navigation. Each saved plan appears as a card with its first destination photo, dates, number of places, region/theme, and sharing status. Selecting a card takes you to its detailed itinerary and editor.

![Saved trip cards using first-destination photos](./docs/screenshots/guide/22-plan-library.png)

### Create a trip and assign places to days

Select **New trip (＋ 새 여행)** and enter a title, departure and end dates, group capacity (including yourself), estimated budget, region, theme, and introduction. Available regions are Seoul, Gyeonggi, Gangwon, Chungnam, Chungbuk, Gyeongnam, Gyeongbuk, Jeonnam, Jeonbuk, Jeju, and Other.

![New trip form with dates, capacity, budget, region, theme, and introduction](./docs/screenshots/guide/24-new-plan.png)

Select **Add place (＋ 장소 추가)** below the estimated travel information to return to map exploration. Add destinations through **Add to itinerary (일정 추가)** in their detail view, then return to My Trips to assign each place a DAY, visit time, and memo. Stops are sorted by visit time. You can also check daily weather, routes, and estimated travel distance and duration.

![Daily itinerary, visit times, memos, route map, and travel estimates](./docs/screenshots/guide/23-plan-details.png)

Use **Save → Find companions (저장 → 동행 구하기)** to share a plan. For an existing shared plan, **Update shared itinerary (공유한 일정 업데이트)** updates the same recruitment post while preserving its participants and any closure set by the organizer.

## 4. Find Companions — Read, Ask, and Join

### Browse shared plans

Filter by region, theme, recruitment status, or search keywords. Each card uses the photo of the first scheduled destination and shows its status, author, participants, and capacity. **Only trips I joined (내가 참가한 동행만)** filters the list to your participation records.

![Companion trip list with first-destination photos and recruitment status](./docs/screenshots/guide/25-companions.png)

### Participate and check the group

Open a card to see the introduction, daily route and weather, schedule, and participant list. Use **Apply to join (참가 신청)** to participate and **Cancel participation (참가 취소)** to leave. Organizers can edit their recruitment post or **Close recruitment (참여마감)**. A trip automatically closed at capacity reopens when someone leaves; a trip manually closed by its organizer stays closed.

![Companion trip description, schedule, and participant list](./docs/screenshots/guide/26-companion-participants.png)

### Ask questions before joining

The **Trip questions and answers (동행 질문과 답변)** section at the bottom lets signed-in members comment and reply even before joining. Submit with the paper-plane button. Members can edit their own comments. Replies remain visible when a parent comment is deleted.

![Questions and replies on a shared trip](./docs/screenshots/guide/27-companion-qa.png)

## 5. Community — Share Stories and Replies

### Create, edit, and delete posts

Read and search the Travel Information, Announcements, and Tourism News tabs. Signed-in members can use **Write a post (＋ 글쓰기)** in Travel Information to enter a category, title, and content. Authors can **Edit / Delete (수정 / 삭제)** their posts from the detail page. Posting announcements and tourism news requires an administrator account.

![Community list, search, and post creation entry point](./docs/screenshots/guide/28-community.png)

![Writing a travel story](./docs/screenshots/guide/29-post-writing.png)

![Edit and Delete controls on a post owned by the signed-in member](./docs/screenshots/guide/30-post-owner.png)

### Comments and replies

Enter a comment below a post and submit it with the paper-plane button. Select **Reply (답글)** on a comment to add a reply underneath it. Only comment authors can edit their comments. Comment authors and the post author can delete them.

![Replies displayed beneath their parent comment](./docs/screenshots/guide/31-community-replies.png)

## 6. Sign Up — Create an Account to Save Trips

1. Open the account page through the profile area and select **Sign up (회원가입)**.
2. Enter an email, password and confirmation, name, nickname, date of birth, and gender. A profile photo is optional.
3. Use a password of at least eight characters containing letters and numbers. Members must be at least 14 years old. An email already registered in this browser cannot be reused.
4. Completing sign-up logs you in so you can save trips, join companions, and use the community. If a protected action sent you to the account page, you return to the previous screen afterward.

![Sign-up fields and optional profile photo selection](./docs/screenshots/guide/02-signup.png)

## 7. Login and My Page — Manage Your Profile and Activity

### Login

Select **Login (로그인)** on the account page and enter your registered email and password. Selecting a demo account fills in its credentials. Ten demo accounts are available, from `traveler1@example.com` to `traveler10@example.com`, with the shared password `Trip2026!`. The tenth account, ‘여행지기’, is an administrator.

![Login form and demo account selection](./docs/screenshots/guide/03-login.png)

### Update your nickname and profile photo

After logging in, select your profile at the top right to open **My Page (마이페이지)**. Change your nickname or use **Choose photo → Save profile (사진 선택 → 프로필 저장)** to upload a photo. Profile changes propagate to your posts, comments, recruitment posts, and participant entries. Settings also provide password changes and account deletion.

![Nickname editing, photo selection, and account details in My Page](./docs/screenshots/guide/04-profile.png)

**My activity (내 활동)** lists your recruitment posts, community posts, and trips you have joined, with links to each item.

![Personal activity overview](./docs/screenshots/guide/05-activity.png)

## About the Screenshots

These screenshots were captured by operating the app in a separate Chrome browser on October 3, 2026. Maps, weather, charging stations, and Roadview use real APIs. Accounts, plans, questions, and posts use local demo data. Actions performed for screenshots do not change the user's existing browser data. Content varies with API responses, map position, account, and stored data.

## Technology

| Area | Technology |
| --- | --- |
| Markup and styling | HTML5, CSS3, responsive media queries |
| Frontend | Vanilla JavaScript, ES Modules, Fetch API |
| Maps and routing | Kakao Maps JavaScript API, Kakao Mobility Directions API |
| Tourism | Korea Tourism Organization TourAPI |
| Weather | Korea Meteorological Administration short- and medium-range forecast APIs |
| Other data | Korea Environment Corporation EV charger API, Sunrise-Sunset API |
| Storage | Web Storage API (`localStorage`) |
| Development server | Node.js built-in `http` module |

## Getting Started

### 1. Start the server

No additional package installation is required to run the app. It uses ES Modules and a Kakao Mobility proxy, so serve it through the local server rather than opening HTML files directly.

```bash
node dev-server.js
```

Open [http://localhost:5500](http://localhost:5500) in your browser.

VS Code Live Server can display general pages, but use `dev-server.js` to test the driving-route proxy as well.

### 2. Configure API keys

Copy `config.example.js` to `config.js` and enter your issued keys.

```bash
cp config.example.js config.js
```

Windows PowerShell:

```powershell
Copy-Item config.example.js config.js
```

```js
window.APP_CONFIG = {
  KAKAO_JS_KEY: "",
  KAKAO_REST_KEY: "",
  TOUR_API_KEY: "",
  WEATHER_API_KEY: "",
  EV_CHARGER_API_KEY: "",
  SGIS_CONSUMER_KEY: "",
  SGIS_CONSUMER_SECRET: "",
  API_PROXY_URL: "http://localhost:5500",
};
```

`config.js` is excluded by `.gitignore` and is not committed to Git.

### 3. API configuration checklist

- Kakao JavaScript key: register `http://localhost:5500` under the app's Web platform settings.
- Kakao REST API key: used for driving directions.
- Korean public data portal keys: generally use the decoded key, since `URLSearchParams` handles encoding.
- Medium-range weather forecasts: check the separate application and approval status.
- `API_PROXY_URL`: set to `http://localhost:5500` when using the supplied development server.

## Data Sources

- [Korea Tourism Organization TourAPI](https://www.data.go.kr/data/15101578/openapi.do)
- [SGIS Open API](https://sgis.kostat.go.kr/developer/html/main.html)
- [Kakao Maps Web API](https://apis.map.kakao.com/web/)
- [Kakao Mobility Directions API](https://developers.kakaomobility.com/docs/navi-api/directions/)
- [KMA short-range forecasts](https://www.data.go.kr/data/15084084/openapi.do)
- [KMA medium-range forecasts](https://www.data.go.kr/data/15059468/openapi.do)
- Korea Environment Corporation EV charging station information
- [Sunrise-Sunset API](https://sunrise-sunset.org/api)

## Project Structure

```text
.
├─ index.html                 # Map exploration
├─ planner.html               # My Trips and itinerary editing
├─ signup.html                # Sign-up and login
├─ mypage.html                # Profile and account management
├─ companions.html            # Companion trip list
├─ companion-detail.html      # Companion trip details
├─ community.html             # Community list
├─ post-detail.html           # Posts and comments
├─ post-write.html            # Post creation and editing
├─ dev-server.js              # Static server and Kakao Mobility proxy
├─ config.example.js          # API configuration template
├─ assets/                    # Logo and images
├─ css/
│  ├─ style.css               # Map and shared UI
│  └─ subpages.css            # Subpage UI
├─ docs/
│  ├─ WORK_SPLIT.md           # Work allocation
│  └─ TROUBLE_SHOOTING.md     # Troubleshooting history
└─ js/
   ├─ api.js                  # External API module
   ├─ app.js                  # Map exploration
   ├─ planner.js              # Trip planning
   ├─ shared.js               # Shared state and UI utilities
   ├─ account.js              # Account and profile utilities
   ├─ companion-utils.js      # Companion status and permissions
   ├─ board.js                # Board state and permissions
   └─ Page-specific scripts
```

## State Storage

The frontend stores the following under the `neorang-galjido-v1` key in `localStorage`:

- Member profiles and login state
- Favorite places and itineraries
- Shared companion trips and participation records
- Posts and comments
- Itinerary-based popular place rankings and map settings

Clearing browser storage resets the demo to its initial state. A production version should use server authentication, a database, and image storage instead of storing passwords in the browser.

## Validation

```bash
node --check js/app.js
node --check js/planner.js
node --check js/shared.js
git diff --check
```

Integration checks cover map movement, layer toggles, itinerary saving, driving routes, sign-up, companion participation, and post/comment permissions in the browser.

## Troubleshooting

The [troubleshooting document](./docs/TROUBLE_SHOOTING.md) (in Korean) explains fixes for destination results not following map position, driving-route CORS, combining weather forecast ranges, profile image storage limits, and avoiding Git conflicts.

## Demo Data and Tests

Additional demo records are added to an existing browser only once, preserving previous edits. My Trips only shows plans owned by the signed-in account.

With the development server running, use Playwright and an installed Chrome browser to run the tests. If Playwright is not on the default module path, set `PLAYWRIGHT_MODULE` to its absolute package path.

```bash
node tests/e2e.cjs
node tests/live.cjs
node tests/workspace-join.cjs
node tests/comments.cjs
node tests/plan-cards.cjs
node tests/companion-thumbnails.cjs
node tests/demo-expansion.cjs
```

The integration suite passed 62 checks. Live API tests require `config.js`. See the [feature audit](./docs/FEATURE_AUDIT.md) and [troubleshooting history](./docs/TROUBLE_SHOOTING.md) for scope, validation details, causes, and fixes (in Korean).
