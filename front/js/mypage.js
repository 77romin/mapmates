import { escapeHtml, initShell, loadState, showToast, logoutAccount } from "./shared.js";
import { GENDER_LABELS, clearUserContent, emptyUser, persist, readProfileImage, syncUserProfile } from "./account.js";
import { STATUS_LABELS, syncCompanion } from "./companion-utils.js";

const state = loadState();
const $ = (selector) => document.querySelector(selector);

if (!state.loggedIn) location.replace("./signup.html?next=mypage.html");

let nextPhoto = state.user.photo;
initShell("mypage", state);

function avatarMarkup(photo, nickname) {
  return photo ? `<img src="${escapeHtml(photo)}" alt="">` : escapeHtml((nickname || "?")[0]);
}

function activityItems(items, empty) {
  return items.join("") || `<li class="helper-text">${empty}</li>`;
}

function renderActivity() {
  state.companions.forEach(syncCompanion);
  const owned = state.companions.filter((item) => item.ownerId === state.user.id);
  const joined = state.companions.filter((item) => item.ownerId !== state.user.id && item.participants.some((person) => person.id === state.user.id));
  const posts = state.posts.filter((post) => post.ownerId === state.user.id);
  const companionLink = (item) => `<li><a href="./companion-detail.html?id=${item.id}"><span class="status-badge ${item.status}">${STATUS_LABELS[item.status]}</span>${escapeHtml(item.title)}</a></li>`;
  $("#owned-count").textContent = owned.length;
  $("#joined-count").textContent = joined.length;
  $("#post-count").textContent = posts.length;
  $("#owned-companions").innerHTML = activityItems(owned.map(companionLink), "여행계획에서 동행을 모집해보세요.");
  $("#joined-companions").innerHTML = activityItems(joined.map(companionLink), "아직 참가한 동행이 없어요.");
  $("#my-posts").innerHTML = activityItems(posts.map((post) => `<li><a href="./post-detail.html?id=${post.id}">${escapeHtml(post.title)}<time>${escapeHtml(post.date)}</time></a></li>`), "커뮤니티에 첫 글을 남겨보세요.");
}

function render() {
  $("#profile-heading").textContent = `${state.user.nickname || state.user.name} 님의 여행 지도`;
  $("#profile-nickname").value = state.user.nickname;
  $("#fixed-email").value = state.user.email;
  $("#fixed-name").value = state.user.name;
  $("#fixed-birth").value = state.user.birthDate || "미입력";
  $("#fixed-gender").value = GENDER_LABELS[state.user.gender] || "미입력";
  $("#profile-avatar").innerHTML = avatarMarkup(nextPhoto, state.user.nickname);
  $("#profile-avatar").className = `photo-preview ${state.user.gender === "female" ? "is-female" : "is-male"}`;
  $("#profile-photo-remove").hidden = !nextPhoto;
  $("#withdraw-password-field").hidden = !state.user.password;
  renderActivity();
}

$("#profile-photo").addEventListener("change", async () => {
  const file = $("#profile-photo").files[0];
  if (!file) return;
  try {
    nextPhoto = await readProfileImage(file);
    render();
    showToast("미리보기를 확인한 뒤 프로필 저장을 눌러주세요.");
  } catch (error) {
    showToast(error.message);
  } finally {
    $("#profile-photo").value = "";
  }
});

$("#profile-photo-remove").addEventListener("click", () => { nextPhoto = ""; render(); });

$("#profile-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const nickname = $("#profile-nickname").value.trim();
  if (!nickname) { $("#profile-nickname").focus(); return showToast("닉네임을 입력해주세요."); }
  const previous = { nickname: state.user.nickname, photo: state.user.photo };
  state.user.nickname = nickname;
  state.user.photo = nextPhoto;
  syncUserProfile(state);
  if (!persist(state, "사진 용량이 커서 저장하지 못했어요. 다른 사진을 선택해주세요.")) {
    Object.assign(state.user, previous);
    syncUserProfile(state);
    nextPhoto = previous.photo;
    return render();
  }
  initShell("mypage", state);
  render();
  showToast("닉네임과 프로필 사진을 저장했어요.");
});

$("#settings-toggle").addEventListener("click", () => {
  const panel = $("#settings-panel");
  panel.hidden = !panel.hidden;
  $("#settings-toggle").setAttribute("aria-expanded", String(!panel.hidden));
  if (!panel.hidden) panel.scrollIntoView({ behavior: "smooth", block: "start" });
});

$("#logout-button").addEventListener("click", () => {
  logoutAccount(state);
  location.href = "./index.html";
});

$("#password-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const current = $("#current-password").value;
  const nextPassword = $("#new-password").value;
  let message = "";
  if (state.user.password && current !== state.user.password) message = "현재 비밀번호가 맞지 않아요.";
  else if (nextPassword.length < 8 || !/[A-Za-z]/.test(nextPassword) || !/\d/.test(nextPassword)) message = "새 비밀번호는 영문과 숫자를 포함해 8자 이상이어야 해요.";
  else if (nextPassword !== $("#new-password-confirm").value) message = "새 비밀번호가 일치하지 않아요.";
  else if (nextPassword === state.user.password) message = "기존 비밀번호와 다른 비밀번호를 입력해주세요.";
  $("#password-error").textContent = message;
  if (message) return;
  state.user.password = nextPassword;
  persist(state);
  event.target.reset();
  render();
  showToast("비밀번호를 변경했어요.");
});

$("#withdraw-form").addEventListener("submit", (event) => {
  event.preventDefault();
  if (state.user.password && $("#withdraw-password").value !== state.user.password) return showToast("비밀번호가 맞지 않아요.");
  if (!confirm("회원탈퇴 시 작성한 게시글·댓글·모집글과 동행 참가 기록이 모두 삭제되며 복구할 수 없습니다. 탈퇴할까요?")) return;
  clearUserContent(state);
  state.loggedIn = false;
  state.members = state.members.filter(member => member.id !== state.user.id);
  state.plans = state.plans.filter(plan => plan.ownerId !== state.user.id);
  state.user = emptyUser();
  persist(state);
  alert("회원탈퇴가 완료됐어요. 그동안 이용해주셔서 감사합니다.");
  location.href = "./index.html";
});

if (state.loggedIn) render();
