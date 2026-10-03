import { mountWorkspace } from './map-workspace.js';
import { bindRoadview } from './trip-map.js';
import { places } from "./data.js";
import { api } from "./api.js";
import { readProfileImage } from "./account.js";

import { loadState, saveState, initShell, ensureLoggedIn } from './shared.js';
const NEARBY_RADIUS_METERS = 20000;
const CHARGER_VISIBLE_MAX_MAP_LEVEL = 7;
const INITIAL_MAP_CENTER = { lat: 37.50079, lng: 127.03689 };
const PLACE_CATEGORY_IDS = ['attraction', 'food', 'stay', 'culture', 'course', 'festival', 'shopping'];
const state = loadState();
state.search = new URLSearchParams(location.search).get('q') || '';
initShell('explore', state);
state.placeCategories = Array.isArray(state.placeCategories)
  ? state.placeCategories.filter((category) => PLACE_CATEGORY_IDS.includes(category))
  : state.category && state.category !== "all" ? [state.category] : [...PLACE_CATEGORY_IDS];
const fallbackPlaces = places.map((place) => ({ ...place }));
const liveData = { tour: false, weather: null, chargerCount: null };
const layerVisibility = { place: state.placeCategories.length > 0, weather: state.weatherVisible === true, charger: false, hotplace: false };
let kakaoMap = null;
let workspace = null;
let kakaoMarkers = [];
let kakaoRoute = null;
let chargerMarkers = [];
let chargerRevision = 0;
let currentUserLocation = null;
let currentLocationPromise = null;
let hotplaceMarkers = [];
let selectedMapType = "roadmap";
let terrainEnabled = false;
let mapIdleTimer = null;
let lastMapFetchKey = "";
let latestWeatherKey = "";
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const persist = () => saveState(state);
const placeById = (id) => places.find((place) => place.id === Number(id)) || state.itineraryPlaces?.[Number(id)] || null;
const escapeHtml = (value = "") => String(value).replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]);

function getCurrentLocation() {
  if (currentUserLocation) return Promise.resolve(currentUserLocation);
  if (!navigator.geolocation) return Promise.resolve(null);
  if (!currentLocationPromise) {
    currentLocationPromise = new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(({ coords }) => {
        currentUserLocation = { lat: coords.latitude, lng: coords.longitude };
        resolve(currentUserLocation);
      }, () => resolve(null), { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
    }).finally(() => { currentLocationPromise = null; });
  }
  return currentLocationPromise;
}

function toast(message) {
  const element = document.createElement("div");
  element.className = "toast";
  element.textContent = message;
  $("#toast-container").append(element);
  setTimeout(() => element.remove(), 2800);
}

function applyMapType() {
  if (!kakaoMap || !window.kakao?.maps) return;
  const { MapTypeId } = window.kakao.maps;
  kakaoMap.removeOverlayMapTypeId(MapTypeId.ROADVIEW);
  kakaoMap.removeOverlayMapTypeId(MapTypeId.TERRAIN);
  kakaoMap.setMapTypeId(selectedMapType === "skyview" ? MapTypeId.SKYVIEW : MapTypeId.ROADMAP);
  if (terrainEnabled) kakaoMap.addOverlayMapTypeId(MapTypeId.TERRAIN);
  if (selectedMapType === "roadview") kakaoMap.addOverlayMapTypeId(MapTypeId.ROADVIEW);
}

function renderPlaceFilterControls() {
  const allSelected = PLACE_CATEGORY_IDS.every((category) => state.placeCategories.includes(category));
  $$('[data-place-category]').forEach((button) => {
    const selected = button.dataset.placeCategory === "all"
      ? allSelected
      : state.placeCategories.includes(button.dataset.placeCategory);
    button.classList.toggle("is-active", selected);
    button.setAttribute("aria-pressed", String(selected));
  });
  layerVisibility.place = state.placeCategories.length > 0;
  $("#place-filter-toggle").classList.toggle("is-active", layerVisibility.place);
  $("#place-filter-toggle").setAttribute("aria-pressed", String(layerVisibility.place));
  $("#place-filter-off").classList.toggle("is-active", !layerVisibility.place);
}

function updatePlaceCategories(categories) {
  state.placeCategories = [...new Set(categories)].filter((category) => PLACE_CATEGORY_IDS.includes(category));
  renderPlaceFilterControls();
  persist();
  renderPlaces();
}

