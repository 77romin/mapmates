import { companions as seedCompanions, posts as seedPosts, hotplaces as seedHotplaces } from "./data.js";

export const STORAGE_KEY = "neorang-galjido-v1";
export const CURRENT_USER_ID = "user-me";

const baseState = {
  favorites: [1, 2, 4, 5, 6, 8],
  itinerary: [2, 4, 1],
  companions: seedCompanions,
  joinedCompanions: [],
  posts: seedPosts,
  hotplaces: seedHotplaces,
  postComments: {},
  routeMode: "straight",
  tripSchedule: [],
  trip: { title: "제주, 우리 둘의 지도", startDate: "2026-10-12", endDate: "2026-10-14", people: 2 },
  loggedIn: true,
  user: {
    id: CURRENT_USER_ID,
    email: "gangmin@example.com",
    password: "",
    name: "김강민",
    nickname: "강민",
    birthDate: "1998-01-01",
    gender: "male",
    photo: "",
  },
};

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

export function loadState() {
  let saved = {};
  try { saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}"); } catch { saved = {}; }
  const state = { ...clone(baseState), ...saved };
  state.user = { ...clone(baseState.user), ...(saved.user || {}) };
  state.trip = { ...clone(baseState.trip), ...(saved.trip || {}) };
  state.companions = Array.isArray(saved.companions) ? saved.companions : clone(seedCompanions);
  state.posts = Array.isArray(saved.posts) ? saved.posts : clone(seedPosts);
  state.postComments = saved.postComments && typeof saved.postComments === "object" ? saved.postComments : {};
  state.itinerary = Array.isArray(saved.itinerary) ? saved.itinerary.map(Number).filter(Number.isFinite) : [...baseState.itinerary];
  if (!Array.isArray(saved.tripSchedule) || !saved.tripSchedule.length) {
    const times = ["09:30", "11:20", "14:10"];
    state.tripSchedule = state.itinerary.map((placeId, index) => ({ placeId, day: 1, time: times[index] || "16:00", memo: "" }));
  }
  return state;
}

export function saveState(state) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function escapeHtml(value = "") {
  return String(value).replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]);
}

export function userAvatar(user, className = "") {
  const name = escapeHtml(user.nickname || user.name || "여행자");
  const content = user.photo
    ? `<img src="${escapeHtml(user.photo)}" alt="${name} 프로필" />`
    : `<span>${escapeHtml((user.nickname || user.name || "?")[0])}</span>`;
  return `<span class="user-avatar ${user.gender === "female" ? "is-female" : "is-male"} ${className}">${content}</span>`;
}

export function showToast(message) {
  let root = document.querySelector("#toast-container");
  if (!root) {
    root = document.createElement("div");
    root.id = "toast-container";
    root.className = "toast-container";
    root.setAttribute("aria-live", "polite");
    document.body.append(root);
  }
  const item = document.createElement("div");
  item.className = "toast";
  item.textContent = message;
  root.append(item);
  setTimeout(() => item.remove(), 2600);
}

export function ensureLoggedIn(state, message = "로그인이 필요한 기능이에요.") {
  if (state.loggedIn) return true;
  showToast(message);
  const next = `${location.pathname.split("/").pop() || "index.html"}${location.search}`;
  setTimeout(() => { location.href = `./signup.html?next=${encodeURIComponent(next)}`; }, 500);
  return false;
}

export function initShell(page, state = loadState()) {
  document.querySelectorAll("[data-page-link]").forEach((link) => link.classList.toggle("is-active", link.dataset.pageLink === page));
  document.querySelectorAll("[data-user-name]").forEach((node) => { node.textContent = state.loggedIn ? state.user.name : "로그인"; });
  document.querySelectorAll("[data-user-avatar]").forEach((node) => {
    node.innerHTML = state.user.photo ? `<img src="${escapeHtml(state.user.photo)}" alt="" />` : escapeHtml((state.user.name || "?")[0]);
  });
  document.querySelectorAll(".profile-button").forEach((link) => {
    if (!state.loggedIn) link.setAttribute("href", "./signup.html");
  });
  document.querySelector("#global-search-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const keyword = document.querySelector("#global-search-input")?.value.trim();
    window.location.href = `./index.html${keyword ? `?q=${encodeURIComponent(keyword)}` : ""}`;
  });
  return state;
}

export function todayLabel(dateValue) {
  const date = new Date(`${dateValue}T00:00:00`);
  return Number.isNaN(date.getTime()) ? dateValue : `${date.getMonth() + 1}/${date.getDate()}`;
}
