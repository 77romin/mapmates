import { serverMode, request, refresh, serverSnapshot } from "./server-api.js";
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
let requestStatus="none";
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
  if(serverMode && item.joinPolicy==='approval' && !joined && !isOwner){button.hidden=false;button.disabled=closed;button.textContent=requestStatus==='pending'?'신청 취소':closed?'모집 마감':'참가 신청 (승인 대기)';$('#join-help').textContent=requestStatus==='pending'?'모집자의 승인을 기다리고 있어요.':'간단한 자기소개와 함께 신청해주세요.';return;}
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
  if(serverMode&&item.lifecycle){let badge=$('#lifecycle-label');if(!badge){badge=document.createElement('p');badge.id='lifecycle-label';$('#companion-hero').before(badge);}badge.textContent='여행 상태: '+({scheduled:'출발 예정',ongoing:'여행 중',completed:'여행 종료',cancelled:'여행 취소'})[item.lifecycle];}
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

async function toggleJoin() {
  if (!ensureLoggedIn(state, "동행에 참가하려면 로그인이 필요해요.")) return;
  if (isCompanionOwner(state, item)) return;
  if(serverMode){try{const joined=hasJoined(state,item)||requestStatus==='pending';const introduction=!joined&&item.joinPolicy==='approval'?prompt('함께 여행하고 싶은 이유를 소개해주세요.'):'';if(introduction===null)return;Object.assign(item,await request(`companions/${item.id}/participants${joined?'/me':''}`,joined?'DELETE':'POST',joined?undefined:{introduction}));await loadRequests();render();}catch(e){showToast(e.message);}return;}
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

async function saveOwnerEdit(event) {
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
  if(serverMode){try{Object.assign(item,await request(`companions/${item.id}`,'PUT',{title,description,capacity,closed:$('#owner-closed').checked,joinPolicy:$('#owner-join-policy').value}));render();}catch(e){$('#owner-error').textContent=e.message;}return;}
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

async function deleteCompanion() {
  if (!isCompanionOwner(state, item)) return showToast("모집글 작성자만 삭제할 수 있어요.");
  if (!confirm("모집글을 삭제하면 참가자 목록도 함께 사라져요. 삭제할까요?")) return;
  if(serverMode){try{await request(`companions/${item.id}`,'DELETE');location.href='./companions.html';}catch(e){showToast(e.message);}return;}
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
  $('#close-recruitment').addEventListener('click', async () => {
    if (!isCompanionOwner(state,item)) return;
    if (item.closed && item.participants.length >= companionCapacity(item)) return showToast('정원을 늘린 다음 모집을 다시 열 수 있어요.');
    if(serverMode){try{Object.assign(item,await request(`companions/${item.id}`,'PUT',{closed:!item.closed}));render();}catch(e){showToast(e.message);}return;}
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

if(serverMode){$('#owner-dates').readOnly=true;$('#owner-dates').title='여행 기간은 내 여행에서 변경해주세요.';$('#owner-tags').readOnly=true;$('#owner-tags').title='태그는 여행 지역과 테마를 사용해요.';}

async function loadRequests(){
 if(!serverMode||!item||!state.loggedIn)return;
 if(isCompanionOwner(state,item)){
  let section=$('#request-management');if(!section){section=document.createElement('section');section.id='request-management';section.className='subpage-card';$('#owner-edit').after(section);}
  const requests=await request(`companions/${item.id}/requests`);
  section.innerHTML='<h2>참가 신청 관리</h2>'+ (requests.map(r=>`<article><strong>${escapeHtml(r.nickname)}</strong> · ${escapeHtml(({pending:'대기',approved:'승인',rejected:'거절',cancelled:'취소'})[r.status])}<p>${escapeHtml(r.introduction)}</p>${r.status==='pending'?`<button class="button" data-decision="approved" data-member="${r.memberId}">승인</button> <button class="button" data-decision="rejected" data-member="${r.memberId}">거절</button>`:''}</article>`).join('')||'<p>신청이 없어요.</p>');
  section.querySelectorAll('[data-decision]').forEach(b=>b.onclick=async()=>{try{await request(`companions/${item.id}/requests/${b.dataset.member}`,'PUT',{status:b.dataset.decision});Object.assign(item,await request(`companions/${item.id}`));await loadRequests();render();}catch(e){showToast(e.message);}});
 }else{requestStatus=(await request(`companions/${item.id}/requests/me`)).status;}
}
if(serverMode&&item){const label=document.createElement('label');label.className='field';label.innerHTML='<span>참가 방식</span><select id="owner-join-policy"><option value="instant">즉시 참가</option><option value="approval">모집자 승인 후 참가</option></select>';$('#owner-closed').closest('label').before(label);$('#owner-join-policy').value=item.joinPolicy||'instant';await loadRequests();render();}

if(serverMode&&item){
 const history=document.createElement('section');history.className='subpage-card';history.id='trip-history';$('#owner-edit').after(history);
 if(isCompanionOwner(state,item)||hasJoined(state,item)){const revisions=await request(`plans/${item.tripId}/revisions`);history.innerHTML='<h2>공유 일정 변경 이력</h2>'+ (revisions.map(r=>`<p>${escapeHtml(r.summary)}<br><small>${escapeHtml(r.createdAt)}</small></p>`).join('')||'<p>변경 이력이 없어요.</p>');}else history.hidden=true;
 if(isCompanionOwner(state,item)){
  const actions=document.createElement('div');actions.className='subpage-actions';actions.innerHTML='<button type="button" class="button" data-trip-state="completed">여행 종료</button><button type="button" class="button" data-trip-state="cancelled">여행 취소</button>';$('#owner-edit').append(actions);
  actions.querySelectorAll('button').forEach(b=>{b.disabled=['completed','cancelled'].includes(item.lifecycle);b.onclick=async()=>{if(!confirm(b.textContent+' 처리할까요? 참가자에게 알려드려요.'))return;try{await request(`companions/${item.id}/lifecycle`,'PUT',{lifecycle:b.dataset.tripState});location.reload();}catch(e){showToast(e.message);}};});
 }
}
