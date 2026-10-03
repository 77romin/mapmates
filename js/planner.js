import { places } from "./data.js";
import { api } from "./api.js";
import { escapeHtml, initShell, loadState, saveState, showToast, ensureLoggedIn, todayLabel } from "./shared.js";

const state = initShell("planner", loadState());
let activeDay = 1;
let map = null;
let routeLine = null;
let routeMarkers = [];
const $ = (selector) => document.querySelector(selector);
const placeById = (id) => places.find((place) => place.id === Number(id)) || state.itineraryPlaces?.[Number(id)] || null;
const placePoint = (place) => ({ ...place, lat: Number(place.lat) || 33.18 + (70 - Number(place.y || 35)) * .006, lng: Number(place.lng) || 126.2 + Number(place.x || 50) * .0065 });

const days = () => Array.from({length: Math.max(1, Math.min(14, Math.round((new Date(state.trip.endDate) - new Date(state.trip.startDate))/86400000) + 1))}, (_,index) => index + 1);
state.trip.id ||= crypto.randomUUID();
let routeRevision = 0;
function dayDate(day) {
  const date = new Date(`${state.trip.startDate}T00:00:00`);
  date.setDate(date.getDate() + day - 1);
  return date;
}

function syncSchedule() {
  const scheduledIds = new Set(state.tripSchedule.map((item) => Number(item.placeId)));
  state.itinerary.forEach((placeId, index) => {
    if (!scheduledIds.has(Number(placeId))) state.tripSchedule.push({ placeId: Number(placeId), day: 1, time: `${String(9 + index * 2).padStart(2, "0")}:30`, memo: "" });
  });
  state.tripSchedule = state.tripSchedule.filter((item) => state.itinerary.includes(Number(item.placeId)));
}

function currentSchedule() {
  return state.tripSchedule
    .filter((item) => Number(item.day) === activeDay)
    .sort((a, b) => String(a.time).localeCompare(String(b.time)));
}

function renderTabs() {
  $("#day-tabs").innerHTML = days().map((day) => {
    const date = dayDate(day);
    return `<button class="${activeDay === day ? "is-active" : ""}" type="button" data-day="${day}">DAY ${day} <small>${date.getMonth() + 1}.${String(date.getDate()).padStart(2, "0")}</small></button>`;
  }).join("");
  document.querySelectorAll("[data-day]").forEach((button) => button.addEventListener("click", () => {
    activeDay = Number(button.dataset.day);
    renderTabs();
    renderSchedule();
    drawRoute();
  }));
}

