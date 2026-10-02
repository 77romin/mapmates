import { initShell, loadState, saveState, showToast } from "./shared.js";
const state = initShell("mypage", loadState());
const $ = (selector) => document.querySelector(selector);
let nextPhoto = state.user.photo;
function render() {
  $("#profile-heading").textContent = `${state.user.name} 님의 여행 지도`;
  $("#profile-nickname").value = state.user.nickname;
  $("#fixed-email").value = state.user.email;
  $("#fixed-name").value = state.user.name;
  $("#fixed-birth").value = state.user.birthDate || "미등록";
  $("#fixed-gender").value = state.user.gender === "female" ? "여성" : "남성";
  $("#profile-avatar").innerHTML = state.user.photo ? `<img src="${state.user.photo}" alt="프로필 사진">` : state.user.nickname[0];
}
$("#profile-photo").addEventListener("change", () => { const file = $("#profile-photo").files[0]; if (!file) return; const reader = new FileReader(); reader.onload = () => { nextPhoto = reader.result; $("#profile-avatar").innerHTML = `<img src="${nextPhoto}" alt="프로필 사진">`; }; reader.readAsDataURL(file); });
$("#profile-form").addEventListener("submit", (event) => { event.preventDefault(); state.user.nickname = $("#profile-nickname").value.trim(); state.user.photo = nextPhoto; saveState(state); initShell("mypage", state); render(); showToast("닉네임과 프로필 사진을 수정했어요."); });
$("#settings-toggle").addEventListener("click", () => { const panel = $("#settings-panel"); panel.hidden = !panel.hidden; $("#settings-toggle").setAttribute("aria-expanded", String(!panel.hidden)); });
$("#password-form").addEventListener("submit", (event) => { event.preventDefault(); if (state.user.password && $("#current-password").value !== state.user.password) return showToast("현재 비밀번호가 맞지 않아요."); state.user.password = $("#new-password").value; saveState(state); event.target.reset(); showToast("비밀번호를 변경했어요."); });
$("#withdraw-button").addEventListener("click", () => { if (!confirm("회원탈퇴 후 계정 정보는 복구할 수 없습니다. 탈퇴할까요?")) return; state.loggedIn = false; saveState(state); location.href = "./index.html"; });
render();
