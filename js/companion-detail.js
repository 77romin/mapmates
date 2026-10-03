import { api } from "./api.js";
import { places } from "./data.js";
import { ensureLoggedIn, escapeHtml, initShell, loadState, showToast, userAvatar } from "./shared.js";
import { persist } from "./account.js";
import { STATUS_LABELS, companionCapacity, hasJoined, isCompanionOwner, syncCompanion } from "./companion-utils.js";

const state = initShell("companions", loadState());
const id = Number(new URLSearchParams(location.search).get("id"));
const item = state.companions.find((entry) => Number(entry.id) === id);
const $ = (selector) => document.querySelector(selector);
const MAX_CAPACITY = 10;
const placeForEntry = entry => item.places?.[Number(entry.placeId)] || places.find(place => place.id === Number(entry.placeId)) || state.itineraryPlaces?.[Number(entry.placeId)];
let routeMap = null;
let routeOverlays = [];
let routeLine = null;

function routeEntries() {
  const selectedDay = $('#companion-route-day').value;
  return [...(item.schedule || [])].filter(entry => selectedDay === 'all' || Number(entry.day || 1) === Number(selectedDay))
    .sort((a,b) => Number(a.day || 1) - Number(b.day || 1) || String(a.time).localeCompare(String(b.time)))
    .map(entry => ({...entry,place:placeForEntry(entry)}));
}
function drawCompanionRoute() {
  const entries = routeEntries();
  const points = entries.filter(entry => entry.place && Number.isFinite(Number(entry.place.lat)) && Number.isFinite(Number(entry.place.lng)) && Math.abs(Number(entry.place.lat)) <= 90 && Math.abs(Number(entry.place.lng)) <= 180);
  $('#companion-route-stops').innerHTML = entries.map((entry,index) => `<li><b>${index + 1}</b><span>${Number(entry.day || 1)}일차 ${escapeHtml(entry.time || '')} · ${escapeHtml(entry.place?.title || '위치 미등록 장소')}</span></li>`).join('');
  routeOverlays.forEach(overlay => overlay.setMap(null)); routeOverlays = [];
  routeLine?.setMap(null);
  if (!points.length) {
    $('#companion-route-status').textContent = '표시할 장소 좌표가 없어요. 모집자가 일정을 등록하면 동선을 볼 수 있습니다.';
    if (!routeMap) $('#companion-route-map').textContent = '아직 등록된 동선이 없어요.';
    return;
  }
  if (!routeMap) {
    const minLat = Math.min(...points.map(entry => Number(entry.place.lat))), maxLat = Math.max(...points.map(entry => Number(entry.place.lat)));
    const minLng = Math.min(...points.map(entry => Number(entry.place.lng))), maxLng = Math.max(...points.map(entry => Number(entry.place.lng)));
    const locations = points.map(entry => ({x:60+(Number(entry.place.lng)-minLng)/(maxLng-minLng || 1)*680,y:235-(Number(entry.place.lat)-minLat)/(maxLat-minLat || 1)*180,index:entries.indexOf(entry)+1}));
    $('#companion-route-map').innerHTML = `<svg viewBox="0 0 800 300" role="img" aria-label="직선 동선 미리보기"><polyline points="${locations.map(point=>`${point.x},${point.y}`).join(' ')}" fill="none" stroke="#dc3545" stroke-width="4"/>${locations.map(point=>`<circle cx="${point.x}" cy="${point.y}" r="18" fill="#dc3545"/><text x="${point.x}" y="${point.y+5}" text-anchor="middle" fill="white" font-size="14">${point.index}</text>`).join('')}</svg>`;
    $('#companion-route-status').textContent = '지도를 연결하지 못해 좌표 기반 동선 미리보기를 표시합니다. 아래 장소 목록에서 방문 순서를 확인하세요.';
    return;
  }
  const bounds = new window.kakao.maps.LatLngBounds();
  const path = points.map(entry => {
    const position = new window.kakao.maps.LatLng(Number(entry.place.lat),Number(entry.place.lng)); bounds.extend(position);
    const marker = document.createElement('button'); marker.type = 'button'; marker.className = 'companion-route-marker'; marker.textContent = entries.indexOf(entry)+1;
    marker.setAttribute('aria-label',`${marker.textContent}번 ${entry.place.title}`);
    marker.addEventListener('click',()=>showToast(`${entry.day || 1}일차 ${entry.time || ''} · ${entry.place.title}`));
    routeOverlays.push(new window.kakao.maps.CustomOverlay({map:routeMap,position,content:marker,yAnchor:1,zIndex:5}));
    return position;
  });
  routeLine = new window.kakao.maps.Polyline({map:routeMap,path,strokeColor:'#dc3545',strokeWeight:5,strokeOpacity:.9,strokeStyle:'solid'});
  if (path.length === 1) { routeMap.setCenter(path[0]); routeMap.setLevel(5); } else routeMap.setBounds(bounds,45,45,45,45);
  $('#companion-route-status').textContent = `${points.length}곳의 방문 동선${entries.length !== points.length ? ' · 좌표가 없는 장소는 지도에서 제외했어요.' : ''}`;
}
async function initCompanionRoute() {
  const days = [...new Set((item.schedule || []).map(entry=>Number(entry.day || 1)))].sort((a,b)=>a-b);
  $('#companion-route-day').innerHTML = '<option value="all">전체 일정</option>' + days.map(day=>`<option value="${day}">${day}일차</option>`).join('');
  $('#companion-route-day').addEventListener('change',drawCompanionRoute);
  const loaded = await api.loadKakaoMap();
  if (loaded) routeMap = new window.kakao.maps.Map($('#companion-route-map'),{center:new window.kakao.maps.LatLng(36,127.5),level:8});
  drawCompanionRoute();
}


