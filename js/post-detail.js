import { CURRENT_USER_ID, escapeHtml, initShell, loadState, saveState, showToast } from "./shared.js";
const state = initShell("community", loadState());
const id = Number(new URLSearchParams(location.search).get("id"));
const post = state.posts.find((item) => Number(item.id) === id) || state.posts[0];
const $ = (selector) => document.querySelector(selector);
state.postComments[post.id] ||= [];
post.views = Number(post.views || 0) + 1;
saveState(state);
function isMine(item) { return item.ownerId === CURRENT_USER_ID; }
function render() {
  $("#post-article").innerHTML = `<header class="post-article-header"><span class="status-badge navy">${escapeHtml(post.category)}</span><h1>${escapeHtml(post.title)}</h1><p>${escapeHtml(post.author)} · ${escapeHtml(post.date)} · 조회 ${post.views}</p></header><div class="post-article-body">${escapeHtml(post.content)}</div>${isMine(post) ? `<div class="owner-actions"><a class="text-button" href="./post-write.html?edit=${post.id}">수정</a></div>` : ""}`;
  const comments = state.postComments[post.id];
  $("#comment-count").textContent = comments.length;
  $("#comment-list").innerHTML = comments.map((comment) => `<article class="comment-item"><div class="comment-head"><strong>${escapeHtml(comment.nickname)}</strong>${isMine(comment) ? `<div class="owner-actions"><button data-edit-comment="${comment.id}">수정</button><button data-delete-comment="${comment.id}">삭제</button></div>` : ""}</div><p>${escapeHtml(comment.content)}</p></article>`).join("") || `<p class="helper-text">첫 댓글을 남겨보세요.</p>`;
  document.querySelectorAll("[data-edit-comment]").forEach((button) => button.addEventListener("click", () => {
    const comment = comments.find((item) => Number(item.id) === Number(button.dataset.editComment));
    const value = prompt("댓글 수정", comment.content);
    if (value?.trim()) { comment.content = value.trim(); saveState(state); render(); }
  }));
  document.querySelectorAll("[data-delete-comment]").forEach((button) => button.addEventListener("click", () => {
    const index = comments.findIndex((item) => Number(item.id) === Number(button.dataset.deleteComment) && isMine(item));
    if (index >= 0 && confirm("내 댓글을 삭제할까요?")) { comments.splice(index, 1); saveState(state); render(); }
  }));
}
$("#comment-form").addEventListener("submit", (event) => { event.preventDefault(); const content = $("#comment-content").value.trim(); if (!content) return; state.postComments[post.id].push({ id: Date.now(), ownerId: CURRENT_USER_ID, nickname: state.user.nickname, content }); $("#comment-content").value = ""; saveState(state); render(); showToast("댓글을 등록했어요."); });
render();
