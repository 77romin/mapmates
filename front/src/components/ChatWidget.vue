<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import http, { AI_SERVER } from '@/api/http'
import { useRoute, useRouter } from 'vue-router'
import { useDraftStore } from '@/stores/draft'
import { useAuthStore } from '@/stores/auth'
import { useAiMapStore } from '@/stores/aiMap'
import { planFromCourse } from '@/utils/coursePlan'

const draft = useDraftStore()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const aiMap = useAiMapStore()
// ---------- 창 상태: 열림·접힘·위치·크기를 기억한다 ----------
const UI_KEY = 'mm_chat_ui'
const MIN_W = 300
const MIN_H = 320
const EDGE = 12 // 화면 가장자리와 띄우는 최소 간격
const DEFAULT = { w: 380, h: 580 }
const saved = (() => {
  try {
    return JSON.parse(localStorage.getItem(UI_KEY)) || {}
  } catch {
    return {}
  }
})()
const wide = window.matchMedia('(min-width: 768px)')
const floating = ref(wide.matches) // 넓은 화면에서만 옮기고 크기를 바꿀 수 있다
// 기본은 열림. 좁은 화면에서는 화면을 가리므로 닫힌 채로 시작한다. 직접 닫거나 연 선택은 기억한다
const open = ref(saved.open ?? wide.matches)
const mini = ref(!!saved.mini) // 제목 줄만 남기고 접었는지
const rect = reactive({ w: saved.w || DEFAULT.w, h: saved.h || DEFAULT.h, x: saved.x ?? null, y: saved.y ?? null })

/** 창이 화면 밖으로 나가지 않게 위치와 크기를 맞춘다 */
function clamp() {
  const vw = window.innerWidth
  const vh = window.innerHeight
  rect.w = Math.min(Math.max(rect.w, MIN_W), vw - EDGE * 2)
  rect.h = Math.min(Math.max(rect.h, MIN_H), vh - EDGE * 2)
  if (rect.x == null) rect.x = vw - rect.w - 20 // 처음에는 오른쪽 아래
  if (rect.y == null) rect.y = vh - rect.h - 20
  rect.x = Math.min(Math.max(rect.x, EDGE), vw - rect.w - EDGE)
  rect.y = Math.min(Math.max(rect.y, EDGE), vh - (mini.value ? 48 : rect.h) - EDGE)
}
const boxStyle = computed(() =>
  floating.value ? { left: rect.x + 'px', top: rect.y + 'px', width: rect.w + 'px', height: mini.value ? 'auto' : rect.h + 'px' } : {},
)
function resetBox(e) {
  if (e.target.closest('button')) return
  Object.assign(rect, DEFAULT, { x: null, y: null })
  clamp()
}

// 끌어서 옮기기(move), 모서리를 끌어 크기 바꾸기(tl: 왼쪽 위, br: 오른쪽 아래)
let drag = null
function dragStart(e, kind) {
  if (!floating.value || (kind === 'move' && e.target.closest('button'))) return
  drag = { kind, px: e.clientX, py: e.clientY, x: rect.x, y: rect.y, w: rect.w, h: rect.h }
  e.currentTarget.setPointerCapture(e.pointerId)
  e.preventDefault()
}
function dragMove(e) {
  if (!drag) return
  const dx = e.clientX - drag.px
  const dy = e.clientY - drag.py
  if (drag.kind === 'move') {
    rect.x = drag.x + dx
    rect.y = drag.y + dy
  } else if (drag.kind === 'br') {
    rect.w = Math.min(Math.max(drag.w + dx, MIN_W), window.innerWidth - EDGE - drag.x)
    rect.h = Math.min(Math.max(drag.h + dy, MIN_H), window.innerHeight - EDGE - drag.y)
  } else {
    // 왼쪽 위 모서리: 오른쪽 아래는 제자리에 둔다
    const right = drag.x + drag.w
    const bottom = drag.y + drag.h
    rect.w = Math.min(Math.max(drag.w - dx, MIN_W), right - EDGE)
    rect.h = Math.min(Math.max(drag.h - dy, MIN_H), bottom - EDGE)
    rect.x = right - rect.w
    rect.y = bottom - rect.h
  }
  clamp()
}
function dragEnd(e) {
  if (!drag) return
  drag = null
  e.currentTarget.releasePointerCapture?.(e.pointerId)
}

