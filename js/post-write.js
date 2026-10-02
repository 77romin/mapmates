import { CURRENT_USER_ID, initShell, loadState, saveState } from "./shared.js";
const state = initShell("community", loadState());
const $ = (selector) => document.querySelector(selector);
const editId = Number(new URLSearchParams(location.search).get("edit"));
const editing = state.posts.find((post) => Number(post.id) === editId && post.ownerId === CURRENT_USER_ID);
if (editing) {
  $("#post-board").value = editing.board;
  $("#post-category").value = editing.category;
  $("#post-title").value = editing.title;
  $("#post-content").value = editing.content;
}
$("#post-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const values = { board: $("#post-board").value, category: $("#post-category").value.trim(), title: $("#post-title").value.trim(), content: $("#post-content").value.trim() };
  const post = editing || { id: Date.now(), ownerId: CURRENT_USER_ID, author: state.user.nickname, date: new Date().toISOString().slice(0, 10).replaceAll("-", "."), views: 0, comments: 0 };
  Object.assign(post, values);
  if (!editing) state.posts.unshift(post);
  saveState(state); location.href = `./post-detail.html?id=${post.id}`;
});
