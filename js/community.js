import { ensureLoggedIn, escapeHtml, initShell, loadState } from "./shared.js";
import { BOARDS, commentsOf, isBoard, isPostOwner } from "./board.js";

const state = initShell("community", loadState());
const $ = (selector) => document.querySelector(selector);
const params = new URLSearchParams(location.search);
let board = isBoard(params.get("board")) ? params.get("board") : "travel";
$("#post-keyword").value = params.get("q") || "";

const sorters = {
  latest: (a, b) => String(b.date).localeCompare(String(a.date)) || Number(b.id) - Number(a.id),
  views: (a, b) => Number(b.views || 0) - Number(a.views || 0),
  comments: (a, b) => commentsOf(state, b).length - commentsOf(state, a).length,
};

function syncUrl() {
  const next = new URLSearchParams({ board });
  const keyword = $("#post-keyword").value.trim();
  if (keyword) next.set("q", keyword);
  history.replaceState(null, "", `?${next}`);
}

function postRow(post) {
  const mine = isPostOwner(state, post) ? `<span class="status-badge open">내 글</span>` : "";
  return `<a class="post-link" href="./post-detail.html?id=${post.id}">
    <span class="status-badge navy">${escapeHtml(post.category)}</span>
    <div><h2>${escapeHtml(post.title)} ${mine}</h2><p>${escapeHtml(post.author)} · 조회 ${Number(post.views || 0)} · 댓글 ${commentsOf(state, post).length}</p></div>
    <time>${escapeHtml(post.date)}</time>
  </a>`;
}

function render() {
  const keyword = $("#post-keyword").value.trim().toLowerCase();
  const items = state.posts
    .filter((post) => post.board === board && (!keyword || `${post.title} ${post.content} ${post.author} ${post.category}`.toLowerCase().includes(keyword)))
    .sort(sorters[$("#post-sort").value] || sorters.latest);
  document.querySelectorAll("[data-board]").forEach((tab) => {
    const active = tab.dataset.board === board;
    tab.classList.toggle("is-active", active);
    tab.setAttribute("aria-selected", String(active));
  });
  $("#board-summary").textContent = `${BOARDS[board].label} ${items.length}개${BOARDS[board].writable ? "" : " · 운영자만 작성할 수 있는 게시판이에요."}`;
  $("#post-list").innerHTML = items.map(postRow).join("") || `<div class="empty-state"><div><strong>${keyword ? "검색 결과가 없어요." : "게시글이 없어요."}</strong><span>${BOARDS[board].writable ? "첫 글을 작성해보세요." : "새 소식이 올라오면 알려드릴게요."}</span></div></div>`;
  syncUrl();
}

document.querySelectorAll("[data-board]").forEach((tab) => tab.addEventListener("click", () => { board = tab.dataset.board; render(); }));
$("#post-search").addEventListener("submit", (event) => { event.preventDefault(); render(); });
$("#post-sort").addEventListener("change", render);
$("#write-post").addEventListener("click", (event) => {
  if (!ensureLoggedIn(state, "글을 쓰려면 로그인이 필요해요.")) event.preventDefault();
});
render();