const onWide = (e) => {
  floating.value = e.matches
  clamp()
}
onMounted(() => {
  clamp()
  window.addEventListener('resize', clamp)
  wide.addEventListener('change', onWide)
  if (open.value) scrollDown()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', clamp)
  wide.removeEventListener('change', onWide)
})
// 지도가 채팅창에 가려지는 폭을 알 수 있게 창의 자리를 알려 둔다
watch([open, mini, floating, () => [rect.x, rect.y, rect.w, rect.h]], () => {
  aiMap.chatBox = open.value && !mini.value && floating.value ? { x: rect.x, y: rect.y, w: rect.w, h: rect.h } : null
}, { immediate: true })
watch(mini, () => {
  clamp() // 펼칠 때 아래로 넘치지 않게
  scrollDown()
})
watch([open, mini, () => [rect.x, rect.y, rect.w, rect.h]], () => {
  try {
    localStorage.setItem(UI_KEY, JSON.stringify({ open: open.value, mini: mini.value, ...rect }))
  } catch {
    /* 저장소를 쓸 수 없으면 기억하지 않는다 */
  }
})
const mode = ref('trip') // trip: Spring AI Planner, doc: FastAPI RAG
const input = ref('')
const loading = ref(false)
const uploading = ref(false)
const listEl = ref(null)
const fileEl = ref(null)
const messages = ref([
  { role: 'ai', text: '안녕하세요, AI 길잡이예요. 여행지 추천, 코스, 날씨를 물어보세요.\n예) 이번 주말 부산 1박2일 코스 짜줘' },
])
const TOOL_LABEL = {
  SEARCH_ATTRACTIONS: '관광지 검색', GET_WEATHER: '날씨 조회', OPTIMIZE_ROUTE: '동선 최적화',
  GET_MY_PLANS: '내 플랜 조회', SEARCH_PERFORMANCES: '공연 검색', SEARCH_DOCUMENTS: '문서 검색',
}