function setView(view) {
  if (view !== "explore") {
    const page = { planner: "planner.html", companions: "companions.html", community: "community.html", mypage: "mypage.html" }[view];
    if (page) window.location.href = `./${page}`;
    return;
  }
  workspace?.show("explore");
  state.activeView = "explore";
  $$(".app-view").forEach((section) => section.classList.toggle("is-active", section.dataset.view === "explore"));
  $(".search-panel")?.classList.remove("is-open");
  document.body.style.overflow = "";
  persist();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function filteredPlaces() {
  const query = state.search.trim().toLowerCase();
  let result = places.filter((place) => {
    const categoryMatch = state.placeCategories.includes(place.category);
    const textMatch = !query || `${place.title} ${place.region} ${place.categoryLabel}`.toLowerCase().includes(query);
    return categoryMatch && textMatch;
  });
  const sort = $("#sort-select")?.value || "recommended";
  if (sort === "rating") result = [...result].sort((a, b) => String(a.title).localeCompare(String(b.title), "ko"));
  if (sort === "distance") result = [...result].sort((a, b) => a.distance - b.distance);
  return result;
}

function renderPlaces() {
  const result = filteredPlaces();
  $("#result-count").textContent = result.length;
  $("#place-list").innerHTML = result.length
    ? result.map((place) => `
      <article class="place-card ${state.selectedPlaceId === place.id ? "is-selected" : ""}" data-place-id="${place.id}" tabindex="0">
        <div class="place-card-image">
          <img src="${place.image}" alt="${escapeHtml(place.title)}" loading="lazy" />
          <button class="favorite-button ${state.favorites.includes(place.id) ? "is-active" : ""}" type="button" data-favorite-id="${place.id}" aria-label="${escapeHtml(place.title)} 찜하기">♡</button>
        </div>
        <div class="place-card-content">
          <span>${escapeHtml(place.categoryLabel)}</span>
          <h3>${escapeHtml(place.title)}</h3>
          <p>${escapeHtml(place.region)}</p>
          <div class="place-card-meta">${place.contentId ? `<span>관광공사 제공 · ${place.distance}km</span>` : `<span>체험용 여행지</span>`}</div>
        </div>
      </article>`).join("")
    : `<div class="empty-state"><div><strong>검색 결과가 없어요</strong><span>다른 지역이나 카테고리로 검색해보세요.</span></div></div>`;
  renderMarkers(result);
  bindPlaceCards();
}

function bindPlaceCards() {
  $$(".place-card").forEach((card) => {
    const open = () => selectPlace(Number(card.dataset.placeId));
    card.addEventListener("click", (event) => { if (!event.target.closest("[data-favorite-id]")) open(); });
    card.addEventListener("keydown", (event) => { if (event.key === "Enter") open(); });
  });
  $$('[data-favorite-id]').forEach((button) => button.addEventListener("click", (event) => {
    event.stopPropagation();
    toggleFavorite(Number(button.dataset.favoriteId));
  }));
}

function renderMarkers(result = filteredPlaces()) {
  if(workspace && workspace.tab!=="explore") result=[];
  const markerRoot = $("#map-markers");
  markerRoot.innerHTML = result.map((place) => `
    <button class="map-marker ${state.selectedPlaceId === place.id ? "is-active" : ""}" type="button" data-marker-id="${place.id}" style="left:${place.x}%;top:${place.y}%" aria-label="${escapeHtml(place.title)}">
      <span class="map-marker-pin"><span>${place.id}</span></span><span class="map-marker-label">${place.title}</span>
    </button>`).join("");
  $$('[data-marker-id]').forEach((button) => button.addEventListener("click", () => selectPlace(Number(button.dataset.markerId))));
  renderRoute();
  syncKakaoMarkers(result);
}

function syncKakaoMarkers(result = filteredPlaces()) {
  if (!kakaoMap || !window.kakao?.maps) return;
  if(workspace && workspace.tab!=="explore") result=[];
  kakaoMarkers.forEach((marker) => marker.setMap(null));
  kakaoMarkers = result.map((place) => {
    const lat = place.lat || 33.18 + (70 - place.y) * .006;
    const lng = place.lng || 126.2 + place.x * .0065;
    const marker = new window.kakao.maps.Marker({ position: new window.kakao.maps.LatLng(lat, lng), map: layerVisibility.place ? kakaoMap : null, title: place.title });
    window.kakao.maps.event.addListener(marker, "click", () => selectPlace(place.id));
    return marker;
  });
  syncHotplaceLayer();
}

function syncHotplaceLayer() {
  if (!kakaoMap || !window.kakao?.maps) return;
  hotplaceMarkers.forEach((marker) => marker.setMap(null));
  hotplaceMarkers = state.hotplaces.filter(place => Number.isFinite(place.lat) && Number.isFinite(place.lng)).map((place) => {
    const lat = place.lat || 33.18 + (70 - place.y) * .006;
    const lng = place.lng || 126.2 + place.x * .0065;
    const content = document.createElement("button");
    content.className = "hotplace-map-marker";
    content.type = "button";
    content.title = `핫플 · ${place.title}`;
    content.setAttribute("aria-label", `핫플 ${place.title}`);
    content.innerHTML = "<span>♥</span>";
    content.addEventListener("click", () => toast(`${place.title} · ${place.description || place.date || "나의 핫플레이스"}`));
    const marker = new window.kakao.maps.CustomOverlay({
      position: new window.kakao.maps.LatLng(lat, lng),
      map: layerVisibility.hotplace ? kakaoMap : null,
      content,
      yAnchor: 1,
      zIndex: 8,
    });
    return marker;
  });
}

function selectPlace(id) {
  state.selectedPlaceId = id;
  persist();
  renderPlaces();
  showPlaceDetail(placeById(id));
}

function toggleFavorite(id) {
  state.favorites = state.favorites.includes(id) ? state.favorites.filter((item) => item !== id) : [...state.favorites, id];
  persist();
  renderPlaces();
  if ($("#favorite-count")) $("#favorite-count").textContent = state.favorites.length;
  toast(state.favorites.includes(id) ? "찜한 장소에 저장했어요." : "찜 목록에서 삭제했어요.");
}

function addToItinerary(id) {
  if (state.itinerary.includes(id)) {
    toast("이미 일정에 포함된 장소예요.");
    return;
  }
  const place = placeById(id);
  if (place) {
    state.itineraryPlaces ||= {};
    state.itineraryPlaces[id] = { ...place };
  }
  state.itinerary.push(id);
  state.tripSchedule ||= [];
  const lastTime = state.tripSchedule.filter((item) => Number(item.day) === 1).sort((a, b) => String(b.time).localeCompare(String(a.time)))[0]?.time || "07:30";
  const [hour, minute] = lastTime.split(":").map(Number);
  const nextMinutes = Math.min(23 * 60 + 30, hour * 60 + minute + 120);
  state.tripSchedule.push({ placeId: id, day: 1, time: `${String(Math.floor(nextMinutes / 60)).padStart(2, "0")}:${String(nextMinutes % 60).padStart(2, "0")}`, memo: "" });
  persist();
  renderItinerary();
  closeModal();
  $("#trip-panel").classList.add("is-open");
  toast("여행 일정에 장소를 추가했어요.");
}

function syncItineraryOrder() {
  const slots = [...state.tripSchedule].sort((a,b) => Number(a.day)-Number(b.day) || String(a.time).localeCompare(String(b.time))).map(entry => ({day:entry.day,time:entry.time}));
  state.itinerary.forEach((id,index) => { const entry=state.tripSchedule.find(entry=>Number(entry.placeId)===Number(id)); if (entry && slots[index]) Object.assign(entry,slots[index]); });
}
function moveItinerary(index, direction) {
  const next = index + direction;
  if (next < 0 || next >= state.itinerary.length) return;
  [state.itinerary[index], state.itinerary[next]] = [state.itinerary[next], state.itinerary[index]];
  syncItineraryOrder();
  persist();
  renderItinerary();
}

function removeFromItinerary(index) {
  const [removedId] = state.itinerary.splice(index, 1);
  state.tripSchedule = (state.tripSchedule || []).filter((item) => Number(item.placeId) !== Number(removedId));
  persist();
  renderItinerary();
  toast("일정에서 장소를 제외했어요.");
}

function renderItinerary() {
  const items = state.itinerary.map(placeById).filter(Boolean);
  const scheduleById = new Map((state.tripSchedule || []).map((item) => [Number(item.placeId), item]));
  const html = items.length ? items.map((place, index) => `
    <article class="itinerary-item" draggable="true" data-itinerary-index="${index}">
      <span class="stop-number">${index + 1}</span>
      <img class="itinerary-thumb" src="${place.image}" alt="" />
      <div class="itinerary-copy"><h3>${escapeHtml(place.title)}</h3><p>${scheduleById.get(place.id)?.time || (index === 0 ? "09:30" : index === 1 ? "11:20" : "14:10")} · ${place.duration}분</p></div>
      <div class="itinerary-actions"><button type="button" data-move-up="${index}" aria-label="위로 이동">▲</button><button type="button" data-remove-stop="${index}" aria-label="일정에서 삭제">×</button><button type="button" data-move-down="${index}" aria-label="아래로 이동">▼</button></div>
    </article>`).join("") : `<div class="empty-state"><div><strong>아직 일정이 비어 있어요</strong><span>지도에서 장소를 추가해보세요.</span></div></div>`;
  $("#itinerary-list").innerHTML = html;
  const plannerItinerary = $("#planner-itinerary");
  if (plannerItinerary) plannerItinerary.innerHTML = items.length ? items.map((place, index) => `
    <article class="planner-row">
      <div class="planner-row-number"><span>${index === 0 ? "09:30" : index === 1 ? "11:20" : "14:10"}</span><strong>${index + 1}</strong></div>
      <img src="${place.image}" alt="${escapeHtml(place.title)}" />
      <div><h3>${escapeHtml(place.title)}</h3><p>${place.categoryLabel} · ${place.region} · 약 ${place.duration}분</p></div>
      <div class="row-actions"><button type="button" data-move-up="${index}" aria-label="위로 이동">↑</button><button type="button" data-remove-stop="${index}" aria-label="삭제">×</button><button type="button" data-move-down="${index}" aria-label="아래로 이동">↓</button></div>
    </article>`).join("") : html;
  const distance = items.slice(1).reduce((sum, place, index) => sum + distanceMeters(items[index].lat,items[index].lng,place.lat,place.lng)/1000,0);
  const minutes = Math.round(distance * 2.2);
  $("#trip-distance").textContent = `${distance.toFixed(1)} km`;
  $("#trip-duration").textContent = `${Math.floor(minutes / 60)}시간 ${minutes % 60}분`;
  $$('[data-move-up]').forEach((button) => button.addEventListener("click", () => moveItinerary(Number(button.dataset.moveUp), -1)));
  $$('[data-move-down]').forEach((button) => button.addEventListener("click", () => moveItinerary(Number(button.dataset.moveDown), 1)));
  $$('[data-remove-stop]').forEach((button) => button.addEventListener("click", () => removeFromItinerary(Number(button.dataset.removeStop))));
  bindDragAndDrop();
  renderRoute();
}

function bindDragAndDrop() {
  let dragged = null;
  $$('[data-itinerary-index]').forEach((row) => {
    row.addEventListener("dragstart", () => { dragged = Number(row.dataset.itineraryIndex); });
    row.addEventListener("dragover", (event) => event.preventDefault());
    row.addEventListener("drop", (event) => {
      event.preventDefault();
      const target = Number(row.dataset.itineraryIndex);
      if (dragged === null || dragged === target) return;
      const [item] = state.itinerary.splice(dragged, 1);
      state.itinerary.splice(target, 0, item);
      syncItineraryOrder();
      persist(); renderItinerary();
    });
  });
}

function renderRoute() {
  // The map only displays a route after selecting a trip in the sidebar.
  if (kakaoRoute) { kakaoRoute.setMap(null); kakaoRoute=null; }
  if (!workspace || workspace.tab==='explore') $('#route-layer').innerHTML='';
}
function syncKakaoRoute() { renderRoute(); }

function showPlaceDetail(place) {
  if (!place) return;
  openModal(`
    <div class="modal modal-wide detail-layout" role="dialog" aria-modal="true" aria-labelledby="detail-title">
      <div class="detail-photo" style="background-image:linear-gradient(rgb(16 24 39 / 4%),rgb(16 24 39 / 18%)),url('${place.image}')"></div>
      <div class="detail-body">
        <div class="section-title-row"><span class="status-badge navy">${place.categoryLabel}</span><button class="modal-close" type="button" aria-label="닫기">×</button></div>
        <h2 id="detail-title">${escapeHtml(place.title)}</h2><p>${escapeHtml(place.region)}</p>
        <p class="detail-description">${escapeHtml(place.description)}</p>
        <div class="detail-metrics"><div><span>오늘 날씨</span><strong>${place.weather}</strong></div><div><span>일출</span><strong>${place.sunrise}</strong></div><div><span>일몰</span><strong>${place.sunset}</strong></div></div>
        ${!place.contentId ? `<section class="detail-section"><h3>체험용 여행 팁</h3><p>${escapeHtml(place.tip)}</p></section>` : ""}
        <section class="detail-section"><h3>주변 전기차 충전소</h3><p>${liveData.chargerCount === null ? "지도에서 충전소 버튼을 켜면 주변 충전 상태를 확인할 수 있어요." : `지도 주변 충전소 ${liveData.chargerCount.toLocaleString()}건을 실시간 데이터에서 확인했어요.`}</p></section>
        <p class="helper-text">일출·일몰: <a href="https://sunrise-sunset.org/" target="_blank" rel="noopener">Sunrise-Sunset.org</a> · 한국 표준시</p><section class="detail-section"><h3>데이터 안내</h3><p>${liveData.tour ? "한국관광공사 TourAPI 실데이터" : "내장 데모 데이터"} · ${liveData.weather ? "기상청 단기예보 실데이터" : "날씨 버튼에서 예보 확인"} · 한국환경공단 충전소 데이터</p></section>
        <div class="detail-actions"><button class="button button-secondary" type="button" data-modal-favorite="${place.id}">${state.favorites.includes(place.id) ? "찜 해제" : "♡ 찜하기"}</button><button class="button button-primary" type="button" data-add-itinerary="${place.id}">＋ 일정에 추가</button></div>
      </div>
    </div>`);
  $('[data-add-itinerary]').addEventListener("click", () => addToItinerary(place.id));
  $('[data-modal-favorite]').addEventListener("click", () => { toggleFavorite(place.id); showPlaceDetail(place); });
  if (place.contentId) api.getTourDetail(place.contentId).then(detail => {
    if (!detail?.overview || !document.querySelector('[data-add-itinerary]') || Number(document.querySelector('[data-add-itinerary]').dataset.addItinerary) !== place.id) return;
    const description = document.querySelector('.detail-description');
    if (description) description.textContent = detail.overview.replace(/<br\s*\/?\s*>/gi,'\n').replace(/<[^>]*>/g,'');
  }).catch(() => {});
  api.getSunTimes({lat:place.lat,lng:place.lng}).then((result) => {
    if (Number(document.querySelector('[data-add-itinerary]')?.dataset.addItinerary) !== place.id) return;
    const metrics = $$('.detail-metrics strong');
    const times = result?.results || result;
    const formatTime = value => value ? new Date(value).toLocaleTimeString('ko-KR',{hour:'2-digit',minute:'2-digit',hour12:false,timeZone:'Asia/Seoul'}) : '확인 불가';
    if (metrics[1]) metrics[1].textContent = formatTime(times?.sunrise);
    if (metrics[2]) metrics[2].textContent = formatTime(times?.sunset);
  });
}

function showHotplaceForm() {
  if (!ensureLoggedIn(state, "핫플레이스를 등록하려면 로그인해주세요.")) return;
  openModal(formModal("핫플레이스 등록", "나만 알고 싶은 장소와 기억을 지도에 남겨보세요.", `<div class="form-grid"><div class="form-field full"><label for="hotplace-title">장소 이름</label><input id="hotplace-title" placeholder="예: 월정리의 오후" /></div><div class="form-field"><label for="hotplace-type">장소 유형</label><select id="hotplace-type"><option>자연</option><option>카페</option><option>맛집</option><option>문화</option></select></div><div class="form-field"><label for="hotplace-date">방문 날짜</label><input id="hotplace-date" type="date" value="2026-10-02" /></div><div class="form-field full"><label for="hotplace-file">사진 파일</label><input id="hotplace-file" type="file" accept="image/png,image/jpeg,image/webp" /></div><div class="form-field full"><label for="hotplace-photo">또는 사진 URL</label><input id="hotplace-photo" placeholder="비워두면 기본 여행 사진을 사용합니다" /></div><div class="form-field full"><label for="hotplace-description">기억</label><textarea id="hotplace-description" placeholder="이 장소에서의 기억을 기록해보세요."></textarea></div></div>`, "핫플레이스 저장", "hotplace-form"));
  $("#hotplace-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const title = $("#hotplace-title").value.trim();
    if (!title) return toast("장소 이름을 입력해주세요.");
    const file = $("#hotplace-file").files[0];
    if (file && file.size > 1024 * 1024) return toast("사진은 1MB 이하로 등록해주세요.");
    let fileData = "";
    try { if (file) fileData = await readProfileImage(file); } catch (error) { return toast(error.message); }
    state.hotplaces.unshift({ id: Date.now(), title, ownerId:state.user.id, lat:kakaoMap?.getCenter().getLat() || INITIAL_MAP_CENTER.lat, lng:kakaoMap?.getCenter().getLng() || INITIAL_MAP_CENTER.lng, type:$("#hotplace-type").value, date:$("#hotplace-date").value, description:$("#hotplace-description").value.trim(), image: fileData || $("#hotplace-photo").value.trim() || places[Math.floor(Math.random() * places.length)].image });
    try { persist(); } catch { state.hotplaces.shift(); return toast("저장 공간이 부족해 사진을 저장하지 못했어요."); }
    syncHotplaceLayer(); closeModal(); toast("핫플레이스를 등록했어요.");
  });
}

