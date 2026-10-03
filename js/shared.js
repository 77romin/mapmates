import { companions as seedCompanions, posts as seedPosts, hotplaces as seedHotplaces, members as seedMembers, demoPlaceCorrections } from "./data.js";

export const STORAGE_KEY = "neorang-galjido-v1";
export const CURRENT_USER_ID = "user-me";

const baseState = {
  favorites: [1, 2, 4, 5, 6, 8],
  itinerary: [2, 4, 1],
  itineraryPlaces: {},
  companions: seedCompanions,
  joinedCompanions: [],
  posts: seedPosts,
  hotplaces: seedHotplaces,
  postComments: {},
  routeMode: "straight",
  tripSchedule: [],
  trip: { title: "제주, 우리 둘의 지도", startDate: "2026-10-12", endDate: "2026-10-14", people: 2 },
  loggedIn: false,
  members: seedMembers,
  plans: [],
  drafts: {},
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
  if (!saved || typeof saved !== "object" || Array.isArray(saved)) saved = {};
  const state = { ...clone(baseState), ...saved };
  state.user = { ...clone(baseState.user), ...(saved.user || {}) };
  state.trip = { ...clone(baseState.trip), ...(saved.trip || {}) };
  state.companions = Array.isArray(saved.companions) ? saved.companions : clone(seedCompanions);
  state.posts = Array.isArray(saved.posts) ? saved.posts : clone(seedPosts);
  state.postComments = saved.postComments && typeof saved.postComments === "object" ? saved.postComments : {};
  state.itineraryPlaces = saved.itineraryPlaces && typeof saved.itineraryPlaces === "object" ? saved.itineraryPlaces : {};
  state.itinerary = Array.isArray(saved.itinerary) ? saved.itinerary.map(Number).filter(Number.isFinite) : [...baseState.itinerary];
  if (!Array.isArray(saved.tripSchedule) || !saved.tripSchedule.length) {
    const times = ["09:30", "11:20", "14:10"];
    state.tripSchedule = state.itinerary.map((placeId, index) => ({ placeId, day: 1, time: times[index] || "16:00", memo: "" }));
  }
  state.members = Array.isArray(saved.members) ? saved.members : clone(seedMembers);
  if (!saved.members && saved.user?.email) state.members.push({ ...state.user, id: state.user.id || CURRENT_USER_ID });
  state.plans = Array.isArray(saved.plans) ? saved.plans : [];
  state.drafts = saved.drafts && typeof saved.drafts === 'object' ? saved.drafts : {};
  if (!saved.demoDataVersion) {
    const mergeSeeds = (existing, seeds) => [...existing.filter(entry => !seeds.some(seed => seed.id === entry.id)), ...clone(seeds)];
    state.members = mergeSeeds(state.members, seedMembers);
    state.posts = mergeSeeds(state.posts, seedPosts);
    state.companions = mergeSeeds(state.companions, seedCompanions);
    seedCompanions.forEach(item => {
      if (!state.plans.some(plan => plan.id === item.tripId)) state.plans.push({id:item.tripId, ownerId:item.ownerId, trip:{id:item.tripId,title:item.sourceTrip,startDate:item.startDate,endDate:item.endDate,people:4,budget:200000}, tripSchedule:clone(item.schedule), itinerary:item.schedule.map(entry => entry.placeId), itineraryPlaces:clone(item.places)});
    });
    seedPosts.forEach(post => {
      if (!state.postComments[post.id]?.length && post.board === 'travel') state.postComments[post.id] = [1,2].map((offset) => {
        const member = seedMembers[(post.id + offset) % seedMembers.length];
        return {id:`demo-comment-${post.id}-${offset}`,ownerId:member.id,nickname:member.nickname,photo:member.photo,gender:member.gender,content:offset === 1 ? '이런 여행 좋아요! 다음 일정에 참고할게요.' : '저도 다녀왔어요. 여유 있게 시간을 잡으니 좋더라고요.',createdAt:Date.parse('2026-10-01T12:00:00+09:00')};
      });
    });
    state.demoDataVersion = 1;
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); } catch { /* The demo remains readable if browser storage is unavailable. */ }
  }
  if (!state.demoCoordinateVersion) {
    const collections=[state.itineraryPlaces,...state.companions.map(item=>item.places),...state.plans.map(plan=>plan.itineraryPlaces),...Object.values(state.drafts).map(draft=>draft.itineraryPlaces),state.pendingGuestTrip?.itineraryPlaces];
    collections.filter(Boolean).forEach(collection=>demoPlaceCorrections.forEach(correction=>{
      const place=collection[correction.id];
      if(place?.title===correction.title && Math.abs(Number(place.lat)-correction.oldLat)<.000001 && Math.abs(Number(place.lng)-correction.oldLng)<.000001) Object.assign(place,{lat:correction.lat,lng:correction.lng});
    }));
    state.demoCoordinateVersion=1;
    try { localStorage.setItem(STORAGE_KEY,JSON.stringify(state)); } catch { /* Keep corrections available for this session. */ }
  }
  state.user = state.loggedIn ? state.members.find(member => member.id === state.user.id) || state.user : state.user;
  state.joinedCompanions = state.companions.filter(item => item.participants?.some(person => person.id === state.user.id)).map(item => item.id);
  return state;
}

