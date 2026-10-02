import { CURRENT_USER_ID, escapeHtml, initShell, loadState, saveState, showToast, userAvatar } from "./shared.js";

const state = initShell("companions", loadState());
const id = Number(new URLSearchParams(location.search).get("id"));
const item = state.companions.find((entry) => Number(entry.id) === id) || state.companions[0];
const $ = (selector) => document.querySelector(selector);
const me = { id: CURRENT_USER_ID, nickname: state.user.nickname, photo: state.user.photo, gender: state.user.gender };
item.participants ||= [{ id: item.ownerId || `seed-${item.id}`, nickname: item.author, photo: "", gender: item.id % 2 ? "female" : "male" }];

function render() {
  const isOwner = item.ownerId === CURRENT_USER_ID;
  const joined = item.participants.some((person) => person.id === CURRENT_USER_ID);
  $("#companion-hero").style.backgroundImage = `url('${item.image}')`;
  $("#companion-hero").innerHTML = `<div><span class="status-badge ${item.status}">${item.status === "soon" ? "마감 임박" : "모집 중"}</span><h1>${escapeHtml(item.title)}</h1><p>${escapeHtml(item.author)} · ${escapeHtml(item.dates)} · ${escapeHtml(item.people)}</p></div>`;
  $("#companion-content").innerHTML = `<h2>여행 소개</h2><p>${escapeHtml(item.description)}</p><div class="tag-row">${(item.tags || []).map((tag) => `<span class="tag"># ${escapeHtml(tag)}</span>`).join("")}</div>${item.schedule ? `<h2>여행 일정</h2><p>${item.schedule.length}개의 여행지가 계획되어 있어요. 참가 후 상세 일정을 함께 조율할 수 있습니다.</p>` : ""}`;
  $("#participant-list").innerHTML = item.participants.map((person) => userAvatar(person)).join("");
  $("#join-companion").hidden = isOwner;
  $("#join-companion").textContent = joined ? "참가 취소" : "동행 참가";
  $("#join-companion").classList.toggle("button-secondary", joined);
  $("#owner-edit").hidden = !isOwner;
  if (isOwner) { $("#owner-title").value = item.title; $("#owner-description").value = item.description; }
}

$("#join-companion").addEventListener("click", () => {
  const index = item.participants.findIndex((person) => person.id === CURRENT_USER_ID);
  if (index >= 0) item.participants.splice(index, 1); else item.participants.push(me);
  item.people = `${item.participants.length}/${String(item.people).split("/")[1] || "4명"}`;
  state.joinedCompanions = index >= 0 ? state.joinedCompanions.filter((value) => Number(value) !== item.id) : [...new Set([...state.joinedCompanions, item.id])];
  saveState(state); render(); showToast(index >= 0 ? "동행 참가를 취소했어요." : "동행 참가가 완료됐어요.");
});
$("#owner-edit-form").addEventListener("submit", (event) => { event.preventDefault(); item.title = $("#owner-title").value.trim(); item.description = $("#owner-description").value.trim(); saveState(state); render(); showToast("모집글을 수정했어요."); });
render();
