import { saveState, showToast } from "./shared.js";

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const PROFILE_IMAGE_SIZE = 320;

export const GENDER_LABELS = { male: "남성", female: "여성" };

export function emptyUser() {
  return { id: "", email: "", password: "", name: "", nickname: "", birthDate: "", gender: "", photo: "" };
}

// localStorage 용량을 아끼기 위해 프로필 사진을 정사각형 JPEG로 줄여서 저장한다.
export function readProfileImage(file) {
  if (!file.type.startsWith("image/")) return Promise.reject(new Error("이미지 파일만 등록할 수 있어요."));
  if (file.size > MAX_IMAGE_BYTES) return Promise.reject(new Error("프로필 사진은 10MB 이하로 선택해주세요."));
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("사진을 읽지 못했어요."));
    reader.onload = () => {
      const image = new Image();
      image.onerror = () => reject(new Error("지원하지 않는 이미지 형식이에요."));
      image.onload = () => {
        const side = Math.min(image.width, image.height);
        const canvas = document.createElement("canvas");
        canvas.width = canvas.height = Math.min(PROFILE_IMAGE_SIZE, side);
        canvas.getContext("2d").drawImage(image, (image.width - side) / 2, (image.height - side) / 2, side, side, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/jpeg", 0.85));
      };
      image.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

export function persist(state, message) {
  try {
    saveState(state);
    return true;
  } catch {
    showToast(message || "브라우저 저장 공간이 부족해 저장하지 못했어요.");
    return false;
  }
}

export function safeNextUrl(next, fallback = "./mypage.html") {
  if (!next || next.includes(":") || next.startsWith("//") || next.startsWith("/")) return fallback;
  return `./${next.replace(/^\.\//, "")}`;
}

export const MIN_SIGNUP_AGE = 14;

export function ageOf(birthDate) {
  const birth = new Date(`${birthDate}T00:00:00`);
  if (Number.isNaN(birth.getTime())) return -1;
  const today = new Date();
  return today.getFullYear() - birth.getFullYear() - (today < new Date(today.getFullYear(), birth.getMonth(), birth.getDate()) ? 1 : 0);
}

// 닉네임·사진을 바꾸면 내가 작성한 글, 댓글, 모집글, 참가자 목록에 함께 반영한다.
export function syncUserProfile(state) {
  const { nickname, photo, gender } = state.user;
  state.plans.filter(plan => plan.ownerId === state.user.id).forEach(plan => { plan.author = nickname; });
  state.posts.filter((post) => post.ownerId === state.user.id).forEach((post) => { post.author = nickname; });
  Object.values(state.postComments).forEach((comments) => comments
    .filter((comment) => comment.ownerId === state.user.id)
    .forEach((comment) => Object.assign(comment, { nickname, photo, gender })));
  state.companions.forEach((item) => {
    if (item.ownerId === state.user.id) Object.assign(item, { author: nickname, avatar: nickname[0] });
    item.participants?.filter((person) => person.id === state.user.id).forEach((person) => Object.assign(person, { nickname, photo, gender }));
  });
}

// 회원탈퇴 또는 다른 계정으로 교체할 때 현재 계정이 남긴 데이터를 정리한다.
export function clearUserContent(state) {
  const removedPostIds = state.posts.filter((post) => post.ownerId === state.user.id).map((post) => String(post.id));
  state.posts = state.posts.filter((post) => post.ownerId !== state.user.id);
  removedPostIds.forEach((id) => { delete state.postComments[id]; });
  Object.keys(state.postComments).forEach((id) => {
    state.postComments[id] = state.postComments[id].filter((comment) => comment.ownerId !== state.user.id);
  });
  state.companions = state.companions.filter((item) => item.ownerId !== state.user.id);
  state.companions.forEach((item) => {
    if (!Array.isArray(item.participants)) return;
    const before = item.participants.length;
    item.participants = item.participants.filter((person) => person.id !== state.user.id);
    if (item.participants.length !== before) item.people = item.people.replace(/^\d+/, String(item.participants.length));
  });
  state.joinedCompanions = [];
  delete state.drafts?.[state.user.id];
}