export function saveState(state) {
  if (state.loggedIn && state.user.id) {
    const index = state.members.findIndex(member => member.id === state.user.id);
    if (index >= 0) state.members[index] = { ...state.user };
  }
  const draftOwner = state.loggedIn ? state.user.id : 'guest';
  state.drafts ||= {};
  state.drafts[draftOwner] = clone({trip:state.trip,tripSchedule:state.tripSchedule,itinerary:state.itinerary,itineraryPlaces:state.itineraryPlaces,favorites:state.favorites});
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function switchAccount(state, member) {
  const draft = state.pendingGuestTrip || state.drafts?.[member.id];
  delete state.pendingGuestTrip;
  Object.assign(state, draft ? clone(draft) : {trip:{...clone(baseState.trip), id:crypto.randomUUID(), title:'나의 새로운 여행'},tripSchedule:[],itinerary:[],itineraryPlaces:{},favorites:[]});
  state.user = {...member}; state.loggedIn = true;
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
  if (location.pathname.endsWith('planner.html') || (location.pathname.endsWith('index.html') && sessionStorage.getItem('neorang-workspace-return')==='mine')) {
    state.pendingGuestTrip = clone({trip:{...state.trip,id:crypto.randomUUID()},tripSchedule:state.tripSchedule,itinerary:state.itinerary,itineraryPlaces:state.itineraryPlaces,favorites:state.favorites});
    saveState(state);
  }
  const next = `${location.pathname.split("/").pop() || "index.html"}${location.search}`;
  setTimeout(() => { location.href = `./signup.html?next=${encodeURIComponent(next)}`; }, 500);
  return false;
}

export function initShell(page, state = loadState()) {
  document.querySelectorAll("[data-page-link]").forEach((link) => link.classList.toggle("is-active", link.dataset.pageLink === page));
  document.querySelectorAll("[data-user-name]").forEach((node) => { node.textContent = state.loggedIn ? state.user.name : "로그인"; });
  document.querySelectorAll("[data-user-avatar]").forEach((node) => {
    node.innerHTML = !state.loggedIn ? "○" : state.user.photo ? `<img src="${escapeHtml(state.user.photo)}" alt="" />` : escapeHtml((state.user.name || "?")[0]);
  });
  document.querySelectorAll(".profile-button").forEach((link) => {
    link.setAttribute("href", state.loggedIn ? "./mypage.html" : "./signup.html?mode=login");
  });
  const searchForm = document.querySelector("#global-search-form");
  if (searchForm && !searchForm.dataset.bound) {
  searchForm.dataset.bound = "true";
  searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const keyword = document.querySelector("#global-search-input")?.value.trim();
    window.location.href = `./index.html${keyword ? `?q=${encodeURIComponent(keyword)}` : ""}`;
  });
  }
  if (!document.querySelector('.intro-link')) {
    const link = document.createElement('a'); link.className = 'intro-link'; link.href = './intro.html'; link.textContent = '소개 ↗'; document.body.append(link);
  }
  return state;
}

export function todayLabel(dateValue) {
  const date = new Date(`${dateValue}T00:00:00`);
  return Number.isNaN(date.getTime()) ? dateValue : `${date.getMonth() + 1}/${date.getDate()}`;
}

export function logoutAccount(state) {
  saveState(state);
  const guest = state.drafts.guest || {trip:clone(baseState.trip),tripSchedule:[],itinerary:[],itineraryPlaces:{},favorites:[]};
  Object.assign(state,clone(guest)); state.loggedIn = false;
  state.user = {id:'',email:'',name:'',nickname:'',photo:'',password:''};
  saveState(state);
}
