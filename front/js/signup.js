import { escapeHtml, initShell, loadState, showToast, logoutAccount, switchAccount } from "./shared.js";
import { MIN_SIGNUP_AGE, ageOf, persist, readProfileImage, safeNextUrl } from "./account.js";

const state = initShell("signup", loadState());
const $ = (selector) => document.querySelector(selector);
const params = new URLSearchParams(location.search);
const nextUrl = safeNextUrl(params.get("next"));
let photo = "";

$("#signup-birth").max = new Date().toISOString().slice(0, 10);

function showMode(mode) {
  document.querySelectorAll("[data-account-tab]").forEach((tab) => {
    const active = tab.dataset.accountTab === mode;
    tab.classList.toggle("is-active", active);
    tab.setAttribute("aria-selected", String(active));
  });
  $("#signup-form").hidden = mode !== "signup";
  $("#login-form").hidden = mode !== "login";
  $("#account-title").textContent = mode === "login" ? "로그인" : "회원가입";
}

function renderAccountState() {
  $("#logged-in-notice").hidden = !state.loggedIn;
  $("#account-forms").hidden = state.loggedIn;
  if (state.loggedIn) $("#logged-in-text").textContent = `${state.user.nickname || state.user.name} 님(${state.user.email})으로 로그인되어 있어요. 다른 계정으로 가입하려면 먼저 로그아웃해주세요.`;
}

function renderPhoto() {
  $("#signup-preview").innerHTML = photo ? `<img src="${escapeHtml(photo)}" alt="">` : "＋";
  $("#signup-photo-remove").hidden = !photo;
}

function signupError() {
  const email = $("#signup-email");
  const password = $("#signup-password").value;
  const birthDate = $("#signup-birth").value;
  if (!email.value.trim() || !email.checkValidity()) return [email, "올바른 이메일 주소를 입력해주세요."];
  if (password.length < 8 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) return [$("#signup-password"), "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 해요."];
  if (password !== $("#signup-password-confirm").value) return [$("#signup-password-confirm"), "비밀번호가 일치하지 않아요."];
  if (!$("#signup-name").value.trim()) return [$("#signup-name"), "이름을 입력해주세요."];
  if (!$("#signup-nickname").value.trim()) return [$("#signup-nickname"), "닉네임을 입력해주세요."];
  if (!birthDate) return [$("#signup-birth"), "생년월일을 입력해주세요."];
  const age = ageOf(birthDate);
  if (age < 0 || new Date(`${birthDate}T00:00:00`) > new Date()) return [$("#signup-birth"), "생년월일을 다시 확인해주세요."];
  if (age < MIN_SIGNUP_AGE) return [$("#signup-birth"), `만 ${MIN_SIGNUP_AGE}세 이상부터 가입할 수 있어요.`];
  if (!$("#signup-gender").value) return [$("#signup-gender"), "성별을 선택해주세요."];
  return null;
}

document.querySelectorAll("[data-account-tab]").forEach((tab) => tab.addEventListener("click", () => showMode(tab.dataset.accountTab)));

$("#logout-button").addEventListener("click", () => {
  logoutAccount(state);
  initShell("signup", state);
  renderAccountState();
  showMode("login");
  showToast("로그아웃했어요.");
});

$("#signup-photo").addEventListener("change", async () => {
  const file = $("#signup-photo").files[0];
  if (!file) return;
  try {
    photo = await readProfileImage(file);
    renderPhoto();
  } catch (error) {
    showToast(error.message);
  } finally {
    $("#signup-photo").value = "";
  }
});

$("#signup-photo-remove").addEventListener("click", () => { photo = ""; renderPhoto(); });

$("#signup-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const problem = signupError();
  $("#signup-error").textContent = problem ? problem[1] : "";
  if (problem) { problem[0].focus(); return; }

  const email = $("#signup-email").value.trim().toLowerCase();
  if (state.members.some(member => member.email.toLowerCase() === email)) {
    $("#signup-error").textContent = "이미 가입된 이메일이에요. 로그인해주세요.";
    return;
  }
  if (state.members.some(member => member.nickname === $("#signup-nickname").value.trim())) {
    $("#signup-error").textContent = "이미 사용 중인 닉네임이에요.";
    return;
  }
  state.user = {
    id: crypto.randomUUID(),
    email,
    password: $("#signup-password").value,
    name: $("#signup-name").value.trim(),
    nickname: $("#signup-nickname").value.trim(),
    birthDate: $("#signup-birth").value,
    gender: $("#signup-gender").value,
    photo,
  };
  state.members.push({ ...state.user });
  switchAccount(state, state.user);
  if (!persist(state, "사진 용량이 커서 저장하지 못했어요. 다른 사진을 선택해주세요.")) return;
  showToast("가입을 환영해요!");
  location.href = nextUrl;
});

$("#login-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const email = $("#login-email").value.trim().toLowerCase();
  const password = $("#login-password").value;
  const member = state.members.find(entry => entry.email.toLowerCase() === email);
  let message = "";
  if (!email || !password) message = "이메일과 비밀번호를 입력해주세요.";
  else if (!member || member.password !== password) message = "이메일 또는 비밀번호가 맞지 않아요.";
  $("#login-error").textContent = message;
  if (message) return;
  switchAccount(state, member);
  persist(state);
  location.href = nextUrl;
});

renderAccountState();
renderPhoto();
showMode(params.get("mode") === "signup" ? "signup" : "login");

// Frontend demo recovery: identity fields are only a local simulation, not email verification.
$("#recover-form").addEventListener("submit", event => {
  event.preventDefault();
  const member = state.members.find(user => user.email.toLowerCase() === $("#recover-email").value.trim().toLowerCase() && user.name === $("#recover-name").value.trim() && user.birthDate === $("#recover-birth").value);
  const password = $("#recover-password").value;
  if (!member) return $("#recover-error").textContent = "입력한 회원정보를 확인해주세요.";
  if (password.length < 8 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) return $("#recover-error").textContent = "비밀번호는 영문과 숫자를 포함해 8자 이상 입력해주세요.";
  member.password = password;
  if (!persist(state)) return;
  $("#recover-error").textContent = "비밀번호를 변경했어요. 새 비밀번호로 로그인해주세요.";
});
$("#demo-accounts").innerHTML = state.members.filter(member => member.demo).map(member => `<button type="button" class="demo-account" data-demo-id="${member.id}">${escapeHtml(member.nickname)}<small>${escapeHtml(member.email)}${member.role === 'admin' ? ' · 운영자' : ''}</small></button>`).join('');
$("#demo-accounts").addEventListener('click', event => {
  const member = state.members.find(member => member.id === event.target.closest('[data-demo-id]')?.dataset.demoId);
  if (!member) return;
  $("#login-email").value = member.email; $("#login-password").value = member.password; showMode('login'); $("#login-email").focus();
});
