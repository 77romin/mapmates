import { initComments } from './comments.js';
import { TripRoute, tripDays, dayDate, renderDayWeather, applyType, bindRoadview } from './trip-map.js';
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
let routeMap = null, selectedDay=1, routeMode='straight', mapType='roadmap';
const route = new TripRoute(()=>routeMap,$('#companion-route-map'),$('#companion-route-status'));
function drawCompanionRoute() {
  const entries=[...(item.schedule || [])].filter(e=>Number(e.day || 1)===selectedDay).sort((a,b)=>String(a.time).localeCompare(String(b.time))).map(e=>({...e,place:placeForEntry(e)}));
  $('#companion-route-stops').innerHTML=entries.map((entry,index)=>`<li><b>${index+1}</b><span>${escapeHtml(entry.time || '')} · ${escapeHtml(entry.place?.title || '위치 미등록 장소')}</span></li>`).join('');
  route.draw(entries,routeMode);
  renderDayWeather($('#companion-day-weather'),entries[0]?.place || (item.schedule?.[0] && placeForEntry(item.schedule[0])),dayDate(item,selectedDay),item.region);
}
async function initCompanionRoute() {
  $('#companion-day-buttons').innerHTML=tripDays(item).map(day=>`<button type="button" data-trip-day="${day}" aria-pressed="${day===1}">DAY ${day}</button>`).join('');
  $('#companion-day-buttons').onclick=event=>{const button=event.target.closest('[data-trip-day]');if(!button)return;selectedDay=Number(button.dataset.tripDay);$('#companion-day-buttons').querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));drawCompanionRoute();};
  $('#companion-route-mode').onchange=event=>{routeMode=event.target.value;drawCompanionRoute();};
  $('#companion-map-type').onchange=event=>{mapType=event.target.value;applyType(routeMap,mapType);$('#companion-map-help').textContent=mapType==='roadview'?'파란색 도로를 더블클릭하면 로드뷰가 열립니다.':'';};
  const loaded=await api.loadKakaoMap();
  if(loaded){routeMap=new window.kakao.maps.Map($('#companion-route-map'),{center:new window.kakao.maps.LatLng(36,127.5),level:8,disableDoubleClickZoom:true});applyType(routeMap,mapType);bindRoadview(routeMap,()=>mapType);}
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
  delete state.companionComments?.[item.id];
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
  initComments({ state, collection: "companionComments", id: item.id, ownerId: item.ownerId, next: `companion-detail.html?id=${item.id}` });
  initCompanionRoute();
}