async function scrollDown() {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  messages.value.push({ role: 'user', text })
  input.value = ''
  loading.value = true
  scrollDown()
  try {
    if (mode.value === 'trip') {
      // 후속 요청("한 곳만 바꿔줘")을 이해하도록 직전 대화를 함께 보낸다 (방금 입력한 질문은 제외)
      const history = messages.value.slice(1, -1).filter((m) => m.role === 'user' || (m.role === 'ai' && m.trip))
        .slice(-6).map((m) => ({ role: m.role, text: m.text }))
      const res = await http.post('/ai/chat', { message: text, mode: 'planner', history })
      const view = asCourse(res)
      messages.value.push({ role: 'ai', trip: true, text: (res.answer || '').replace(/\*\*/g, ''), tools: (res.steps || []).map((s) => TOOL_LABEL[s.tool] || s.tool), places: res.places || [], course: res.course || null, view })
      // 메인 지도를 보고 있으면 추천 결과를 바로 지도에 올린다. 다른 화면에서는 '지도에서 보기'를 눌러야 옮겨 간다
      if (view && route.path === '/') aiMap.show(messages.value[messages.value.length - 1].view)
    } else {
      // 문서 Q&A: fetch API 로 FastAPI RAG 서버의 /chat 호출
      const response = await fetch(`${AI_SERVER}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text }),
      })
      const data = await response.json()
      if (!response.ok) throw new Error(data.detail || '응답을 받지 못했습니다.')
      messages.value.push({ role: 'ai', text: data.answer, doc: data.used_documents, notice: data.notice, sources: data.sources || [] })
    }
  } catch (e) {
    const msg = e instanceof TypeError ? 'AI 서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해 주세요.' : e.message
    messages.value.push({ role: 'error', text: msg })
  } finally {
    loading.value = false
    scrollDown()
  }
}

async function upload(e) {
  const file = e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (!/\.(txt|md|pdf)$/i.test(file.name)) {
    messages.value.push({ role: 'error', text: 'txt, md, pdf 파일만 업로드할 수 있습니다.' })
    return scrollDown()
  }
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    const response = await fetch(`${AI_SERVER}/upload`, { method: 'POST', body: form })
    const data = await response.json()
    if (!response.ok) throw new Error(data.detail || '업로드에 실패했습니다.')
    messages.value.push({ role: 'ai', text: `📄 ${data.message}\n이제 이 문서 내용을 질문해 보세요.` })
  } catch (e) {
    messages.value.push({ role: 'error', text: e instanceof TypeError ? 'AI 서버에 연결할 수 없습니다.' : e.message })
  } finally {
    uploading.value = false
    scrollDown()
  }
}

function addPlace(p) {
  draft.add(p)
}
/** 응답에 코스가 없고 장소만 있으면(한두 곳 추천) 지도에 보여 줄 수 있게 하루짜리 묶음으로 만든다 */
const asCourse = (res) =>
  res.course || (res.places?.length ? { title: '추천 장소', days: 1, stops: res.places.map((p) => ({ ...p, dayNo: 1 })) } : null)

/** 코스를 메인 지도에 보여 준다. 다른 화면에 있으면 지도 탐색으로 옮겨 간다 */
function showOnMap(course) {
  aiMap.show(course)
  if (route.path !== '/') router.push('/')
}

async function makePlan(m) {
  m.making = true
  try {
    await planFromCourse(m.course, { auth, draft, router })
  } catch (e) {
    messages.value.push({ role: 'error', text: '플랜을 만들지 못했습니다. ' + e.message })
    scrollDown()
  } finally {
    m.making = false
  }
}
</script>

<template>
  <button v-if="!open" class="chat-fab" aria-label="AI 길잡이 열기" @click="open = true; scrollDown()">
    <i class="bi bi-stars" aria-hidden="true"></i><span class="chat-fab-text">AI 길잡이</span>
  </button>

  <section v-else class="chat-box" :class="{ floating, mini }" :style="boxStyle" aria-label="AI 길잡이">
    <!-- 제목 줄을 잡고 끌면 창이 움직인다. 두 번 누르면 처음 위치와 크기로 돌아간다 -->
    <header class="chat-head" :title="floating ? '끌어서 옮기기, 두 번 눌러 처음 위치로' : null" @pointerdown="dragStart($event, 'move')" @pointermove="dragMove" @pointerup="dragEnd" @pointercancel="dragEnd" @dblclick="resetBox">
      <strong><i class="bi bi-stars me-1" aria-hidden="true"></i>AI 길잡이</strong>
      <div class="d-flex gap-1">
        <button class="head-btn" :aria-label="mini ? '창 펼치기' : '창 접기'" :aria-expanded="!mini" @click="mini = !mini"><i :class="['bi', mini ? 'bi-arrows-angle-expand' : 'bi-dash-lg']" aria-hidden="true"></i></button>
        <button class="head-btn" aria-label="닫기" @click="open = false"><i class="bi bi-x-lg" aria-hidden="true"></i></button>
      </div>
    </header>
    <div v-show="!mini" class="chat-main">
    <div class="chat-modes" role="tablist">
      <button role="tab" :aria-selected="mode === 'trip'" :class="{ active: mode === 'trip' }" @click="mode = 'trip'">여행 도우미</button>
      <button role="tab" :aria-selected="mode === 'doc'" :class="{ active: mode === 'doc' }" @click="mode = 'doc'">문서 Q&amp;A</button>
    </div>

    <div ref="listEl" class="chat-list" aria-live="polite">
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="bubble">
          <span v-if="m.doc !== undefined" :class="['badge mb-1', m.doc ? 'badge-soft' : 'badge-ink']">
            <i :class="['bi me-1', m.doc ? 'bi-file-earmark-text' : 'bi-robot']" aria-hidden="true"></i>{{ m.doc ? '문서 참고' : '일반 응답' }}
          </span>
          <div class="pre-line">{{ m.text }}</div>
          <div v-if="m.notice" class="meta">{{ m.notice }}</div>
          <div v-if="m.sources?.length" class="meta">출처: <span v-for="(s, j) in m.sources" :key="j">{{ s.source }} ({{ Math.round(s.score * 100) }}%){{ j < m.sources.length - 1 ? ', ' : '' }}</span></div>
          <div v-if="m.tools?.length" class="meta"><i class="bi bi-tools me-1" aria-hidden="true"></i>{{ m.tools.join(', 이어서 ') }}</div>
          <!-- 코스: 일차를 유지한 채 플랜으로 바로 만든다 -->
          <div v-if="m.course" class="course">
            <div class="course-head">{{ m.course.title }}, {{ m.course.stops.length }}곳</div>
            <div v-for="d in m.course.days" :key="d" class="course-day">
              <div class="course-day-head"><strong>{{ d }}일차</strong><span v-if="m.course.dayKm?.[d - 1]">이동 약 {{ m.course.dayKm[d - 1] }}km</span></div>
              <ol class="course-stops">
                <li v-for="s in m.course.stops.filter((x) => x.dayNo === d)" :key="s.no">
                  <img v-if="s.image" :src="s.image" alt="" loading="lazy" @error="s.image = null" />
                  <span v-else class="course-noimg" aria-hidden="true"><i class="bi bi-geo-alt"></i></span>
                  <span class="min-w-0">
                    <span class="course-name">{{ s.title }}</span>
                    <span class="course-kind">{{ s.type }}</span>
                    <span v-if="s.summary" class="course-sum">{{ s.summary }}</span>
                  </span>
                </li>
              </ol>
            </div>
            <div class="d-flex gap-2">
              <button class="btn btn-sm mm-btn-quiet flex-shrink-0" @click="showOnMap(m.course)"><i class="bi bi-map me-1" aria-hidden="true"></i>지도에서 보기</button>
              <button class="btn btn-sm flex-grow-1" :class="m.course.planId ? 'mm-btn-quiet' : 'btn-coral'" :disabled="m.making" @click="makePlan(m)">
                <span v-if="m.making" class="spinner-border spinner-border-sm me-1"></span>
                <i v-else :class="['bi me-1', m.course.planId ? 'bi-box-arrow-up-right' : 'bi-signpost-2']" aria-hidden="true"></i>{{ m.course.planId ? '플랜 열기' : '플랜 만들기' }}
              </button>
            </div>
            <div v-if="!auth.isLogin && !m.course.planId" class="meta">플랜 만들기는 로그인 화면을 거쳐 새 플랜으로 이어집니다.</div>
          </div>
          <button v-else-if="m.view" class="btn btn-sm mm-btn-quiet mt-2" @click="showOnMap(m.view)"><i class="bi bi-map me-1" aria-hidden="true"></i>지도에서 보기</button>
          <div v-if="m.places?.length" class="meta">한 곳씩 담으려면 아래에서 고르세요.</div>
          <div v-if="m.places?.length" class="places">
            <button v-for="p in m.places" :key="p.no" class="place-chip" :class="{ on: draft.has(p.no) }" :disabled="draft.has(p.no)" @click="addPlace(p)">
              <i :class="['bi me-1', draft.has(p.no) ? 'bi-check2' : 'bi-plus']" aria-hidden="true"></i>{{ p.title }}
            </button>
          </div>
        </div>
      </div>
      <div v-if="loading" class="msg ai"><div class="bubble"><span class="spinner-grow spinner-grow-sm text-secondary" role="status"></span> 답변을 만들고 있어요…</div></div>
    </div>

    <form class="chat-input" @submit.prevent="send">
      <template v-if="mode === 'doc'">
        <input ref="fileEl" type="file" accept=".txt,.md,.pdf" hidden @change="upload" />
        <button type="button" class="btn btn-light" :disabled="uploading" aria-label="문서 업로드" title="문서 업로드 (txt, md, pdf)" @click="fileEl.click()">
          <span v-if="uploading" class="spinner-border spinner-border-sm"></span>
          <i v-else class="bi bi-paperclip" aria-hidden="true"></i>
        </button>
      </template>
      <input v-model="input" class="form-control" maxlength="1000" :placeholder="mode === 'trip' ? '여행에 대해 물어보세요' : '업로드한 문서에 대해 물어보세요'" aria-label="질문 입력" />
      <button class="btn btn-coral" :disabled="loading || !input.trim()" aria-label="보내기"><i class="bi bi-send-fill" aria-hidden="true"></i></button>
    </form>
    </div>
    <!-- 모서리를 끌어 크기를 바꾼다 -->
    <template v-if="floating && !mini">
      <span class="rz tl" aria-hidden="true" @pointerdown="dragStart($event, 'tl')" @pointermove="dragMove" @pointerup="dragEnd" @pointercancel="dragEnd"></span>
      <span class="rz br" aria-hidden="true" @pointerdown="dragStart($event, 'br')" @pointermove="dragMove" @pointerup="dragEnd" @pointercancel="dragEnd"></span>
    </template>
  </section>
</template>

<style scoped>
.chat-fab { position: fixed; right: 20px; bottom: 20px; height: 52px; padding: 0 20px 0 16px; border-radius: var(--mm-r-pill); border: 0; background: var(--mm-ink); color: #fff; font-size: 1.25rem; display: inline-flex; align-items: center; gap: var(--mm-sp-2); box-shadow: var(--mm-shadow-2); z-index: 1040; transition: background 0.15s; }
.chat-fab-text { font-size: var(--mm-fs-sm); font-weight: 700; }
@media (max-width: 767.98px) { .chat-fab { width: 52px; padding: 0; justify-content: center; } .chat-fab-text { display: none; } }
.place-chip { border: 1px solid var(--mm-line-strong); background: #fff; color: var(--mm-ink); border-radius: var(--mm-r-pill); padding: 4px 12px; font-size: var(--mm-fs-sm); font-weight: 600; text-align: left; }
.place-chip:hover:not(:disabled) { border-color: var(--mm-ink); }
.place-chip.on { background: var(--mm-surface); color: var(--mm-muted); border-color: var(--mm-surface); }
.chat-fab:hover { background: var(--mm-coral-dark); }
.chat-box { position: fixed; right: 20px; bottom: 20px; width: 380px; max-width: calc(100vw - 24px); height: 580px; max-height: calc(100vh - 90px); background: #fff; border-radius: var(--mm-r-lg); box-shadow: var(--mm-shadow-3); display: flex; flex-direction: column; overflow: hidden; z-index: 1040; }
.chat-head { background: var(--mm-ink); color: #fff; padding: 10px 10px 10px 16px; display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; }
/* 넓은 화면: 위치와 크기를 직접 정한다 */
.chat-box.floating { right: auto; bottom: auto; max-width: none; max-height: none; }
.chat-box.floating .chat-head { cursor: grab; touch-action: none; user-select: none; }
.chat-box.floating .chat-head:active { cursor: grabbing; }
.chat-main { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.head-btn { width: 30px; height: 30px; border: 0; border-radius: 50%; background: transparent; color: #fff; display: inline-flex; align-items: center; justify-content: center; }
.head-btn:hover, .head-btn:focus-visible { background: rgba(255, 255, 255, 0.18); outline: 0; }
.rz { position: absolute; width: 18px; height: 18px; z-index: 2; touch-action: none; }
.rz.tl { top: 0; left: 0; cursor: nwse-resize; }
.rz.br { right: 0; bottom: 0; cursor: nwse-resize; }
/* 오른쪽 아래 모서리의 손잡이 표시 */
.rz.br::after { content: ''; position: absolute; right: 4px; bottom: 4px; width: 8px; height: 8px; border-right: 2px solid var(--mm-placeholder); border-bottom: 2px solid var(--mm-placeholder); border-bottom-right-radius: 3px; }
.chat-modes { display: flex; border-bottom: 1px solid var(--mm-line); }
.chat-modes button { flex: 1; border: 0; background: #fff; padding: 9px; font-weight: 600; font-size: var(--mm-fs-sm); color: var(--mm-muted); border-bottom: 2px solid transparent; }
.chat-modes button.active { color: var(--mm-coral-dark); border-bottom-color: var(--mm-coral-dark); }
.chat-list { flex: 1; overflow-y: auto; padding: var(--mm-sp-4); background: var(--mm-surface); display: flex; flex-direction: column; gap: 10px; }
.msg { display: flex; }
.msg.user { justify-content: flex-end; }
.bubble { max-width: 86%; padding: 10px 14px; border-radius: var(--mm-r-md); font-size: var(--mm-fs-sm); line-height: 1.6; background: #fff; border: 0; box-shadow: var(--mm-shadow-1); }
.msg.user .bubble { background: var(--mm-ink); color: #fff; box-shadow: none; border-bottom-right-radius: 4px; }
.msg.ai .bubble { border-bottom-left-radius: 4px; }
.msg.error .bubble { background: var(--mm-danger-bg); border-color: var(--mm-danger-line); color: var(--mm-danger); }
.meta { font-size: var(--mm-fs-xs); color: var(--mm-muted); margin-top: 6px; }
.places { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.course { margin-top: var(--mm-sp-3); padding: var(--mm-sp-3); border-radius: var(--mm-r-sm); background: var(--mm-route-tint); }
.course-head { font-weight: 700; color: var(--mm-ink); margin-bottom: var(--mm-sp-2); }
.course-day { margin-bottom: var(--mm-sp-3); }
.course-day-head { display: flex; justify-content: space-between; align-items: baseline; font-size: var(--mm-fs-xs); color: var(--mm-route); margin-bottom: var(--mm-sp-1); }
.course-day-head strong { font-size: var(--mm-fs-sm); font-weight: 700; }
.course-stops { list-style: none; padding: 0; margin: 0; background: #fff; border-radius: var(--mm-r-sm); }
.course-stops li { display: flex; gap: var(--mm-sp-2); align-items: flex-start; padding: var(--mm-sp-2); }
.course-stops li + li { border-top: 1px solid var(--mm-line); }
.course-stops img, .course-noimg { width: 40px; height: 40px; border-radius: 6px; object-fit: cover; flex-shrink: 0; background: var(--mm-surface); }
.course-noimg { display: inline-flex; align-items: center; justify-content: center; color: var(--mm-placeholder); }
.course-name { display: block; font-weight: 700; color: var(--mm-ink); line-height: 1.3; }
.course-kind { display: block; font-size: var(--mm-fs-xs); color: var(--mm-muted); }
.course-sum { display: block; font-size: var(--mm-fs-xs); color: var(--mm-text); line-height: 1.45; margin-top: 2px; }
.chat-input { display: flex; gap: 6px; padding: 10px; border-top: 1px solid var(--mm-line); }
</style>
