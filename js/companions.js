import { escapeHtml, initShell, loadState } from "./shared.js";

const state = initShell("companions", loadState());
const $ = (selector) => document.querySelector(selector);

function render() {
  const region = $("#companion-region").value;
  const theme = $("#companion-theme").value;
  const status = $("#companion-status").value;
  const items = state.companions.filter((item) => (region === "all" || item.region === region) && (theme === "all" || item.theme === theme) && (status === "all" || item.status === status));
  $("#companion-count").textContent = items.length;
  $("#companion-list").innerHTML = items.map((item) => `<a class="subpage-card companion-link-card" href="./companion-detail.html?id=${item.id}"><img src="${item.image}" alt=""><div class="companion-link-body"><span class="status-badge ${item.status}">${item.status === "soon" ? "마감 임박" : "모집 중"}</span><h2>${escapeHtml(item.title)}</h2><p>${escapeHtml(item.description)}</p><div class="companion-meta"><span>${escapeHtml(item.author)}</span><span>${escapeHtml(item.dates)} · ${escapeHtml(item.people)}</span></div></div></a>`).join("") || `<div class="empty-state"><div><strong>조건에 맞는 동행이 없어요.</strong><span>필터를 바꾸거나 내 여행계획에서 모집해보세요.</span></div></div>`;
}

$("#companion-search").addEventListener("click", render);
render();
