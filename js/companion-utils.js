import { CURRENT_USER_ID } from "./shared.js";

export const STATUS_LABELS = { open: "모집 중", soon: "마감 임박", closed: "모집 마감" };

const SEED_MEMBERS = [
  { nickname: "바다산책", gender: "female" },
  { nickname: "걷는여행자", gender: "male" },
  { nickname: "필름로그", gender: "female" },
  { nickname: "주말기차", gender: "male" },
];

export function companionCapacity(item) {
  const parsed = Number(String(item.people || "").split("/")[1]?.replace(/\D/g, ""));
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 4;
}

// 시드 모집글은 참가자 정보가 없으므로 "2/4명" 같은 인원 표기에 맞춰 같은 결과가 나오도록 만든다.
function seedParticipants(item) {
  const count = Math.max(1, Number(String(item.people || "1").split("/")[0].replace(/\D/g, "")) || 1);
  const owner = { id: item.ownerId || `seed-${item.id}`, nickname: item.author, photo: "", gender: item.id % 2 ? "female" : "male" };
  const members = SEED_MEMBERS.slice(0, count - 1).map((member, index) => ({ id: `seed-${item.id}-${index + 1}`, photo: "", ...member }));
  return [owner, ...members];
}

export function syncCompanion(item) {
  if (!Array.isArray(item.participants) || !item.participants.length) item.participants = seedParticipants(item);
  const maximum = Math.max(companionCapacity(item), item.participants.length);
  item.people = `${item.participants.length}/${maximum}명`;
  if (item.closed || item.participants.length >= maximum) item.status = "closed";
  else item.status = maximum - item.participants.length <= 1 ? "soon" : "open";
  return item;
}

export function isCompanionOwner(state, item) {
  return state.loggedIn && item.ownerId === CURRENT_USER_ID;
}

export function hasJoined(state, item) {
  return state.loggedIn && (item.participants || []).some((person) => person.id === CURRENT_USER_ID);
}