function showProfileForm() { location.href = './mypage.html'; }
function showAccountModal() { location.href = './signup.html?mode=login'; }
function renderUser() {
  $(".trip-panel-header h2").textContent = state.trip.title;
  $("#trip-date-label").textContent = `${state.trip.startDate} — ${state.trip.endDate}`;
  $(".trip-meta button:last-child span").textContent = state.trip.people;
  $(".profile-name").textContent = state.loggedIn ? state.user.name : "로그인";

  if ($("#mypage-title")) $("#mypage-title").textContent = state.loggedIn ? `${state.user.name} 님의 여행 지도` : "로그인이 필요해요";
}

function renderAll() {
  renderPlaces(); renderItinerary(); renderUser();
}

function formModal(title, description, fields, submitLabel, formId) {
  return `<div class="modal" role="dialog" aria-modal="true"><div class="modal-header"><div><h2>${title}</h2><p>${description}</p></div><button class="modal-close" type="button">×</button></div><form class="modal-body" id="${formId}">${fields}<div class="modal-actions"><button class="button button-secondary modal-close-button" type="button">취소</button><button class="button button-primary" type="submit">${submitLabel}</button></div></form></div>`;
}

let modalPreviousFocus = null;
function openModal(content) {
  modalPreviousFocus = document.activeElement;
  $("#modal-root").innerHTML = `<div class="modal-backdrop">${content}</div>`;
  document.body.style.overflow = "hidden";
  $$(".modal-close, .modal-close-button").forEach((button) => button.addEventListener("click", closeModal));
  $(".modal-backdrop").addEventListener("click", (event) => { if (event.target.classList.contains("modal-backdrop")) closeModal(); });
  $(".modal button, .modal input, .modal select")?.focus();
}