function renderSchedule() {
  const schedule = currentSchedule();
  $("#schedule-list").innerHTML = schedule.length ? schedule.map((item) => {
    const place = placeById(item.placeId);
    if (!place) return "";
    return `<article class="schedule-row" data-place-id="${place.id}">
      <input class="schedule-time" type="time" value="${escapeHtml(item.time)}" aria-label="${escapeHtml(place.title)} 여행 시각" data-time-id="${place.id}" />
      <img src="${place.image}" alt="" />
      <div class="schedule-copy"><div class="schedule-heading"><h3>${escapeHtml(place.title)}</h3><div><select data-schedule-day="${place.id}" aria-label="${escapeHtml(place.title)} 여행 일자">${days().map((day) => `<option value="${day}" ${Number(item.day) === day ? "selected" : ""}>DAY ${day}</option>`).join("")}</select><button type="button" data-remove-schedule="${place.id}" aria-label="${escapeHtml(place.title)} 일정에서 삭제">×</button></div></div><textarea data-memo-id="${place.id}" aria-label="${escapeHtml(place.title)} 메모" placeholder="메모를 입력하세요">${escapeHtml(item.memo)}</textarea></div>
    </article>`;
  }).join("") : `<div class="empty-state"><div><strong>이 날짜에는 일정이 없어요.</strong><span>지도에서 여행지를 추가하거나 다른 날짜를 선택하세요.</span></div></div>`;
  document.querySelectorAll("[data-time-id]").forEach((input) => input.addEventListener("change", () => {
    const item = state.tripSchedule.find((entry) => Number(entry.placeId) === Number(input.dataset.timeId));
    item.time = input.value;
    saveState(state);
    renderSchedule();
    drawRoute();
  }));
  document.querySelectorAll("[data-memo-id]").forEach((input) => input.addEventListener("input", () => {
    const item = state.tripSchedule.find((entry) => Number(entry.placeId) === Number(input.dataset.memoId));
    item.memo = input.value;
    saveState(state);
  }));
  document.querySelectorAll("[data-schedule-day]").forEach((select) => select.addEventListener("change", () => {
    const item = state.tripSchedule.find((entry) => Number(entry.placeId) === Number(select.dataset.scheduleDay));
    item.day = Number(select.value);
    saveState(state);
    renderSchedule();
    drawRoute();
    showToast(`DAY ${item.day} 일정으로 이동했어요.`);
  }));
  document.querySelectorAll("[data-remove-schedule]").forEach((button) => button.addEventListener("click", () => {
    const placeId = Number(button.dataset.removeSchedule);
    state.itinerary = state.itinerary.filter((id) => Number(id) !== placeId);
    state.tripSchedule = state.tripSchedule.filter((item) => Number(item.placeId) !== placeId);
    if (!places.some((place) => place.id === placeId)) delete state.itineraryPlaces?.[placeId];
    saveState(state);
    renderSchedule();
    drawRoute();
    renderWeather();
    showToast("일정에서 여행지를 삭제했어요.");
  }));
}

function routePlaces() {
  return currentSchedule().map((item) => placeById(item.placeId)).filter(Boolean);
}

function haversine(a, b) {
  a = placePoint(a);
  b = placePoint(b);
  const rad = (value) => value * Math.PI / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const value = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
}

function updateRouteSummary(distanceKm, minutes) {
  $("#route-distance").textContent = `${distanceKm.toFixed(1)} km`;
  $("#route-duration").textContent = minutes ? `${Math.floor(minutes / 60)}시간 ${Math.round(minutes % 60)}분` : "-";
}

async function drawRoute() {
  const revision = ++routeRevision;
  const stops = routePlaces();
  const estimate = stops.slice(1).reduce((sum, place, index) => sum + haversine(stops[index], place), 0);
  updateRouteSummary(estimate, estimate * 2.2);
  if (!map || !window.kakao?.maps) return;
  if (routeLine) routeLine.setMap(null);
  routeMarkers.forEach((marker) => marker.setMap(null));
  routeMarkers = [];
  const items = routePlaces();
  if (!items.length) { $("#route-status").textContent = "지도에서 여행지를 추가해 경로를 만들어보세요."; return; }
  const bounds = new window.kakao.maps.LatLngBounds();
  const directPath = items.map((place) => {
    const coordinates = placePoint(place);
    const point = new window.kakao.maps.LatLng(coordinates.lat, coordinates.lng);
    bounds.extend(point);
    routeMarkers.push(new window.kakao.maps.Marker({ map, position: point, title: place.title }));
    return point;
  });
  map.setBounds(bounds);
  if (state.routeMode === "car" && items.length > 1) {
    $("#route-status").textContent = "카카오모빌리티 차량 경로를 불러오는 중이에요.";
    const result = await api.getCarDirections({ origin: placePoint(items[0]), destination: placePoint(items.at(-1)), waypoints: items.slice(1, -1).map(placePoint) });
    if (revision !== routeRevision) return;
    const vertices = result?.sections?.flatMap((section) => section.roads?.flatMap((road) => road.vertexes || []) || []) || [];
    if (vertices.length >= 4) {
      const carPath = [];
      for (let index = 0; index < vertices.length; index += 2) carPath.push(new window.kakao.maps.LatLng(vertices[index + 1], vertices[index]));
      routeLine = new window.kakao.maps.Polyline({ map, path: carPath, strokeWeight: 6, strokeColor: "#0872ef", strokeOpacity: .88, strokeStyle: "solid" });
      $("#route-status").textContent = "카카오모빌리티 추천 차량 경로입니다.";
      updateRouteSummary((result.summary?.distance || 0) / 1000, (result.summary?.duration || 0) / 60);
      return;
    }
    $("#route-status").textContent = "차량 경로를 가져오지 못해 직선 경로로 표시합니다.";
  } else {
    $("#route-status").textContent = "여행지 사이를 직선 경로로 표시하고 있어요.";
  }
  routeLine = new window.kakao.maps.Polyline({ map, path: directPath, strokeWeight: 5, strokeColor: "#dc3545", strokeOpacity: .8, strokeStyle: "solid" });
  const distance = items.slice(1).reduce((sum, item, index) => sum + haversine(items[index], item), 0);
  updateRouteSummary(distance, distance * 2.2);
}

