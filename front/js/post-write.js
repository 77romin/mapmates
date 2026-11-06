import { ensureLoggedIn, initShell, loadState, showToast } from "./shared.js";
import { persist } from "./account.js";
import { BOARDS, formatDate, isPostOwner, canWriteBoard } from "./board.js";

const state = initShell("community", loadState());
const $ = (selector) => document.querySelector(selector);
const editParam = new URLSearchParams(location.search).get("edit");
const editing = editParam ? state.posts.find((post) => String(post.id) === editParam) : null;
const MAX_CONTENT = 5000;
if (state.user.role === 'admin') $('#post-board').querySelectorAll('option').forEach(option => option.disabled = false);
const boardParam = new URLSearchParams(location.search).get('board');
if (canWriteBoard(state, boardParam)) $('#post-board').value = boardParam;
let dirty = false;

function updateLength() {
  $("#post-length").textContent = `${$("#post-content").value.length} / ${MAX_CONTENT}`;
}

function setupForm() {
  if (!editing) return;
  document.title = "글 수정 | 너랑 갈.지도";
  $("#write-eyebrow").textContent = "EDIT POST";
  $("#write-title").textContent = "게시글 수정";
  $("#post-submit").textContent = "수정 완료";
  $("#post-cancel").href = `./post-detail.html?id=${editing.id}`;
  $("#post-board").value = editing.board;
  $("#post-category").value = editing.category;
  $("#post-title").value = editing.title;
  $("#post-content").value = editing.content;
}

function formError() {
  if (!canWriteBoard(state, $("#post-board").value)) return [$("#post-board"), "이 게시판에는 글을 쓸 수 없어요."];
  if (!$("#post-category").value.trim()) return [$("#post-category"), "분류를 입력해주세요."];
  if (!$("#post-title").value.trim()) return [$("#post-title"), "제목을 입력해주세요."];
  if ($("#post-content").value.trim().length < 5) return [$("#post-content"), "내용을 5자 이상 입력해주세요."];
  return null;
}

function blockPage(message, target) {
  $("#post-form").hidden = true;
  showToast(message);
  setTimeout(() => { location.href = target; }, 700);
}

if (!ensureLoggedIn(state, "글을 쓰려면 로그인이 필요해요.")) {
  $("#post-form").hidden = true;
} else if (editParam && !editing) {
  blockPage("수정할 게시글을 찾을 수 없어요.", "./community.html");
} else if (editing && !isPostOwner(state, editing)) {
  blockPage("작성자만 게시글을 수정할 수 있어요.", `./post-detail.html?id=${editing.id}`);
} else {
  setupForm();
  updateLength();
}

$("#post-content").addEventListener("input", updateLength);
$("#post-form").addEventListener("input", () => { dirty = true; });
window.addEventListener("beforeunload", (event) => { if (dirty) event.preventDefault(); });

$("#post-form").addEventListener("submit", (event) => {
  event.preventDefault();
  if (!state.loggedIn || (editing && !isPostOwner(state, editing))) return showToast("글을 저장할 권한이 없어요.");
  const problem = formError();
  $("#post-error").textContent = problem ? problem[1] : "";
  if (problem) { problem[0].focus(); return; }
  const values = {
    board: $("#post-board").value,
    category: $("#post-category").value.trim(),
    title: $("#post-title").value.trim(),
    content: $("#post-content").value.trim().slice(0, MAX_CONTENT),
  };
  const post = editing || { id: Date.now(), ownerId: state.user.id, author: state.user.nickname, date: formatDate(), views: 0 };
  Object.assign(post, values, editing ? { updatedAt: Date.now() } : {});
  if (!editing) state.posts.unshift(post);
  if (!persist(state)) return;
  dirty = false;
  location.href = `./post-detail.html?id=${post.id}`;
});
