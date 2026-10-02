import { places } from "./data.js";
import { CURRENT_USER_ID, ensureLoggedIn, escapeHtml, initShell, loadState, showToast, userAvatar } from "./shared.js";
import { persist } from "./account.js";
import { STATUS_LABELS, companionCapacity, hasJoined, isCompanionOwner, syncCompanion } from "./companion-utils.js";

const state = initShell("companions", loadState());
const id = Number(new URLSearchParams(location.search).get("id"));
const item = state.companions.find((entry) => Number(entry.id) === id);
const $ = (selector) => document.querySelector(selector);
const MAX_CAPACITY = 10;

function scheduleMarkup(canSeeDetail) {
  if (!item.schedule?.length) return "";
  if (!canSeeDetail) return `<h2>여행 일정</h2><p>${item.schedule.length}개의 여행지가 계획되어 있어요. 참가 후 상세 일정을 함께 확인할 수 있습니다.</p>`;
  const rows = [...item.schedule]
    .sort((a, b) => Number(a.day) - Number(b.day) || String(a.time).localeCompare(String(b.time)))
    .map((entry) => {
      const place = places.find((candidate) => candidate.id === Number(entry.placeId)) || state.itineraryPlaces?.[Number(entry.placeId)];
      return `<li><span class="schedule-day">${Number(entry.day) || 1}일차 ${escapeHtml(entry.time || "")}</span><strong>${escapeHtml(place?.title || "여행지")}</strong>${entry.memo ? `<p>${escapeHtml(entry.memo)}</p>` : ""}</li>`;
    });
  return `<h2>여행 일정</h2><ol class="companion-schedule">${rows.join("")}</ol>`;
}

function participantMarkup(person) {
  const role = person.id === (item.ownerId || `seed-${item.id}`) ? "모집자" : person.id === CURRENT_USER_ID && state.loggedIn ? "나" : "";
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
  if (isOwner) renderOwnerForm();
}

function toggleJoin() {
  if (!ensureLoggedIn(state, "동행에 참가하려면 로그인이 필요해요.")) return;
  if (isCompanionOwner(state, item)) return;
  const index = item.participants.findIndex((person) => person.id === CURRENT_USER_ID);
  if (index >= 0) {
    if (!confirm("동행 참가를 취소할까요?")) return;
    item.participants.splice(index, 1);
    state.joinedCompanions = state.joinedCompanions.filter((value) => Number(value) !== Number(item.id));
  } else {
    syncCompanion(item);
    if (item.status === "closed") return showToast("모집이 마감된 동행이에요.");
    item.participants.push({ id: CURRENT_USER_ID, nickname: state.user.nickname, photo: state.user.photo, gender: state.user.gender });
    state.joinedCompanions = [...new Set([...state.joinedCompanions, item.id])];
  }
  syncCompanion(item);
  persist(state);
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
  persist(state);
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
  $("#join-companion").addEventListener("click", toggleJoin);
  $("#owner-edit-form").addEventListener("submit", saveOwnerEdit);
  $("#owner-delete").addEventListener("click", deleteCompanion);
  render();
}