function latLngToGrid(lat, lon) {
  const RE = 6371.00877, GRID = 5, SLAT1 = 30, SLAT2 = 60, OLON = 126, OLAT = 38, XO = 43, YO = 136;
  const DEGRAD = Math.PI / 180, re = RE / GRID, slat1 = SLAT1 * DEGRAD, slat2 = SLAT2 * DEGRAD;
  let sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(Math.tan(Math.PI * .25 + slat2 * .5) / Math.tan(Math.PI * .25 + slat1 * .5));
  const sf = Math.pow(Math.tan(Math.PI * .25 + slat1 * .5), sn) * Math.cos(slat1) / sn;
  const ro = re * sf / Math.pow(Math.tan(Math.PI * .25 + OLAT * DEGRAD * .5), sn);
  const ra = re * sf / Math.pow(Math.tan(Math.PI * .25 + lat * DEGRAD * .5), sn);
  let theta = lon * DEGRAD - OLON * DEGRAD;
  if (theta > Math.PI) theta -= 2 * Math.PI;
  if (theta < -Math.PI) theta += 2 * Math.PI;
  theta *= sn;
  return { nx: Math.floor(ra * Math.sin(theta) + XO + .5), ny: Math.floor(ro - ra * Math.cos(theta) + YO + .5) };
}

function weatherBase() {
  const now = new Date(Date.now() + 9 * 60 * 60 * 1000);
  const bases = [2, 5, 8, 11, 14, 17, 20, 23];
  let baseHour = [...bases].reverse().find((hour) => hour <= now.getUTCHours());
  if (baseHour === undefined) { now.setUTCDate(now.getUTCDate() - 1); baseHour = 23; }
  return { baseDate: `${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, "0")}${String(now.getUTCDate()).padStart(2, "0")}`, baseTime: `${String(baseHour).padStart(2, "0")}00` };
}

function midForecastBase() {
  const now = new Date(Date.now() + 9 * 60 * 60 * 1000);
  let hour = now.getUTCHours() >= 18 ? 18 : now.getUTCHours() >= 6 ? 6 : -6;
  if (hour < 0) { now.setUTCDate(now.getUTCDate() - 1); hour = 18; }
  return `${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, "0")}${String(now.getUTCDate()).padStart(2, "0")}${String(hour).padStart(2, "0")}00`;
}