function scheduleMarkup(canSeeDetail) {
  if (!item.schedule?.length) return "";

  const rows = [...item.schedule]
    .sort((a, b) => Number(a.day) - Number(b.day) || String(a.time).localeCompare(String(b.time)))
    .map((entry) => {
      const place = placeForEntry(entry);
      return `<li><span class="schedule-day">${Number(entry.day) || 1}일차 ${escapeHtml(entry.time || "")}</span><strong>${escapeHtml(place?.title || "여행지")}</strong>${entry.memo ? `<p>${escapeHtml(entry.memo)}</p>` : ""}</li>`;
    });
  return `<h2>여행 일정</h2><ol class="companion-schedule">${rows.join("")}</ol>`;
}

function participantMarkup(person) {
  const role = person.id === (item.ownerId || `seed-${item.id}`) ? "모집자" : person.id === state.user.id && state.loggedIn ? "나" : "";
  return `<li>${userAvatar(person)}<span>${escapeHtml(person.nickname || "여행자")}</span>${role ? `<small>${role}</small>` : ""}</li>`;
}

function renderJoinButton(isOwner, joined) {
  const button = $("#join-companion");
  const closed = item.status === "closed";
  button.hidden = isOwner;
  button.disabled = closed && !joined;
  button.classList.toggle("button-secondary", joined);
  button.textContent = joined ? "참가 취소" : closed ? "모집 마감" : state.loggedIn ? "참가 신청" : "로그인하고 참가하기";
  $("#join-help").textContent = isOwner ? "내가 만든 모집글이에요. 아래에서 내용을 수정할 수 있어요." : joined ? "참가 중인 동행이에요. 상세 일정을 확인해보세요." : "";
}

function renderOwnerForm() {
  $("#owner-title").value = item.title;
  $("#owner-description").value = item.description;
  $("#owner-dates").value = item.dates || "";
  $("#owner-tags").value = (item.tags || []).join(", ");
  $("#owner-closed").checked = Boolean(item.closed);
  const minimum = Math.max(2, item.participants.length);
  const capacity = companionCapacity(item);
  $("#owner-capacity").innerHTML = Array.from({ length: MAX_CAPACITY - minimum + 1 }, (_, index) => minimum + index)
    .map((value) => `<option value="${value}"${value === capacity ? " selected" : ""}>${value}명</option>`).join("");
}

