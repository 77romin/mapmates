import { ensureLoggedIn, escapeHtml, loadState, showToast, userAvatar } from './shared.js';
import { persist } from './account.js';
import { formatDateTime } from './board.js';

// 게시글과 동행 Q&A가 같은 저장·권한·대댓글 흐름을 사용한다.
export function initComments({ state, collection, id, ownerId, next }) {
  const $ = selector => document.querySelector(selector);
  state[collection] ||= {};
  state[collection][id] ||= [];
  let editingId = null, replyingId = null;
  const list = () => state[collection][id];
  const find = key => list().find(comment => String(comment.id) === String(key));
  const mine = comment => state.loggedIn && comment.ownerId === state.user.id;
  const canDelete = comment => mine(comment) || (state.loggedIn && ownerId === state.user.id);
  const sendIcon = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m22 2-7 20-4-9-9-4Z M22 2 11 13"/></svg>';
  function markup(comment, reply = false) {
    const key = escapeHtml(String(comment.id));
    const actions = comment.deleted ? '' : `<div class="owner-actions"><button type="button" data-action="reply" data-id="${key}">답글</button>${mine(comment) ? `<button type="button" data-action="edit" data-id="${key}">수정</button>` : ''}${canDelete(comment) ? `<button type="button" data-action="delete" data-id="${key}">삭제</button>` : ''}</div>`;
    const body = comment.deleted ? '<p class="helper-text">삭제된 댓글입니다.</p>' : editingId === comment.id ? `<form class="comment-edit-form" data-id="${key}"><textarea aria-label="댓글 수정" maxlength="500" required>${escapeHtml(comment.content)}</textarea><div class="owner-actions"><button type="submit">저장</button><button type="button" data-action="cancel">취소</button></div></form>` : `<p>${comment.replyTo ? `<span class="reply-mention">@${escapeHtml(comment.replyTo)} </span>` : ''}${escapeHtml(comment.content)}</p>`;
    const replyForm = replyingId === comment.id ? `<form class="comment-form reply-form" data-parent="${key}"><label class="field"><span class="sr-only">${escapeHtml(comment.nickname)}에게 답글</span><textarea aria-label="답글 내용" maxlength="500" required placeholder="답글을 입력하세요"></textarea></label><button class="button button-primary comment-send" type="submit" aria-label="답글 등록">${sendIcon}</button><button class="text-button" type="button" data-action="cancel">취소</button></form>` : '';
    return `<article class="comment-item${reply ? ' is-reply' : ''}" data-comment-id="${key}"><div class="comment-head"><div class="comment-author">${comment.deleted ? '' : userAvatar(comment, 'is-small')}<strong>${comment.deleted ? '삭제된 댓글' : escapeHtml(comment.nickname)}</strong>${!comment.deleted && comment.ownerId === ownerId ? '<small class="writer-badge">작성자</small>' : ''}<time>${formatDateTime(comment.createdAt || comment.id)}${comment.updatedAt ? ' · 수정됨' : ''}</time></div>${actions}</div>${body}${replyForm}</article>`;
  }
  function render() {
    $('#comment-count').textContent = list().filter(c => !c.deleted).length;
    $('#comment-form').hidden = !state.loggedIn;
    $('#comment-login').hidden = state.loggedIn;
    $('#comment-login a').href = `./signup.html?next=${encodeURIComponent(next)}`;
    const roots = list().filter(c => !c.parentId || !find(c.parentId));
    $('#comment-list').innerHTML = roots.map(root => `<div class="comment-thread">${markup(root)}${list().filter(c => String(c.parentId) === String(root.id)).map(c => markup(c, true)).join('')}</div>`).join('') || '<p class="helper-text">첫 댓글을 남겨보세요.</p>';
    $('#comment-list form textarea')?.focus();
  }
  function change(mutate) {
    const before = structuredClone(list());
    state[collection] = loadState()[collection] || state[collection];
    state[collection][id] ||= [];
    if (mutate() === false) return false;
    if (!persist(state)) { state[collection][id] = before; return false; }
    return true;
  }
  function add(content, parent) {
    if (!ensureLoggedIn(state, '댓글을 쓰려면 로그인이 필요해요.')) return false;
    if (!content.trim()) { showToast('댓글 내용을 입력해주세요.'); return false; }
    return change(() => {
      const target = parent && find(parent);
      if (parent && (!target || target.deleted)) { showToast('답글을 달 댓글이 삭제되었어요.'); return false; }
      const now = Date.now();
      list().push({ id: crypto.randomUUID(), ownerId: state.user.id, nickname: state.user.nickname, photo: state.user.photo, gender: state.user.gender, content: content.trim().slice(0, 500), createdAt: now, ...(target ? { parentId: target.parentId || target.id, replyTo: target.nickname } : {}) });
    });
  }
  $('#comment-content').addEventListener('input', () => { $('#comment-length').textContent = `${$('#comment-content').value.length} / 500`; });
  $('#comment-form').addEventListener('submit', event => {
    event.preventDefault();
    if (!add($('#comment-content').value)) return;
    event.target.reset(); $('#comment-length').textContent = '0 / 500'; render(); showToast('댓글을 등록했어요.');
  });
  $('#comment-list').addEventListener('click', event => {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const { action } = button.dataset;
    if (action === 'cancel') { editingId = replyingId = null; render(); return; }
    const comment = find(button.dataset.id);
    if (!comment || comment.deleted) return;
    if (action === 'reply') {
      if (!ensureLoggedIn(state, '답글을 쓰려면 로그인이 필요해요.')) return;
      editingId = null; replyingId = comment.id; render();
    }
    if (action === 'edit' && mine(comment)) { replyingId = null; editingId = comment.id; render(); }
    if (action === 'delete' && canDelete(comment) && confirm('이 댓글을 삭제할까요? 답글은 유지됩니다.')) {
      if (!change(() => {
        const current = find(button.dataset.id);
        if (!current || !canDelete(current)) return;
        if (list().some(c => String(c.parentId) === String(current.id))) Object.assign(current, { deleted: true, content: '', nickname: '', photo: '', replyTo: '' });
        else state[collection][id] = list().filter(c => c !== current);
      })) return;
      editingId = replyingId = null; render(); showToast('댓글을 삭제했어요.');
    }
  });
  $('#comment-list').addEventListener('submit', event => {
    event.preventDefault();
    const form = event.target;
    const content = form.querySelector('textarea').value.trim();
    if (form.dataset.parent) {
      if (!add(content, form.dataset.parent)) return;
      replyingId = null; render(); showToast('답글을 등록했어요.'); return;
    }
    if (!content) return showToast('댓글 내용을 입력해주세요.');
    if (!mine(find(form.dataset.id) || {})) return showToast('내 댓글만 수정할 수 있어요.');
    if (!change(() => { const comment = find(form.dataset.id); if (comment && mine(comment) && !comment.deleted) Object.assign(comment, { content: content.slice(0, 500), updatedAt: Date.now() }); })) return;
    editingId = null; render(); showToast('댓글을 수정했어요.');
  });
  render();
}
