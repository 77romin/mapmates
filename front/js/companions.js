import { places } from "./data.js";
import { escapeHtml, initShell, loadState, userAvatar } from "./shared.js";
import { STATUS_LABELS, hasJoined, isCompanionOwner, syncCompanion } from "./companion-utils.js";

const state = initShell("companions", loadState());
const $ = (selector) => document.querySelector(selector);
const MAX_AVATARS = 4;

state.companions.forEach(syncCompanion);
$("#companion-mine").closest("label").hidden = !state.loggedIn;

function matches(item) {
  const region = $("#companion-region").value;
  const theme = $("#companion-theme").value;
  const status = $("#companion-status").value;
  const keyword = $("#companion-keyword").value.trim().toLowerCase();
  const text = `${item.title} ${item.description} ${(item.tags || []).join(" ")} ${item.author}`.toLowerCase();
  return (region === "all" || item.region === region)
    && (theme === "all" || item.theme === theme)
    && (status === "all" || item.status === status)
    && (!keyword || text.includes(keyword))
    && (!$("#companion-mine").checked || hasJoined(state, item));
}

function thumbnail(item) {
  const first = [...(item.schedule || [])].sort((a,b) => Number(a.day || 1) - Number(b.day || 1) || String(a.time || '').localeCompare(String(b.time || '')))[0];
  const plan = state.plans.find(plan => plan.id === item.tripId);
  const place = first && (item.places?.[first.placeId] || plan?.itineraryPlaces?.[first.placeId] || state.itineraryPlaces?.[first.placeId] || places.find(place => Number(place.id) === Number(first.placeId)));
  return place?.image || './assets/sunset-clouds.png';
}

function card(item) {
  const extra = item.participants.length - MAX_AVATARS;
  const badge = isCompanionOwner(state, item) ? `<span class="status-badge navy">내 모집글</span>` : hasJoined(state, item) ? `<span class="status-badge navy">참가 중</span>` : "";
  return `<a class="subpage-card companion-link-card${item.status === "closed" ? " is-closed" : ""}" href="./companion-detail.html?id=${item.id}">
    <img src="${escapeHtml(thumbnail(item))}" alt="" loading="lazy">
    <div class="companion-link-body">
      <div class="badge-row"><span class="status-badge ${item.status}">${STATUS_LABELS[item.status]}</span>${badge}</div>
      <h2>${escapeHtml(item.title)}</h2>
      <p>${escapeHtml(item.description)}</p>
      <div class="participant-stack is-compact" aria-label="참가자 ${item.participants.length}명">${item.participants.slice(0, MAX_AVATARS).map((person) => userAvatar(person)).join("")}${extra > 0 ? `<span class="avatar-more">+${extra}</span>` : ""}</div>
      <div class="companion-meta"><span>${escapeHtml(item.author)}</span><span>${escapeHtml(item.dates)} · ${escapeHtml(item.people)}</span></div>
    </div>
  </a>`;
}

function render() {
  const items = state.companions.filter(matches);
  $("#companion-count").textContent = items.length;
  $("#companion-list").innerHTML = items.map(card).join("") || `<div class="empty-state"><div><strong>조건에 맞는 동행이 없어요.</strong><span>필터를 바꾸거나 내 여행계획에서 모집해보세요.</span></div></div>`;
  $('#companion-list').querySelectorAll('.companion-link-card > img').forEach(image => image.addEventListener('error', () => {
    if (!image.dataset.fallback) { image.dataset.fallback = '1'; image.src = './assets/sunset-clouds.png'; }
  }));
}

$("#companion-filter-form").addEventListener("submit", (event) => { event.preventDefault(); render(); });
["#companion-region", "#companion-theme", "#companion-status", "#companion-mine"].forEach((selector) => $(selector).addEventListener("change", render));
render();