function midRegionCodes(place) {
  const region = `${place.region || ""} ${place.title || ""}`;
  if (/서울|경기|인천/.test(region)) return { landRegId: "11B00000", temperatureRegId: "11B10101" };
  if (/강원/.test(region)) return { landRegId: "11D10000", temperatureRegId: "11D10301" };
  if (/대전|세종|충남/.test(region)) return { landRegId: "11C20000", temperatureRegId: "11C20401" };
  if (/충북/.test(region)) return { landRegId: "11C10000", temperatureRegId: "11C10301" };
  if (/광주|전남/.test(region)) return { landRegId: "11F20000", temperatureRegId: "11F20501" };
  if (/전북/.test(region)) return { landRegId: "11F10000", temperatureRegId: "11F10201" };
  if (/대구|경북/.test(region)) return { landRegId: "11H10000", temperatureRegId: "11H10701" };
  if (/부산|울산|경남/.test(region)) return { landRegId: "11H20000", temperatureRegId: "11H20201" };
  return { landRegId: "11G00000", temperatureRegId: "11G00201" };
}

function dateDiffFromToday(date) {
  const today = new Date();
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((date - start) / 86400000);
}

function weatherIcon(label) {
  if (/비.*눈|눈.*비/.test(label)) return "🌨️";
  if (label.includes("눈")) return "❄️";
  if (label.includes("비")) return "🌧️";
  if (label.includes("흐림")) return "☁️";
  if (label.includes("구름")) return "🌤️";
  return "☀️";
}

async function renderWeather() {
  const place = routePlaces()[0] || places[0];
  const point = placePoint(place);
  const [items, mid] = await Promise.all([
    api.getWeather({ ...latLngToGrid(point.lat, point.lng), ...weatherBase() }).catch(() => null),
    api.getMidWeather({ ...midRegionCodes(place), tmFc: midForecastBase() }).catch(() => null),
  ]);
  const grouped = (items || []).reduce((result, item) => { (result[item.fcstDate] ||= []).push(item); return result; }, {});
  $("#planner-weather").innerHTML = days().map((day) => {
    const date = dayDate(day);
    const key = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
    const values = grouped[key] || [];
    const sky = Number(values.find((item) => item.category === "SKY" && item.fcstTime === "1200")?.fcstValue);
    const rain = values.some((item) => item.category === "PTY" && Number(item.fcstValue) > 0);
    const temps = values.filter((item) => item.category === "TMP").map((item) => Number(item.fcstValue)).filter(Number.isFinite);
    const diff = dateDiffFromToday(date);
    const suffix = diff >= 3 && diff <= 10 ? String(diff) : "";
    const midLabel = suffix ? (diff <= 7 ? mid?.land?.[`wf${suffix}Am`] || mid?.land?.[`wf${suffix}Pm`] : mid?.land?.[`wf${suffix}`]) : "";
    const midMin = suffix ? Number(mid?.temperature?.[`taMin${suffix}`]) : NaN;
    const midMax = suffix ? Number(mid?.temperature?.[`taMax${suffix}`]) : NaN;
    const label = values.length ? (rain ? "비/눈" : ({ 1: "맑음", 3: "구름 많음", 4: "흐림" }[sky] || "날씨")) : midLabel || "예보 제공 전";
    const icon = values.length ? (rain ? "🌧️" : ({ 1: "☀️", 3: "🌤️", 4: "☁️" }[sky] || "🌡️")) : midLabel ? weatherIcon(midLabel) : "🗓️";
    const temperature = temps.length ? `${Math.round(Math.min(...temps))}° / ${Math.round(Math.max(...temps))}°` : Number.isFinite(midMin) && Number.isFinite(midMax) ? `${Math.round(midMin)}° / ${Math.round(midMax)}°` : "예보 발표 전";
    const source = values.length ? "단기예보" : (midLabel || Number.isFinite(midMin) ? "중기예보" : "예보 대기");
    return `<article><strong>DAY ${day} · ${todayLabel(key.slice(0, 4) + "-" + key.slice(4, 6) + "-" + key.slice(6))}</strong><b>${icon}</b><span>${label}<br>${temperature}</span><small>${source}</small></article>`;
  }).join("");
}