function closeModal() {
  $("#modal-root").innerHTML = "";
  document.body.style.overflow = "";
  modalPreviousFocus?.focus();
}

function bindGlobalEvents() {
  $$('[data-view-target]').forEach((button) => button.addEventListener("click", () => setView(button.dataset.viewTarget)));
  $("#global-search-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const keyword = $("#global-search-input").value.trim();
    if (!keyword) return;
    setView("explore");
    state.search = keyword;
    $("#search-input").value = keyword;
    await refreshTourData(keyword);
  });
  $("#search-form").addEventListener("submit", async (event) => { event.preventDefault(); state.search = $("#search-input").value.trim(); persist(); await refreshTourData(state.search); });
  $("#search-input").addEventListener("input", (event) => { state.search = event.target.value; renderPlaces(); });
  $("#sort-select").addEventListener("change", renderPlaces);
  $("#place-filter-toggle").addEventListener("click", () => {
    const panel = $("#place-filter-panel");
    const isOpen = panel.hidden;
    panel.hidden = !isOpen;
    $("#place-filter-toggle").setAttribute("aria-expanded", String(isOpen));
  });
  $$('[data-place-category]').forEach((button) => button.addEventListener("click", () => {
    const category = button.dataset.placeCategory;
    if (category === "all") updatePlaceCategories(PLACE_CATEGORY_IDS);
    else if (state.placeCategories.includes(category)) updatePlaceCategories(state.placeCategories.filter((item) => item !== category));
    else updatePlaceCategories([...state.placeCategories, category]);
  }));
  $("#place-filter-off").addEventListener("click", () => updatePlaceCategories([]));
  $("#map-search-trigger").addEventListener("click", () => { $(".search-panel").classList.add("is-open"); $("#search-input").focus(); });
  $("#mobile-search-fab").addEventListener("click", () => $(".search-panel").classList.add("is-open"));
  $("#mobile-panel-close").addEventListener("click", () => $(".search-panel").classList.remove("is-open"));
  $("#filter-open-button").addEventListener("click", () => $(".search-panel").classList.add("is-open"));
  $("#search-panel-toggle").addEventListener("click", () => {
    const view = $("#view-explore");
    const collapsed = view.classList.toggle("search-collapsed");
    $("#search-panel-toggle").setAttribute("aria-expanded", String(!collapsed));
    $("#search-panel-toggle").setAttribute("aria-label", collapsed ? "여행지 정보 펼치기" : "여행지 정보 접기");
    setTimeout(() => kakaoMap?.relayout(), 260);
  });
  $$(".trip-meta button").forEach(button=>button.addEventListener("click",()=>location.href="./planner.html"));
  $("#trip-collapse-button").addEventListener("click", () => $("#trip-panel").classList.remove("is-open"));

  $("#refresh-nearby-button").addEventListener("click", () => refreshNearbyFromMap(true));
  $("#add-stop-button").addEventListener("click", () => { $(".search-panel").classList.add("is-open"); toast("지도에서 추가할 장소를 선택하세요."); });
  $("#save-trip-button")?.addEventListener("click", () => { persist(); toast("여행 일정을 저장했어요."); });
  $("#planner-save-button")?.addEventListener("click", () => { persist(); toast("변경사항을 저장했어요."); });
  $("#share-trip-button").addEventListener("click", async () => { location.href = "./planner.html?publish=1"; });
  $("#locate-button").addEventListener("click", async () => {
    const location = await getCurrentLocation();
    if (!location) return toast("위치 권한을 허용하면 현재 위치를 표시할 수 있어요.");
    if (kakaoMap) kakaoMap.panTo(new window.kakao.maps.LatLng(location.lat, location.lng));
    if (layerVisibility.charger) await refreshChargerLayer();
    toast("현재 위치로 지도를 이동했어요.");
  });
  $("#zoom-in").addEventListener("click", () => {
    if (kakaoMap) kakaoMap.setLevel(Math.max(1, kakaoMap.getLevel() - 1), { animate: true });
    else $(".map-pattern").style.transform = "rotate(-4deg) scale(1.3)";
  });
  $("#zoom-out").addEventListener("click", () => {
    if (kakaoMap) kakaoMap.setLevel(Math.min(14, kakaoMap.getLevel() + 1), { animate: true });
    else $(".map-pattern").style.transform = "rotate(-4deg) scale(1)";
  });
  $("#map-type-toggle").addEventListener("click", () => {
    const menu = $("#map-type-menu");
    const isOpen = menu.hidden;
    menu.hidden = !isOpen;
    $("#map-type-toggle").classList.toggle("is-active", isOpen);
    $("#map-type-toggle").setAttribute("aria-expanded", String(isOpen));
  });
  $$('[data-map-base]').forEach((button) => button.addEventListener("click", () => {
    selectedMapType = button.dataset.mapBase;
    terrainEnabled=false; $("#terrain-toggle").setAttribute("aria-pressed","false");
    if(selectedMapType==='roadview') toast("파란색 도로를 더블클릭하면 로드뷰가 열려요.");
    $$('[data-map-base]').forEach((item) => {
      const selected = item === button;
      item.classList.toggle("is-active", selected);
      item.setAttribute("aria-checked", String(selected));
    });
    applyMapType();
  }));
  $("#terrain-toggle").addEventListener("click", () => {
    terrainEnabled = !terrainEnabled;
    if(terrainEnabled){selectedMapType="roadmap";$$(`[data-map-base]`).forEach(b=>{b.classList.remove("is-active");b.setAttribute("aria-checked","false");});}
    $("#terrain-toggle").setAttribute("aria-pressed", String(terrainEnabled));
    applyMapType();
  });
  document.addEventListener("click", (event) => {
    if (!event.target.closest(".map-type-picker")) {
      $("#map-type-menu").hidden = true;
      $("#map-type-toggle").classList.remove("is-active");
      $("#map-type-toggle").setAttribute("aria-expanded", "false");
    }
    if (!event.target.closest("#place-filter-toggle, #place-filter-panel")) {
      $("#place-filter-panel").hidden = true;
      $("#place-filter-toggle").setAttribute("aria-expanded", "false");
    }
  });
  $$('[data-layer]').forEach((button) => button.addEventListener("click", async () => {
    const layer = button.dataset.layer;
    layerVisibility[layer] = !layerVisibility[layer];
    button.classList.toggle("is-active", layerVisibility[layer]);
    button.setAttribute("aria-pressed", String(layerVisibility[layer]));
    await updateMapLayer(layer);
    if (layer === "charger" && layerVisibility.charger && kakaoMap?.getLevel() > CHARGER_VISIBLE_MAX_MAP_LEVEL) {
      toast("충전소는 지도를 확대된 지도까지 확대하면 표시돼요.");
    } else {
      toast(`${button.textContent.trim()} 표시를 ${layerVisibility[layer] ? "켰어요" : "껐어요"}.`);
    }
  }));



  $$('[data-board]').forEach((button) => button.addEventListener("click", () => { state.currentBoard = button.dataset.board; persist();  }));

  $("#add-hotplace-button")?.addEventListener("click", showHotplaceForm);
  $("#profile-edit-button")?.addEventListener("click", showProfileForm);
  $("#account-button")?.addEventListener("click", () => showAccountModal());
  document.addEventListener("keydown", (event) => {
    if (event.key === 'Tab' && $('.modal')) {
      const nodes = [...$('.modal').querySelectorAll('a[href],button,input,select,textarea')].filter(node=>!node.disabled && !node.hidden);
      const first=nodes[0],last=nodes.at(-1);
      if (event.shiftKey && document.activeElement===first) {event.preventDefault();last?.focus();}
      else if (!event.shiftKey && document.activeElement===last) {event.preventDefault();first?.focus();}
    }
    if (event.key === "Escape") closeModal(); if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); setView("explore"); $(".search-panel").classList.add("is-open"); $("#search-input").focus(); } });
}

