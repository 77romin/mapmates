<script setup>
import { nextTick, ref } from 'vue'
import http, { AI_SERVER } from '@/api/http'
import { useDraftStore } from '@/stores/draft'

const draft = useDraftStore()
const open = ref(false)
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
      const res = await http.post('/ai/chat', { message: text, mode: 'planner' })
      messages.value.push({ role: 'ai', text: (res.answer || '').replace(/\*\*/g, ''), tools: (res.steps || []).map((s) => TOOL_LABEL[s.tool] || s.tool), places: res.places || [] })
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
</script>

<template>
  <button v-if="!open" class="chat-fab" aria-label="AI 길잡이 열기" @click="open = true; scrollDown()">
    <i class="bi bi-stars" aria-hidden="true"></i>
  </button>

  <section v-else class="chat-box" aria-label="AI 길잡이">
    <header class="chat-head">
      <strong><i class="bi bi-stars me-1" aria-hidden="true"></i>AI 길잡이</strong>
      <button class="btn-close btn-close-white" aria-label="닫기" @click="open = false"></button>
    </header>
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
          <div v-if="m.tools?.length" class="meta"><i class="bi bi-tools me-1" aria-hidden="true"></i>{{ m.tools.join(' → ') }}</div>
          <div v-if="m.places?.length" class="places">
            <button v-for="p in m.places" :key="p.no" class="btn btn-sm" :class="draft.has(p.no) ? 'btn-light' : 'btn-outline-coral'" :disabled="draft.has(p.no)" @click="addPlace(p)">
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
  </section>
</template>

<style scoped>
.chat-fab { position: fixed; right: 20px; bottom: 20px; width: 58px; height: 58px; border-radius: 50%; border: 0; background: var(--mm-ink); color: #fff; font-size: 1.5rem; box-shadow: 0 6px 18px rgba(31, 42, 68, 0.35); z-index: 1040; }
.chat-fab:hover { background: var(--mm-coral); }
.chat-box { position: fixed; right: 20px; bottom: 20px; width: 380px; max-width: calc(100vw - 24px); height: 580px; max-height: calc(100vh - 90px); background: #fff; border-radius: 16px; box-shadow: 0 12px 40px rgba(31, 42, 68, 0.28); display: flex; flex-direction: column; overflow: hidden; z-index: 1040; }
.chat-head { background: var(--mm-ink); color: #fff; padding: 12px 16px; display: flex; justify-content: space-between; align-items: center; }
.chat-modes { display: flex; border-bottom: 1px solid var(--mm-line); }
.chat-modes button { flex: 1; border: 0; background: #fff; padding: 9px; font-weight: 600; font-size: 0.9rem; color: var(--mm-muted); border-bottom: 2px solid transparent; }
.chat-modes button.active { color: var(--mm-coral-dark); border-bottom-color: var(--mm-coral); }
.chat-list { flex: 1; overflow-y: auto; padding: 14px; background: var(--mm-bg); display: flex; flex-direction: column; gap: 10px; }
.msg { display: flex; }
.msg.user { justify-content: flex-end; }
.bubble { max-width: 86%; padding: 9px 13px; border-radius: 14px; font-size: 0.92rem; line-height: 1.5; background: #fff; border: 1px solid var(--mm-line); }
.msg.user .bubble { background: var(--mm-coral); color: #fff; border-color: var(--mm-coral); border-bottom-right-radius: 4px; }
.msg.ai .bubble { border-bottom-left-radius: 4px; }
.msg.error .bubble { background: #fdecec; border-color: #f5c2c2; color: #a12828; }
.meta { font-size: 0.76rem; color: var(--mm-muted); margin-top: 6px; }
.places { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.chat-input { display: flex; gap: 6px; padding: 10px; border-top: 1px solid var(--mm-line); }
</style>
