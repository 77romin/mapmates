import { CURRENT_USER_ID, loadState, saveState, showToast } from "./shared.js";
const state = loadState();
const $ = (selector) => document.querySelector(selector);
let photo = "";
$("#signup-photo").addEventListener("change", () => { const file = $("#signup-photo").files[0]; if (!file) return; const reader = new FileReader(); reader.onload = () => { photo = reader.result; $("#signup-preview").innerHTML = `<img src="${photo}" alt="프로필 미리보기">`; }; reader.readAsDataURL(file); });
$("#signup-form").addEventListener("submit", (event) => { event.preventDefault(); if ($("#signup-password").value !== $("#signup-password-confirm").value) return showToast("비밀번호가 일치하지 않아요."); state.user = { id: CURRENT_USER_ID, email: $("#signup-email").value.trim(), password: $("#signup-password").value, name: $("#signup-name").value.trim(), nickname: $("#signup-nickname").value.trim(), birthDate: $("#signup-birth").value, gender: $("#signup-gender").value, photo }; state.loggedIn = true; saveState(state); location.href = "./mypage.html"; });