async function initKakaoMap() {
  const loaded = await api.loadKakaoMap();
  if (!loaded || !window.kakao?.maps) return;
  const stage = $("#map-stage");
  const mapElement = document.createElement("div");
  mapElement.id = "kakao-map";
  Object.assign(mapElement.style, { position: "absolute", inset: "0", zIndex: "1" });
  stage.prepend(mapElement);
  kakaoMap = new window.kakao.maps.Map(mapElement, {
    center: new window.kakao.maps.LatLng(INITIAL_MAP_CENTER.lat, INITIAL_MAP_CENTER.lng),
    level: 9,
    disableDoubleClickZoom: true,
  });
  bindRoadview(kakaoMap,()=>selectedMapType);
  workspace?.refresh();
  kakaoMap.setDraggable(true);
  kakaoMap.setZoomable(true);
  stage.classList.add("has-live-map");
  applyMapType();
  syncKakaoMarkers();
  syncKakaoRoute();
  window.kakao.maps.event.addListener(kakaoMap, "idle", () => {
    clearTimeout(mapIdleTimer);
    mapIdleTimer = setTimeout(() => {
      refreshNearbyFromMap();
      if (layerVisibility.charger) refreshChargerLayer();
    }, 350);
  });
  await refreshNearbyFromMap(true);
}