function render() {
  syncCompanion(item);
  const isOwner = isCompanionOwner(state, item);
  const joined = hasJoined(state, item);
  document.title = `${item.title} | 너랑 갈.지도`;
  $("#companion-hero").style.backgroundImage = `url(${JSON.stringify(item.image || "")})`;
  $("#companion-hero").innerHTML = `<div><span class="status-badge ${item.status}">${STATUS_LABELS[item.status]}</span><h1>${escapeHtml(item.title)}</h1><p>${escapeHtml(item.author)} · ${escapeHtml(item.dates)} · ${escapeHtml(item.people)}</p></div>`;
  $("#companion-content").innerHTML = `<h2>여행 소개</h2><p class="detail-description">${escapeHtml(item.description)}</p><div class="tag-row">${(item.tags || []).map((tag) => `<span class="tag"># ${escapeHtml(tag)}</span>`).join("")}</div>${scheduleMarkup(isOwner || joined)}`;
  $("#participant-count").textContent = item.people;
  $("#participant-list").innerHTML = item.participants.map(participantMarkup).join("");
  renderJoinButton(isOwner, joined);
  $("#owner-edit").hidden = !isOwner;
  $('#close-recruitment').hidden = !isOwner;
  $('#close-recruitment').textContent = item.closed ? '참여모집 다시 열기' : '참여마감';
  if (isOwner) renderOwnerForm();
}

function toggleJoin() {
  if (!ensureLoggedIn(state, "동행에 참가하려면 로그인이 필요해요.")) return;
  if (isCompanionOwner(state, item)) return;
  const index = item.participants.findIndex((person) => person.id === state.user.id);
  if (index >= 0) {
    if (!confirm("동행 참가를 취소할까요?")) return;
    item.participants.splice(index, 1);
    state.joinedCompanions = state.joinedCompanions.filter((value) => Number(value) !== Number(item.id));
  } else {
    syncCompanion(item);
    if (item.status === "closed") return showToast("모집이 마감된 동행이에요.");
    item.participants.push({ id: state.user.id, nickname: state.user.nickname, photo: state.user.photo, gender: state.user.gender });
    state.joinedCompanions = [...new Set([...state.joinedCompanions, item.id])];
  }
  syncCompanion(item);
  if (!persist(state)) return;
  render();
  showToast(index >= 0 ? "동행 참가를 취소했어요." : "동행 참가를 완료했어요.");
}

function saveOwnerEdit(event) {
  event.preventDefault();
  if (!isCompanionOwner(state, item)) return showToast("모집글 작성자만 수정할 수 있어요.");
  const title = $("#owner-title").value.trim();
  const description = $("#owner-description").value.trim();
  const capacity = Number($("#owner-capacity").value);
  let message = "";
  if (!title) message = "제목을 입력해주세요.";
  else if (!description) message = "소개를 입력해주세요.";
  else if (capacity < item.participants.length) message = "현재 참가자 수보다 적게 설정할 수 없어요.";
  $("#owner-error").textContent = message;
  if (message) return;
  Object.assign(item, {
    title,
    description,
    dates: $("#owner-dates").value.trim() || item.dates,
    people: `${item.participants.length}/${capacity}명`,
    tags: $("#owner-tags").value.split(",").map((tag) => tag.trim().replace(/^#/, "")).filter(Boolean).slice(0, 6),
    closed: $("#owner-closed").checked,
  });
  syncCompanion(item);
  if (!persist(state)) return;
  render();
  showToast("모집글을 수정했어요.");
}

function deleteCompanion() {
  if (!isCompanionOwner(state, item)) return showToast("모집글 작성자만 삭제할 수 있어요.");
  if (!confirm("모집글을 삭제하면 참가자 목록도 함께 사라져요. 삭제할까요?")) return;
  state.companions = state.companions.filter((entry) => entry !== item);
  state.joinedCompanions = state.joinedCompanions.filter((value) => Number(value) !== Number(item.id));
  persist(state);
  location.href = "./companions.html";
}

if (!item) {
  $("#companion-view").hidden = true;
  $("#companion-missing").hidden = false;
} else {
  $('#close-recruitment').addEventListener('click', () => {
    if (!isCompanionOwner(state,item)) return;
    if (item.closed && item.participants.length >= companionCapacity(item)) return showToast('정원을 늘린 다음 모집을 다시 열 수 있어요.');
    item.closed = !item.closed; syncCompanion(item);
    if (!persist(state)) return; render(); showToast(item.closed ? '참여를 마감했어요. 기존 참가자는 일정을 확인할 수 있어요.' : '동행 모집을 다시 열었어요.');
  });
  $("#join-companion").addEventListener("click", toggleJoin);
  $("#owner-edit-form").addEventListener("submit", saveOwnerEdit);
  $("#owner-delete").addEventListener("click", deleteCompanion);
  render();
  initCompanionRoute();
}