async function initMap() {
  const loaded = await api.loadKakaoMap();
  if (!loaded) { $("#planner-map").textContent = "카카오맵을 불러오지 못했습니다."; return; }
  const first = placePoint(routePlaces()[0] || places[0]);
  map = new window.kakao.maps.Map($("#planner-map"), { center: new window.kakao.maps.LatLng(first.lat, first.lng), level: 8 });
  drawRoute();
}

function readTripForm() {
  const title = $('#plan-title').value.trim();
  const startDate = $('#plan-start').value;
  const endDate = $('#plan-end').value;
  const span = (new Date(endDate) - new Date(startDate)) / 86400000;
  if (!title || !startDate || !endDate || span < 0 || span > 13) { showToast('제목과 1~14일 이내의 올바른 여행 기간을 입력해주세요.'); return false; }
  if (state.tripSchedule.some(entry => Number(entry.day) > span + 1)) { showToast('기간을 줄이기 전에 이후 날짜의 일정을 이동하거나 삭제해주세요.'); return false; }
  const people = Number($('#plan-people').value);
  const budget = Number($('#plan-budget').value);
  if (!Number.isInteger(people) || people < 2 || people > 10 || !Number.isFinite(budget) || budget < 0) { showToast('정원은 2~10명, 예산은 0원 이상으로 입력해주세요.'); return false; }
  Object.assign(state.trip, {title,startDate,endDate,people,budget,region:$('#plan-region').value,theme:$('#plan-theme').value,description:$('#plan-description').value.trim()});
  return true;
}
function savePlan(notify = true) {
  if (!readTripForm()) return false;
  if (!ensureLoggedIn(state, '여행계획을 저장하려면 로그인해주세요.')) return false;
  const snapshot = structuredClone({id:state.trip.id,ownerId:state.user.id,trip:state.trip,tripSchedule:state.tripSchedule,itinerary:state.itinerary,itineraryPlaces:state.itineraryPlaces});
  const index = state.plans.findIndex(plan => plan.id === snapshot.id && plan.ownerId === state.user.id);
  if (index >= 0) state.plans[index] = snapshot; else state.plans.unshift(snapshot);
  try { saveState(state); } catch { showToast('저장 공간이 부족해 계획을 저장하지 못했어요.'); return false; }
  renderPlanHeader(); renderTabs(); renderSchedule(); renderWeather(); drawRoute();
  if (notify) showToast('여행계획을 저장했어요. 이제 동행을 모집할 수 있어요.');
  return true;
}
function publishCompanion() {
  if (!state.tripSchedule.length) return showToast('지도에서 여행지를 먼저 추가해주세요.');
  if (!savePlan(false)) return;
  const existing = state.companions.find(item => item.ownerId === state.user.id && item.tripId === state.trip.id);
  if (existing?.participants.length > state.trip.people) return showToast('현재 참가자 수보다 정원을 줄일 수 없어요.');
  const participant = {id:state.user.id,nickname:state.user.nickname,photo:state.user.photo,gender:state.user.gender};
  const allPlaces = Object.fromEntries(state.tripSchedule.map(entry => [entry.placeId,placeById(entry.placeId)]).filter(([,place]) => place));
  const region = $('#plan-region').value;
  const payload = {
    id:existing?.id || Date.now(), ownerId:state.user.id, tripId:state.trip.id, sourceTrip:state.trip.title, region, theme:$('#plan-theme').value,
    title:state.trip.title, description:$('#plan-description').value.trim() || '이 여행계획을 함께 즐길 동행을 모집해요. 아래 일정에서 우리 여행을 미리 확인하세요.',
    dates:`${state.trip.startDate.replaceAll('-', '.')} - ${state.trip.endDate.replaceAll('-', '.')}`,
    startDate:state.trip.startDate, endDate:state.trip.endDate, people:`${existing?.participants.length || 1}/${state.trip.people}명`,author:state.user.nickname,avatar:state.user.nickname[0],tags:[region,'여행계획','동행'],image:Object.values(allPlaces)[0]?.image || places[0].image,
    participants:existing?.participants || [participant], schedule:structuredClone(state.tripSchedule), places:structuredClone(allPlaces), closed:existing?.closed || false,
  };
  if (existing) Object.assign(existing,payload); else state.companions.unshift(payload);
  try { saveState(state); } catch { return showToast('모집글을 저장하지 못했어요. 저장 공간을 확인해주세요.'); }
  location.href = `./companion-detail.html?id=${payload.id}`;
}
function renderPlanHeader() {
  $('#trip-title').textContent = state.trip.title;
  $('#trip-dates').textContent = `${state.trip.startDate} — ${state.trip.endDate}`;
  $('#plan-duration').textContent = `${days().length}일 여행`;
  $('#plan-title').value = state.trip.title; $('#plan-start').value = state.trip.startDate; $('#plan-end').value = state.trip.endDate;
  $('#plan-people').value = state.trip.people; $('#plan-budget').value = state.trip.budget || 0;
  const existing = state.companions.find(item => item.tripId === state.trip.id && item.ownerId === state.user.id);
  $('#plan-region').value = state.trip.region || existing?.region || '서울'; if (!$('#plan-region').value) $('#plan-region').value='기타'; $('#plan-theme').value=state.trip.theme || existing?.theme || '자연'; $('#plan-description').value=state.trip.description || existing?.description || '';
  $('#publish-companion').textContent = existing ? '공유한 일정 업데이트' : '동행 구하기';
  $('#plan-library').innerHTML = state.plans.filter(plan => state.loggedIn && plan.ownerId === state.user.id).map(plan => `<button type="button" data-plan-id="${escapeHtml(plan.id)}" class="${plan.id === state.trip.id ? 'is-active' : ''}">${escapeHtml(plan.trip.title)}</button>`).join('');
}
$('#plan-library').addEventListener('click', event => {
  const plan = state.plans.find(plan => plan.id === event.target.closest('[data-plan-id]')?.dataset.planId && plan.ownerId === state.user.id);
  if (!plan) return;
  Object.assign(state,structuredClone({trip:plan.trip,tripSchedule:plan.tripSchedule,itinerary:plan.itinerary,itineraryPlaces:plan.itineraryPlaces})); activeDay = 1;
  saveState(state); renderPlanHeader(); renderTabs(); renderSchedule(); renderWeather(); drawRoute();
});
$('#new-plan').addEventListener('click', () => {
  if (!ensureLoggedIn(state)) return;
  if (state.tripSchedule.length && !savePlan(false)) return;
  const startDate = new Date().toLocaleDateString('en-CA');
  state.trip = {id:crypto.randomUUID(),title:'새로운 여행',startDate,endDate:startDate,people:4,budget:0};
  state.itinerary = []; state.tripSchedule = []; state.itineraryPlaces = {}; activeDay = 1;
  $('#plan-description').value = ''; saveState(state); renderPlanHeader(); renderTabs(); renderSchedule(); drawRoute(); renderWeather();
});

syncSchedule();
renderPlanHeader();
$("#trip-title").textContent = state.trip.title;
$("#trip-dates").textContent = `${state.trip.startDate.replaceAll("-", ".")} - ${state.trip.endDate.replaceAll("-", ".")}`;
renderTabs();
renderSchedule();
renderWeather();
document.querySelectorAll("[data-route-mode]").forEach((button) => button.addEventListener("click", () => {
  state.routeMode = button.dataset.routeMode;
  saveState(state);
  document.querySelectorAll("[data-route-mode]").forEach((item) => item.classList.toggle("is-active", item.dataset.routeMode === state.routeMode));
  drawRoute();
}));
document.querySelectorAll("[data-route-mode]").forEach((button) => button.classList.toggle("is-active", button.dataset.routeMode === state.routeMode));
$("#save-plan").addEventListener("click", () => savePlan());
$("#publish-companion").addEventListener("click", publishCompanion);
initMap();
