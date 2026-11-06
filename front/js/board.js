

// 공지사항·관광 뉴스는 운영자가 관리하는 게시판이라 일반 회원은 여행정보 공유에만 글을 쓸 수 있다.
export const BOARDS = {
  travel: { label: "여행정보 공유", writable: true },
  notice: { label: "공지사항", writable: false },
  news: { label: "관광 뉴스", writable: false },
};

export function isBoard(value) {
  return Object.hasOwn(BOARDS, value);
}

export function isPostOwner(state, post) {
  return state.loggedIn && post.ownerId === state.user.id;
}

export function commentsOf(state, post) {
  return state.postComments[post.id] || [];
}

export function formatDate(date = new Date()) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())}`;
}

export function formatDateTime(timestamp) {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return "";
  return `${formatDate(date)} ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export function canWriteBoard(state, board) { return state.loggedIn && (board === 'travel' || state.user.role === 'admin'); }
