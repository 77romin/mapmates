import { initComments } from './comments.js';
import { escapeHtml, initShell, loadState, showToast } from "./shared.js";
import { persist } from "./account.js";
import { BOARDS, isPostOwner } from "./board.js";

const state = initShell("community", loadState());
const id = Number(new URLSearchParams(location.search).get("id"));
const post = state.posts.find((item) => Number(item.id) === id);
const $ = (selector) => document.querySelector(selector);


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

function deletePost() {
  if (!isPostOwner(state, post)) return showToast("작성자만 게시글을 삭제할 수 있어요.");
  if (!confirm("게시글과 댓글이 모두 삭제돼요. 삭제할까요?")) return;
  state.posts = state.posts.filter((item) => item !== post);
  delete state.postComments[post.id];
  persist(state);
  location.href = `./community.html?board=${encodeURIComponent(post.board)}`;
}

if (!post) {
  $("#post-view").hidden = true;
  $("#post-missing").hidden = false;
} else {
  state.postComments[post.id] ||= [];
  countView();
  renderArticle();
  initComments({ state, collection: "postComments", id: post.id, ownerId: post.ownerId, next: `post-detail.html?id=${post.id}` });
}
