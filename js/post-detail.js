import { ensureLoggedIn, escapeHtml, initShell, loadState, showToast, userAvatar } from "./shared.js";
import { persist } from "./account.js";
import { BOARDS, formatDateTime, isPostOwner } from "./board.js";

const state = initShell("community", loadState());
const id = Number(new URLSearchParams(location.search).get("id"));
const post = state.posts.find((item) => Number(item.id) === id);
const $ = (selector) => document.querySelector(selector);
const MAX_COMMENT = 500;
let editingId = null;

const isMine = (comment) => state.loggedIn && comment.ownerId === state.user.id;
// 댓글 수정은 작성자만, 삭제는 작성자와 게시글 작성자가 할 수 있다.
const canDelete = (comment) => isMine(comment) || isPostOwner(state, post);
const comments = () => state.postComments[post.id];
const findComment = (commentId) => comments().find((comment) => String(comment.id) === String(commentId));

function countView() {
  const key = `neorang-viewed-post-${post.id}`;
  try {
    if (sessionStorage.getItem(key)) return;
    sessionStorage.setItem(key, "1");
  } catch {
    // 저장소를 쓸 수 없으면 새로고침마다 조회수가 오르는 것을 감수한다.
  }
  post.views = Number(post.views || 0) + 1;
  persist(state);
}

function renderArticle() {
  document.title = `${post.title} | 너랑 갈.지도`;
  $("#back-to-list").href = `./community.html?board=${encodeURIComponent(post.board)}`;
  const ownerActions = isPostOwner(state, post)
    ? `<div class="owner-actions post-owner-actions"><a class="text-button" href="./post-write.html?edit=${post.id}">수정</a><button class="text-button is-danger" id="delete-post" type="button">삭제</button></div>`
    : "";
  $("#post-article").innerHTML = `<header class="post-article-header">
      <span class="status-badge navy">${escapeHtml(post.category)}</span> <span class="helper-text">${escapeHtml(BOARDS[post.board]?.label || "")}</span>
      <h1>${escapeHtml(post.title)}</h1>
      <p>${escapeHtml(post.author)} · ${escapeHtml(post.date)}${post.updatedAt ? " (수정됨)" : ""} · 조회 ${Number(post.views || 0)}</p>
    </header>
    <div class="post-article-body">${escapeHtml(post.content)}</div>${ownerActions}`;
  $("#delete-post")?.addEventListener("click", deletePost);
}

function commentMarkup(comment) {
  const author = { nickname: comment.nickname, photo: comment.photo, gender: comment.gender };
  const writer = comment.ownerId === post.ownerId && post.ownerId ? `<small class="writer-badge">작성자</small>` : "";
  const actions = [
    isMine(comment) && editingId !== comment.id ? `<button type="button" data-action="edit" data-id="${comment.id}">수정</button>` : "",
    canDelete(comment) ? `<button type="button" data-action="delete" data-id="${comment.id}">삭제</button>` : "",
  ].join("");
  const body = editingId === comment.id
    ? `<form class="comment-edit-form" data-id="${comment.id}"><label class="sr-only" for="comment-edit-${comment.id}">댓글 수정</label><textarea id="comment-edit-${comment.id}" maxlength="${MAX_COMMENT}" required>${escapeHtml(comment.content)}</textarea><div class="owner-actions"><button type="submit">저장</button><button type="button" data-action="cancel">취소</button></div></form>`
    : `<p>${escapeHtml(comment.content)}</p>`;
  return `<article class="comment-item">
    <div class="comment-head">
      <div class="comment-author">${userAvatar(author, "is-small")}<strong>${escapeHtml(comment.nickname)}</strong>${writer}<time>${formatDateTime(comment.createdAt || comment.id)}${comment.updatedAt ? " · 수정됨" : ""}</time></div>
      ${actions ? `<div class="owner-actions">${actions}</div>` : ""}
    </div>
    ${body}
  </article>`;
}

function renderComments() {
  const list = comments();
  $("#comment-count").textContent = list.length;
  $("#comment-form").hidden = !state.loggedIn;
  $("#comment-login").hidden = state.loggedIn;
  $("#comment-login a").href = `./signup.html?next=${encodeURIComponent(`post-detail.html?id=${post.id}`)}`;
  $("#comment-list").innerHTML = list.map(commentMarkup).join("") || `<p class="helper-text">첫 댓글을 남겨보세요.</p>`;
  if (editingId) $(`#comment-edit-${editingId}`)?.focus();
}

function deletePost() {
  if (!isPostOwner(state, post)) return showToast("작성자만 게시글을 삭제할 수 있어요.");
  if (!confirm("게시글과 댓글이 모두 삭제돼요. 삭제할까요?")) return;
  state.posts = state.posts.filter((item) => item !== post);
  delete state.postComments[post.id];
  persist(state);
  location.href = `./community.html?board=${encodeURIComponent(post.board)}`;
}

$("#comment-content").addEventListener("input", () => {
  $("#comment-length").textContent = `${$("#comment-content").value.length} / ${MAX_COMMENT}`;
});

$("#comment-form").addEventListener("submit", (event) => {
  event.preventDefault();
  if (!ensureLoggedIn(state, "댓글을 쓰려면 로그인이 필요해요.")) return;
  const content = $("#comment-content").value.trim();
  if (!content) return showToast("댓글 내용을 입력해주세요.");
  const now = Date.now();
  comments().push({ id: now, ownerId: state.user.id, nickname: state.user.nickname, photo: state.user.photo, gender: state.user.gender, content: content.slice(0, MAX_COMMENT), createdAt: now });
  persist(state);
  event.target.reset();
  $("#comment-length").textContent = `0 / ${MAX_COMMENT}`;
  renderComments();
  showToast("댓글을 등록했어요.");
});

$("#comment-list").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button) return;
  const { action } = button.dataset;
  if (action === "cancel") { editingId = null; return renderComments(); }
  const comment = findComment(button.dataset.id);
  if (!comment) return;
  if (action === "edit" && isMine(comment)) { editingId = comment.id; renderComments(); }
  if (action === "delete" && canDelete(comment) && confirm(isMine(comment) ? "내 댓글을 삭제할까요?" : "이 댓글을 삭제할까요?")) {
    state.postComments[post.id] = comments().filter((item) => item !== comment);
    if (editingId === comment.id) editingId = null;
    persist(state);
    renderComments();
    showToast("댓글을 삭제했어요.");
  }
});

$("#comment-list").addEventListener("submit", (event) => {
  event.preventDefault();
  const comment = findComment(event.target.dataset.id);
  if (!comment || !isMine(comment)) return showToast("내 댓글만 수정할 수 있어요.");
  const content = event.target.querySelector("textarea").value.trim();
  if (!content) return showToast("댓글 내용을 입력해주세요.");
  Object.assign(comment, { content: content.slice(0, MAX_COMMENT), updatedAt: Date.now() });
  editingId = null;
  persist(state);
  renderComments();
  showToast("댓글을 수정했어요.");
});

if (!post) {
  $("#post-view").hidden = true;
  $("#post-missing").hidden = false;
} else {
  state.postComments[post.id] ||= [];
  countView();
  renderArticle();
  renderComments();
}
