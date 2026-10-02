import { escapeHtml, initShell, loadState } from "./shared.js";
const state = initShell("community", loadState());
let board = "travel";
const $ = (selector) => document.querySelector(selector);
function render() {
  const keyword = $("#post-keyword").value.trim().toLowerCase();
  const items = state.posts.filter((post) => post.board === board && (!keyword || `${post.title} ${post.author} ${post.category}`.toLowerCase().includes(keyword)));
  $("#post-list").innerHTML = items.map((post) => `<a class="post-link" href="./post-detail.html?id=${post.id}"><span class="status-badge navy">${escapeHtml(post.category)}</span><div><h2>${escapeHtml(post.title)}</h2><p>${escapeHtml(post.author)} · 조회 ${post.views || 0} · 댓글 ${(state.postComments[post.id] || []).length || post.comments || 0}</p></div><time>${escapeHtml(post.date)}</time></a>`).join("") || `<div class="empty-state"><div><strong>게시글이 없어요.</strong><span>첫 글을 작성해보세요.</span></div></div>`;
}
document.querySelectorAll("[data-board]").forEach((button) => button.addEventListener("click", () => { board = button.dataset.board; document.querySelectorAll("[data-board]").forEach((item) => item.classList.toggle("is-active", item === button)); render(); }));
$("#post-search").addEventListener("submit", (event) => { event.preventDefault(); render(); });
render();
