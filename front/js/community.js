import {serverMode,request} from './server-api.js';
import { ensureLoggedIn, escapeHtml, initShell, loadState } from "./shared.js";
import { BOARDS, commentsOf, isBoard, isPostOwner, canWriteBoard } from "./board.js";

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

let serverPage=1,renderToken=0;
async function render(append=false) {
  const keyword = $("#post-keyword").value.trim().toLowerCase();
  let items = state.posts
    .filter((post) => post.board === board && (!keyword || `${post.title} ${post.content} ${post.author} ${post.category}`.toLowerCase().includes(keyword)))
    .sort(sorters[$("#post-sort").value] || sorters.latest);
  if(serverMode){if(!append)$('#post-list').textContent='게시글을 불러오는 중이에요.';const token=++renderToken;try{if(!append)serverPage=1;const data=await request(`posts?${new URLSearchParams({board,keyword,sort:$('#post-sort').value,page:serverPage,size:20})}`);if(token!==renderToken)return;items=append?[...state.posts,...data.items]:data.items;state.posts=items;state.postPage=data;}catch(e){$('#post-list').textContent=e.message;return;}}
  document.querySelectorAll("[data-board]").forEach((tab) => {
    const active = tab.dataset.board === board;
    tab.classList.toggle("is-active", active);
    tab.setAttribute("aria-selected", String(active));
  });
  $("#board-summary").textContent = `${BOARDS[board].label} ${serverMode?state.postPage.total:items.length}개${BOARDS[board].writable ? "" : " · 운영자만 작성할 수 있는 게시판이에요."}`;
  $("#post-list").innerHTML = items.map(postRow).join("") || `<div class="empty-state"><div><strong>${keyword ? "검색 결과가 없어요." : "게시글이 없어요."}</strong><span>${BOARDS[board].writable ? "첫 글을 작성해보세요." : "새 소식이 올라오면 알려드릴게요."}</span></div></div>`;
  $("#write-post").href = `./post-write.html?board=${board}`;
  $("#write-post").hidden = state.loggedIn && !canWriteBoard(state, board);
  if(serverMode){let more=$('#posts-more');if(!more){more=document.createElement('button');more.id='posts-more';more.className='button';more.textContent='더 보기';$('#post-list').after(more);more.onclick=()=>{serverPage++;render(true);};}more.hidden=items.length>=state.postPage.total;}
  syncUrl();
}

document.querySelectorAll("[data-board]").forEach((tab) => tab.addEventListener("click", () => { board = tab.dataset.board; render(); }));
$("#post-search").addEventListener("submit", (event) => { event.preventDefault(); render(); });
$("#post-sort").addEventListener("change", () => render());
$("#write-post").addEventListener("click", (event) => {
  if (!state.loggedIn) { event.preventDefault(); location.href = `./signup.html?next=${encodeURIComponent(`post-write.html?board=${board}`)}`; }
});
render();