function mapRadiusMeters() {
  return NEARBY_RADIUS_METERS;
}

function distanceMeters(lat1, lng1, lat2, lng2) {
  const rad = (value) => value * Math.PI / 180;
  const dLat = rad(lat2 - lat1);
  const dLng = rad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function chargersWithinRadius(chargers, origin, radius) {
  return chargers.filter((charger) => {
    const lat = Number(charger.lat);
    const lng = Number(charger.lng);
    return Number.isFinite(lat)
      && Number.isFinite(lng)
      && distanceMeters(origin.lat, origin.lng, lat, lng) <= radius;
  });
}

async function refreshNearbyFromMap(force = false) {
  if (!kakaoMap) return;
  const center = kakaoMap.getCenter();
  const radius = mapRadiusMeters();
  const fetchKey = `${center.getLng().toFixed(3)}:${center.getLat().toFixed(3)}:${Math.round(radius / 1000)}`;
  if (!force && fetchKey === lastMapFetchKey) return;
  lastMapFetchKey = fetchKey;
  const status = $("#live-data-status");
  status.textContent = "현재 지도 검색 중";
  status.className = "live-data-status";
  try {
    const items = await api.searchNearby({ lng: center.getLng(), lat: center.getLat(), radius });
    if (fetchKey !== lastMapFetchKey) return;
    if (!items?.length) {
      if (items === null) { renderPlaces(); status.textContent = "데모 데이터"; return; }
      places.splice(0, places.length);
      renderPlaces();
      status.textContent = "이 위치에 결과 없음";
      status.classList.add("is-fallback");
      return;
    }
    places.splice(0, places.length, ...items.map(mapTourPlace));
    liveData.tour = true;
    state.search = "";
    $("#search-input").value = "";
    state.selectedPlaceId = places[0].id;
    renderPlaces();
    renderItinerary();
    status.textContent = `지도 중심 ${Math.round(radius / 1000)}km`;
    status.classList.add("is-live");
    if (layerVisibility.weather) await refreshWeatherLayer();
  } catch {
    status.textContent = "지도 데이터 재시도";
    status.classList.add("is-fallback");
  }
}

function latLngToGrid(lat, lon) {
  const RE = 6371.00877, GRID = 5, SLAT1 = 30, SLAT2 = 60, OLON = 126, OLAT = 38, XO = 43, YO = 136;
  const DEGRAD = Math.PI / 180;
  const re = RE / GRID;
  const slat1 = SLAT1 * DEGRAD, slat2 = SLAT2 * DEGRAD;
  let sn = Math.tan(Math.PI * .25 + slat2 * .5) / Math.tan(Math.PI * .25 + slat1 * .5);
  sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
  let sf = Math.tan(Math.PI * .25 + slat1 * .5);
  sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
  let ro = Math.tan(Math.PI * .25 + OLAT * DEGRAD * .5);
  ro = re * sf / Math.pow(ro, sn);
  let ra = Math.tan(Math.PI * .25 + lat * DEGRAD * .5);
  ra = re * sf / Math.pow(ra, sn);
  let theta = lon * DEGRAD - OLON * DEGRAD;
  if (theta > Math.PI) theta -= 2 * Math.PI;
  if (theta < -Math.PI) theta += 2 * Math.PI;
  theta *= sn;
  return { nx: Math.floor(ra * Math.sin(theta) + XO + .5), ny: Math.floor(ro - ra * Math.cos(theta) + YO + .5) };
}

async function refreshWeatherLayer() {
  if (!layerVisibility.weather) return;
  const center = kakaoMap ? kakaoMap.getCenter() : { getLat: () => INITIAL_MAP_CENTER.lat, getLng: () => INITIAL_MAP_CENTER.lng };
  const grid = latLngToGrid(center.getLat(), center.getLng());
  $("#weather-map-badge").innerHTML = `<p class="weather-loading">5일 날씨를 불러오는 중이에요.</p>`;
  const weatherKey = `${grid.nx}:${grid.ny}`;
  latestWeatherKey = weatherKey;
  const weather = await api.getWeather({ ...grid, ...weatherBase() }).catch(() => null);
  if (!layerVisibility.weather || latestWeatherKey !== weatherKey) return;
  const dailyForecast = summarizeDailyWeather(weather);
  liveData.weather = summarizeWeather(weather);
  renderWeatherForecast(dailyForecast);
  places.forEach((place) => { place.weather = liveData.weather || "예보 확인 불가"; });
  renderPlaces();
}

function renderWeatherForecast(days) {
  const root = $("#weather-map-badge");
  if (!days.length) {
    root.innerHTML = `<p class="weather-loading">예보 정보를 불러오지 못했어요.</p>`;
    return;
  }
  root.innerHTML = `
    <div class="weather-forecast-heading"><strong>지도 중심 ${days.length}일 예보</strong><span>기상청 단기예보</span></div>
    <div class="weather-table-scroll">
      <table class="weather-forecast-table">
        <thead><tr>${days.map((day, index) => `<th class="${index === 0 ? "is-today" : ""}" scope="col">${index === 0 ? "오늘 " : ""}${day.dateLabel}</th>`).join("")}</tr></thead>
        <tbody><tr>${days.map((day) => `<td><span class="forecast-icon" role="img" aria-label="${day.label}" title="${day.label}">${day.icon}</span><span class="forecast-label">${day.label}</span><span class="forecast-temperature"><span class="low">${day.minTemp}</span> / <span class="high">${day.maxTemp}</span></span></td>`).join("")}</tr></tbody>
      </table>
    </div>`;
}

async function refreshChargerLayer() {
  if (!kakaoMap || !layerVisibility.charger) {
    if (!kakaoMap && layerVisibility.charger) { $("#charger-status").hidden = false; $("#charger-status").textContent = "충전소는 카카오 지도가 연결되면 사용할 수 있어요."; }
    return;
  }
  if (kakaoMap.getLevel() > CHARGER_VISIBLE_MAX_MAP_LEVEL) {
    chargerMarkers.forEach(({ marker, info }) => { marker.setMap(null); info.setMap(null); });
    chargerMarkers = [];
    $("#charger-status").hidden = false;
    $("#charger-status").textContent = "충전소를 보려면 지도를 조금 더 확대하세요.";
    return;
  }
  const revision = ++chargerRevision;
  const center = kakaoMap.getCenter();
  const currentLocation = { lat: center.getLat(), lng: center.getLng() };
  const status = $('#charger-status');
  status.hidden = false; status.textContent = '현재 지도 지역 충전소를 불러오는 중…';
  const zcode = await new Promise(resolve => {
    new window.kakao.maps.services.Geocoder().coord2RegionCode(currentLocation.lng, currentLocation.lat, (result, code) => {
      resolve(code === window.kakao.maps.services.Status.OK ? result.find(region => region.region_type === 'B')?.code.slice(0, 2) : null);
    });
  });
  if (revision !== chargerRevision || !layerVisibility.charger) return;
  if (!zcode) { status.textContent = '지역을 확인하지 못했어요. 지도를 이동해 다시 시도하세요.'; return; }
  try {
    const result = await api.getRegionalEvChargers({ zcode, onProgress: progress => {
      if (revision !== chargerRevision || !layerVisibility.charger) return;
      renderChargerStations(progress.items,currentLocation);
      status.textContent = `현재 지도 주변 ${chargerMarkers.length}곳 · 지역 데이터 조회 중…`;
    } });
    if (revision !== chargerRevision || !layerVisibility.charger) return;
    renderChargerStations(result.items, currentLocation);
    status.textContent = `현재 지도 주변 충전소 ${chargerMarkers.length}곳 · ${result.partial ? '지역 일부 조회 · ' : ''}최대 80곳 표시 · 조회 ${new Date(result.updatedAt).toLocaleTimeString('ko-KR')}`;
  } catch {
    if (revision === chargerRevision && layerVisibility.charger) status.textContent = '충전소 조회 실패 · 버튼을 껐다 켜서 다시 시도하세요.';
  }
}

function renderChargerStations(chargers, currentLocation) {
  chargerMarkers.forEach(({ marker, info }) => { marker.setMap(null); info.setMap(null); });
  const bounds = kakaoMap.getBounds();
  const nearby = chargersWithinRadius(chargers, currentLocation, NEARBY_RADIUS_METERS).filter(charger => bounds.contain(new window.kakao.maps.LatLng(Number(charger.lat), Number(charger.lng))));
  const stations = [...nearby.reduce((groups, charger) => {
    const key = charger.statId || `${charger.statNm}:${charger.lat}:${charger.lng}`;
    if (!groups.has(key)) groups.set(key, { name: charger.statNm || '전기차 충전소', address: charger.addr || '', lat: Number(charger.lat), lng: Number(charger.lng), chargers: [] });
    groups.get(key).chargers.push(charger);
    return groups;
  }, new Map()).values()].sort((a,b) => distanceMeters(currentLocation.lat,currentLocation.lng,a.lat,a.lng) - distanceMeters(currentLocation.lat,currentLocation.lng,b.lat,b.lng)).slice(0,80);

  chargerMarkers = stations.map((station) => {
    const position = new window.kakao.maps.LatLng(station.lat, station.lng);
    const available = station.chargers.filter((charger) => String(charger.stat) === "2").length;
    const charging = station.chargers.filter((charger) => String(charger.stat) === "3").length;
    const markerButton = document.createElement("button");
    markerButton.className = "charger-map-marker";
    markerButton.type = "button";
    markerButton.title = `${station.name} · ${available}대 충전 가능`;
    markerButton.setAttribute("aria-label", `${station.name}, 총 ${station.chargers.length}대 중 ${available}대 충전 가능`);
    markerButton.innerHTML = '<span aria-hidden="true">ϟ</span>';

    const infoContent = document.createElement("section");
    infoContent.className = "charger-info-window";
    infoContent.innerHTML = `
      <div class="charger-info-heading">
        <div><strong>${escapeHtml(station.name)}</strong><small>${escapeHtml(station.address)}</small></div>
        <button type="button" aria-label="충전소 정보 닫기">×</button>
      </div>
      <div class="charger-availability">
        <strong><b>${available}</b>대 충전 가능</strong>
        <span>전체 ${station.chargers.length}대 · 충전 중 ${charging}대</span>
      </div>
      <div class="charger-status-list">
        ${station.chargers.slice(0, 8).map((charger, index) => {
          const status = String(charger.stat);
          const label = { 1: "통신 이상", 2: "충전 가능", 3: "충전 중", 4: "운영 중지", 5: "점검 중", 9: "상태 미확인" }[status] || "상태 미확인";
          const tone = status === "2" ? "ready" : status === "3" ? "charging" : "unavailable";
          return `<span class="charger-status ${tone}">${escapeHtml(charger.chgerId || String(index + 1).padStart(2, "0"))} · ${label}</span>`;
        }).join("")}
      </div>`;

    const marker = new window.kakao.maps.CustomOverlay({ position, map: kakaoMap, content: markerButton, yAnchor: 1, zIndex: 8 });
    const info = new window.kakao.maps.CustomOverlay({ position, content: infoContent, yAnchor: 1.22, zIndex: 20 });
    const entry = { marker, info, open: false };
    const close = () => { info.setMap(null); entry.open = false; markerButton.classList.remove("is-active"); };
    markerButton.addEventListener("click", () => {
      chargerMarkers.forEach((item) => { if (item !== entry) { item.info.setMap(null); item.open = false; item.marker.getContent().classList.remove("is-active"); } });
      if (entry.open) close();
      else { info.setMap(kakaoMap); entry.open = true; markerButton.classList.add("is-active"); }
    });
    infoContent.querySelector("button").addEventListener("click", close);
    return entry;
  });
  liveData.chargerCount = stations.length;
}

async function updateMapLayer(layer) {
  if (layer === "place") kakaoMarkers.forEach((marker) => marker.setMap(layerVisibility.place ? kakaoMap : null));
  if (layer === "hotplace") syncHotplaceLayer();
  if (layer === 'weather') {
    state.weatherVisible = layerVisibility.weather; persist();
    $('#weather-map-badge').hidden = !layerVisibility.weather;
    if (layerVisibility.weather) await refreshWeatherLayer();
  }
  if (layer === "charger") {
    ++chargerRevision;
    $('#charger-status').hidden = !layerVisibility.charger;
    if (layerVisibility.charger) await refreshChargerLayer();
    else chargerMarkers.forEach(({ marker, info }) => { marker.setMap(null); info.setMap(null); });
  }
}

function weatherBase() {
  const now = new Date(Date.now() + 9 * 60 * 60 * 1000);
  const bases = [2, 5, 8, 11, 14, 17, 20, 23];
  let hour = now.getUTCHours();
  let baseHour = [...bases].reverse().find((value) => value <= hour);
  if (baseHour === undefined) {
    now.setUTCDate(now.getUTCDate() - 1);
    baseHour = 23;
  }
  return {
    baseDate: `${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, "0")}${String(now.getUTCDate()).padStart(2, "0")}`,
    baseTime: `${String(baseHour).padStart(2, "0")}00`,
  };
}

function summarizeDailyWeather(items) {
  if (!items?.length) return [];
  const grouped = items.reduce((result, item) => {
    (result[item.fcstDate] ||= []).push(item);
    return result;
  }, {});
  const skyMap = { 1: ["맑음", "☀️"], 3: ["구름 많음", "🌤️"], 4: ["흐림", "☁️"] };
  const rainMap = { 1: ["비", "🌧️"], 2: ["비·눈", "🌨️"], 3: ["눈", "🌨️"], 4: ["소나기", "🌦️"], 5: ["빗방울", "🌦️"], 6: ["빗방울·눈", "🌨️"], 7: ["눈날림", "🌨️"] };
  const formatTemp = (value) => Number.isFinite(value) ? `${Math.round(value)}°` : "-";
  return Object.entries(grouped).sort(([a], [b]) => a.localeCompare(b)).slice(0, 5).map(([date, values]) => {
    const daytime = values.filter((item) => Number(item.fcstTime) >= 600 && Number(item.fcstTime) <= 1800);
    const source = daytime.length ? daytime : values;
    const precipitation = source.find((item) => item.category === "PTY" && Number(item.fcstValue) > 0);
    const skies = source.filter((item) => item.category === "SKY");
    const noonSky = skies.reduce((closest, item) => Math.abs(Number(item.fcstTime) - 1200) < Math.abs(Number(closest?.fcstTime ?? 9999) - 1200) ? item : closest, null);
    const [label, icon] = precipitation ? rainMap[Number(precipitation.fcstValue)] : (skyMap[Number(noonSky?.fcstValue)] || ["날씨", "🌡️"]);
    const temperatures = values.filter((item) => item.category === "TMP").map((item) => Number(item.fcstValue)).filter(Number.isFinite);
    const tmn = Number(values.find((item) => item.category === "TMN")?.fcstValue);
    const tmx = Number(values.find((item) => item.category === "TMX")?.fcstValue);
    const min = Number.isFinite(tmn) ? tmn : Math.min(...temperatures);
    const max = Number.isFinite(tmx) ? tmx : Math.max(...temperatures);
    return { dateLabel: `${Number(date.slice(4, 6))}/${Number(date.slice(6, 8))}`, label, icon, minTemp: formatTemp(min), maxTemp: formatTemp(max) };
  });
}

function summarizeWeather(items) {
  const today = summarizeDailyWeather(items)[0];
  return today ? `${today.label} ${today.minTemp}/${today.maxTemp}` : null;
}

function mapTourPlace(item, index) {
  const type = { 15: ["festival", "공연·행사"], 38: ["shopping", "쇼핑"], 25: ["course", "여행코스"], 12: ["attraction", "관광지"], 14: ["culture", "문화시설"], 28: ["course", "레포츠"], 32: ["stay", "숙소"], 39: ["food", "음식점"] }[item.contenttypeid] || ["attraction", "여행지"];
  const lng = Number(item.mapx) || 126.2 + (index % 8) * .08;
  const lat = Number(item.mapy) || 33.2 + (index % 5) * .06;
  const fallback = fallbackPlaces[index % fallbackPlaces.length];
  return {
    ...fallback,
    id: Number(item.contentid) || index + 1,
    contentId: item.contentid,
    title: item.title || fallback.title,
    region: item.addr1 || "제주특별자치도",
    category: type[0],
    categoryLabel: type[1],
    description: `${item.title || "제주 여행지"}의 관광 정보입니다. 일정에 추가해 나만의 여행 동선을 만들어보세요.`,
    image: item.firstimage || item.firstimage2 || "./assets/sunset-clouds.png",
    lng,
    lat,
    x: Math.max(8, Math.min(92, ((lng - 126.1) / .85) * 84 + 8)),
    y: Math.max(8, Math.min(85, 85 - ((lat - 33.1) / .5) * 77)),
    rating: 0, reviews: 0, weather: '날씨 버튼에서 확인', sunrise: '조회 중', sunset: '조회 중',
    distance: Number((Number(item.dist) / 1000 || distanceMeters(kakaoMap?.getCenter().getLat() || INITIAL_MAP_CENTER.lat,kakaoMap?.getCenter().getLng() || INITIAL_MAP_CENTER.lng,lat,lng)/1000).toFixed(1)),
  };
}

async function refreshTourData(keyword = "") {
  const status = $("#live-data-status");
  status.textContent = "TourAPI 불러오는 중";
  status.className = "live-data-status";
  try {
    const items = await api.searchTour({ keyword, areaCode: keyword ? "" : 39 });
    if (!items?.length) {
      if (Array.isArray(items)) places.splice(0, places.length);
      renderPlaces();
      status.textContent = items === null ? "데모 데이터" : "검색 결과 없음";
      status.classList.add("is-fallback");
      return;
    }
    places.splice(0, places.length, ...items.map(mapTourPlace));
    liveData.tour = true;
    state.selectedPlaceId = places.some((place) => place.id === state.selectedPlaceId) ? state.selectedPlaceId : places[0].id;
    renderPlaces();
    renderItinerary();
    if (keyword && kakaoMap && places[0]?.lat && places[0]?.lng) kakaoMap.panTo(new window.kakao.maps.LatLng(places[0].lat, places[0].lng));
    status.textContent = "TourAPI LIVE";
    status.classList.add("is-live");
  } catch {
    renderPlaces();
    status.textContent = "데모 데이터";
    status.classList.add("is-fallback");
  }
}

async function hydrateLiveData() {
  if (!api.hasKakaoMap) await refreshTourData(state.search);
  if (layerVisibility.weather && !kakaoMap) refreshWeatherLayer();
}

function init() {
  $("#weather-map-badge").hidden = !layerVisibility.weather;
  renderAll();
  renderPlaceFilterControls();
  if ($("#favorite-count")) $("#favorite-count").textContent = state.favorites.length;
  $("#search-input").value = state.search;
  $$('[data-layer]').forEach(item => {item.setAttribute('aria-pressed', String(layerVisibility[item.dataset.layer]));item.classList.toggle('is-active',layerVisibility[item.dataset.layer]);});
  workspace=mountWorkspace(state,()=>kakaoMap,()=>{renderItinerary();renderMarkers();});
  bindGlobalEvents();
  setView("explore");
  if(sessionStorage.getItem("neorang-workspace-return")==="mine"){sessionStorage.removeItem("neorang-workspace-return");workspace.resume();}
  const browseReturn = sessionStorage.getItem('neorang-browse-return');
  if(browseReturn){sessionStorage.removeItem('neorang-browse-return');workspace.openCompanion(browseReturn);}
  initKakaoMap();
  hydrateLiveData();
}

init();
