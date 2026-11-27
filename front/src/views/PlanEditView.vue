<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import http from '@/api/http'
import KakaoMap from '@/components/KakaoMap.vue'
import { useDraftStore } from '@/stores/draft'
import { usePanelPad } from '@/utils/panelPad'

const route = useRoute()
const router = useRouter()
const draft = useDraftStore()

const isNew = computed(() => !route.params.id)
const plan = reactive({ title: '', startDate: '', endDate: '', budget: 0, memo: '', isPublic: false })
const items = ref([])
const members = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref(null) // { type, text }
const day = ref(1)
const meeting = ref(null)
const padLeft = usePanelPad(440)
const editing = ref(!route.params.id) // 플랜 정보 입력칸을 펼쳤는지. 새 플랜은 펼친 채로 시작한다
const openNo = ref(null) // 일차·경비·메모 입력칸을 펼친 방문지
const fmtDay = (d) => { const [, m, dd] = d.split('-'); return `${Number(m)}월 ${Number(dd)}일` }
const period = computed(() => {
  if (!plan.startDate) return '날짜 미정'
  if (!plan.endDate || plan.endDate === plan.startDate) return `${fmtDay(plan.startDate)}, 당일`
  return `${fmtDay(plan.startDate)} ~ ${fmtDay(plan.endDate)}, ${dayCount.value - 1}박 ${dayCount.value}일`
})
// 이동 수단: line(직선) | car | walk | transit
const MODES = [
  { key: 'line', label: '직선', icon: 'bi-slash-lg' },
  { key: 'car', label: '자동차', icon: 'bi-car-front' },
  { key: 'walk', label: '도보', icon: 'bi-person-walking' },
  { key: 'transit', label: '대중교통', icon: 'bi-bus-front' },
]
const mode = ref('line')
const routeInfo = ref(null)
const routeLoading = ref(false)
const routeError = ref('')

const dayCount = computed(() => {
  if (plan.startDate && plan.endDate) {
    const diff = Math.round((new Date(plan.endDate) - new Date(plan.startDate)) / 86400000) + 1
    if (diff >= 1 && diff <= 30) return diff
  }
  return Math.max(1, ...items.value.map((i) => i.dayNo || 1))
})
const dayItems = computed({
  get: () => items.value.filter((i) => (i.dayNo || 1) === day.value),
  set: (list) => {
    // 드래그로 바뀐 순서를 전체 목록에 반영한다
    items.value = [...items.value.filter((i) => (i.dayNo || 1) !== day.value), ...list]
  },
})
const totalCost = computed(() => items.value.reduce((s, i) => s + (Number(i.cost) || 0), 0))
const overBudget = computed(() => plan.budget > 0 && totalCost.value > plan.budget)
const markers = computed(() => {
  const list = dayItems.value.map((i, idx) => ({ id: i.attractionNo, latitude: i.latitude, longitude: i.longitude, title: i.title, label: idx + 1, kind: 'hot' }))
  if (meeting.value) list.push({ id: 'meet', latitude: meeting.value.latitude, longitude: meeting.value.longitude, title: '중간 집결지', kind: 'meet', label: '집결' })
  return list
})
watch(dayCount, (n) => {
  if (day.value > n) day.value = n
})

const fmtMin = (m) => (m == null ? '' : m >= 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m}분`)

/** 현재 일차의 방문 순서대로 선택한 이동 수단의 경로를 불러온다. */
async function loadRoute() {
  routeInfo.value = null
  routeError.value = ''
  const nos = dayItems.value.map((i) => i.attractionNo)
  if (mode.value === 'line' || nos.length < 2) return
  routeLoading.value = true
  const requested = mode.value + nos.join(',')
  try {
    const res = await http.post('/plans/route', { mode: mode.value, attractionNos: nos })
    // 응답을 기다리는 동안 순서나 수단이 바뀌었으면 버린다
    if (requested === mode.value + dayItems.value.map((i) => i.attractionNo).join(',')) routeInfo.value = res
  } catch (e) {
    routeError.value = e.message
  } finally {
    routeLoading.value = false
  }
}
// 이동 수단, 일차, 방문 순서가 바뀌면 경로를 다시 구한다
watch([mode, () => dayItems.value.map((i) => i.attractionNo).join(',')], loadRoute)

async function load() {
  if (isNew.value) {
    items.value = draft.items.map((i) => ({ ...i }))
    return
  }
  loading.value = true
  try {
    const p = await http.get(`/plans/${route.params.id}`)
    Object.assign(plan, { title: p.title, startDate: p.startDate || '', endDate: p.endDate || '', budget: p.budget || 0, memo: p.memo || '', isPublic: !!p.isPublic })
    items.value = p.items
    members.value = p.members
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

/** 기존 플랜에 지도에서 담아 둔 장소를 추가한다. (이미 있는 장소는 건너뜀) */
function addDraft() {
  const exists = new Set(items.value.map((i) => i.attractionNo))
  const added = draft.items.filter((i) => !exists.has(i.attractionNo)).map((i) => ({ ...i, dayNo: day.value }))
  items.value = [...items.value, ...added]
  draft.clear()
  flash('success', `${added.length}곳을 추가했습니다. 저장을 눌러야 반영됩니다.`)
}
function removeItem(item) {
  items.value = items.value.filter((i) => i !== item)
}
function flash(type, text) {
  notice.value = { type, text }
  setTimeout(() => (notice.value = null), 5000)
}

async function optimize() {
  const list = dayItems.value
  if (list.length < 3) return flash('warning', '방문지가 3곳 이상일 때 동선을 최적화할 수 있습니다.')
  try {
    const res = await http.post('/plans/optimize', { attractionNos: list.map((i) => i.attractionNo) })
    const byNo = Object.fromEntries(list.map((i) => [i.attractionNo, i]))
    dayItems.value = res.order.map((no) => byNo[no])
    const saved = Math.round((res.beforeKm - res.afterKm) * 10) / 10
    flash('success', saved > 0 ? `동선을 최적화했습니다. ${res.beforeKm}km → ${res.afterKm}km (${saved}km 단축)` : `이미 가장 짧은 동선입니다. (총 ${res.afterKm}km)`)
  } catch (e) {
    flash('danger', e.message)
  }
}

async function save() {
  if (!plan.title.trim()) {
    editing.value = true // 접혀 있으면 입력칸을 펼쳐 보여 준다
    return flash('warning', '플랜 제목을 입력해 주세요.')
  }
  if (plan.startDate && plan.endDate && plan.endDate < plan.startDate) return flash('warning', '종료일이 시작일보다 빠릅니다.')
  saving.value = true
  try {
    const ordered = []
    for (let d = 1; d <= dayCount.value; d++) ordered.push(...items.value.filter((i) => (i.dayNo || 1) === d))
    const body = {
      ...plan, startDate: plan.startDate || null, endDate: plan.endDate || null, budget: Number(plan.budget) || 0,
      items: ordered.map((i) => ({ attractionNo: i.attractionNo, dayNo: i.dayNo || 1, memo: i.memo, cost: Number(i.cost) || 0 })),
    }
    if (isNew.value) {
      const created = await http.post('/plans', body)
      draft.clear()
      router.replace(`/plans/${created.id}`)
    } else {
      await http.put(`/plans/${route.params.id}`, body)
      flash('success', '저장했습니다.')
    }
  } catch (e) {
    flash('danger', e.message)
  } finally {
    saving.value = false
  }
}

async function findMeeting() {
  try {
    meeting.value = await http.get(`/plans/${route.params.id}/meeting-point`)
    flash('success', `메이트 ${meeting.value.memberCount}명의 출발지 기준 중간 집결지를 지도에 표시했습니다.`)
  } catch (e) {
    flash('warning', e.message)
  }
}
</script>

<template>
  <div class="mm-split mm-split--float">
    <aside class="mm-panel plan-panel">
      <div v-if="loading" class="p-3" role="status" aria-busy="true" aria-label="플랜을 불러오는 중">
        <div class="mm-skel mm-skel-line w-50"></div>
        <div class="mm-skel mm-skel-line w-75"></div>
        <div v-for="n in 4" :key="n" class="mm-skel plan-skel"></div>
      </div>
      <div v-else-if="error" class="mm-empty"><i class="bi bi-exclamation-circle"></i>{{ error }}<br /><RouterLink to="/plans" class="btn btn-sm btn-outline-coral mt-2">목록으로</RouterLink></div>
      <template v-else>
        <!-- 플랜 정보: 평소엔 요약만, 누르면 입력칸이 펼쳐진다 -->
        <div class="plan-head">
          <div class="min-w-0 flex-grow-1">
            <h1 class="plan-title text-truncate">{{ plan.title || '새 플랜' }}</h1>
            <div class="mm-item-meta">{{ period }}<template v-if="plan.budget">, 예산 {{ Number(plan.budget).toLocaleString() }}원</template></div>
          </div>
          <span v-if="plan.isPublic" class="badge badge-soft">공개</span>
          <button class="mm-add" :aria-expanded="editing" aria-controls="planForm" :aria-label="editing ? '플랜 정보 접기' : '플랜 정보 수정'" @click="editing = !editing">
            <i :class="['bi', editing ? 'bi-chevron-up' : 'bi-pencil']" aria-hidden="true"></i>
          </button>
        </div>
        <form v-show="editing" id="planForm" class="px-3 pb-3" novalidate @submit.prevent="save">
          <label class="form-label small fw-semibold mb-1" for="planTitle">플랜 제목</label>
          <input id="planTitle" v-model="plan.title" class="form-control mb-2" maxlength="100" placeholder="예) 너랑 갈 부산 1박 2일" required />
          <div class="row g-2">
            <div class="col-6"><label class="form-label small fw-semibold mb-1" for="sd">시작일</label><input id="sd" v-model="plan.startDate" type="date" class="form-control" /></div>
            <div class="col-6"><label class="form-label small fw-semibold mb-1" for="ed">종료일</label><input id="ed" v-model="plan.endDate" type="date" class="form-control" :min="plan.startDate" /></div>
            <div class="col-6"><label class="form-label small fw-semibold mb-1" for="bg">예산(원)</label><input id="bg" v-model.number="plan.budget" type="number" min="0" step="10000" class="form-control" /></div>
            <div class="col-6 d-flex align-items-end"><div class="form-check form-switch mb-2"><input id="pub" v-model="plan.isPublic" class="form-check-input" type="checkbox" /><label class="form-check-label small" for="pub">공개(메이트 모집 가능)</label></div></div>
            <div class="col-12"><textarea v-model="plan.memo" class="form-control" rows="2" maxlength="1000" placeholder="메모 (준비물, 예약 정보 등)" aria-label="메모"></textarea></div>
          </div>
        </form>

        <div class="plan-tools">
          <div class="d-flex align-items-center gap-1 flex-wrap">
            <button v-for="d in dayCount" :key="d" class="btn btn-sm rounded-pill" :class="day === d ? 'bg-ink' : 'btn-light'" @click="day = d">{{ d }}일차</button>
            <button class="btn btn-sm mm-btn-quiet ms-auto" @click="optimize"><i class="bi bi-magic me-1" aria-hidden="true"></i>동선 최적화</button>
          </div>
          <div class="seg mt-2" role="group" aria-label="이동 수단">
            <button v-for="m in MODES" :key="m.key" type="button" :class="{ on: mode === m.key }" :aria-pressed="mode === m.key" @click="mode = m.key">
              <i :class="['bi', m.icon, 'me-1']" aria-hidden="true"></i>{{ m.label }}
            </button>
          </div>
          <div v-if="mode !== 'line'" class="route-sum" aria-live="polite">
            <span v-if="dayItems.length < 2" class="text-secondary">방문지가 2곳 이상이면 경로가 표시됩니다.</span>
            <span v-else-if="routeLoading" class="text-secondary"><span class="spinner-border spinner-border-sm me-1"></span>경로를 찾는 중…</span>
            <span v-else-if="routeError" class="text-danger">{{ routeError }} <button class="btn btn-link btn-sm p-0 align-baseline" @click="loadRoute">다시 시도</button></span>
            <template v-else-if="routeInfo">
              <strong>총 {{ routeInfo.distanceKm }}km<span v-if="routeInfo.durationMin">, 약 {{ fmtMin(routeInfo.durationMin) }}</span></strong>
              <span v-if="routeInfo.message" class="text-secondary d-block">{{ routeInfo.message }}</span>
            </template>
          </div>
          <button v-if="!isNew && draft.count" class="btn btn-sm mm-btn-quiet w-100 mt-2" @click="addDraft"><i class="bi bi-bag-plus me-1" aria-hidden="true"></i>지도에서 담은 장소 {{ draft.count }}곳을 {{ day }}일차에 추가</button>
          <div v-if="notice" :class="['alert py-2 px-3 mt-2 mb-0 small', 'alert-' + notice.type]" role="status">{{ notice.text }}</div>
        </div>

        <div class="mm-panel-body px-3 pb-3">
          <div v-if="!dayItems.length" class="mm-empty py-4">
            <i class="bi bi-geo"></i>{{ day }}일차에 담긴 장소가 없습니다.
            <div class="mt-2"><RouterLink to="/" class="btn btn-sm btn-outline-coral">지도에서 장소 담기</RouterLink></div>
            <p v-if="!isNew" class="small mt-2 mb-0">지도에서 담은 장소는 '담은 장소'에서 새 플랜으로 만들 수 있습니다.</p>
          </div>
          <!-- 방문지: 번호와 선으로 이어진 일정표 -->
          <draggable v-else v-model="dayItems" item-key="attractionNo" handle=".drag-handle" :animation="180" class="stops">
            <template #item="{ element, index }">
              <div class="stop">
                <div v-if="index > 0 && routeInfo?.legs?.[index - 1]" class="leg">
                  {{ routeInfo.legs[index - 1].distanceKm }}km<span v-if="routeInfo.legs[index - 1].durationMin">, {{ fmtMin(routeInfo.legs[index - 1].durationMin) }}</span>
                  <a v-if="routeInfo.legs[index - 1].link" :href="routeInfo.legs[index - 1].link" target="_blank" rel="noopener noreferrer" class="ms-1">카카오맵 길찾기 <i class="bi bi-box-arrow-up-right" aria-hidden="true"></i></a>
                </div>
                <div class="stop-row">
                  <span class="mm-num stop-num">{{ index + 1 }}</span>
                  <img v-if="element.firstImage1" :src="element.firstImage1" class="stop-thumb" alt="" loading="lazy" @error="element.firstImage1 = ''" />
                  <div class="flex-grow-1 min-w-0">
                    <div class="mm-item-title text-truncate">{{ element.title }}</div>
                    <div class="mm-item-meta text-truncate">{{ element.contentTypeName }}<template v-if="Number(element.cost)">, {{ Number(element.cost).toLocaleString() }}원</template></div>
                    <div v-if="element.memo && openNo !== element.attractionNo" class="stop-memo text-truncate"><i class="bi bi-sticky me-1" aria-hidden="true"></i>{{ element.memo }}</div>
                  </div>
                  <button class="stop-btn" :aria-expanded="openNo === element.attractionNo" :aria-label="element.title + ' 일차·경비·메모 수정'" @click="openNo = openNo === element.attractionNo ? null : element.attractionNo"><i :class="['bi', openNo === element.attractionNo ? 'bi-chevron-up' : 'bi-pencil']" aria-hidden="true"></i></button>
                  <button class="stop-btn" :aria-label="element.title + ' 삭제'" @click="removeItem(element)"><i class="bi bi-x-lg" aria-hidden="true"></i></button>
                  <i class="bi bi-grip-vertical drag-handle fs-5" title="드래그해서 순서 변경" aria-hidden="true"></i>
                </div>
                <div v-if="openNo === element.attractionNo" class="stop-edit row g-2">
                  <div class="col-4">
                    <label class="form-label small mb-1" :for="'day' + element.attractionNo">일차</label>
                    <select :id="'day' + element.attractionNo" v-model.number="element.dayNo" class="form-select form-select-sm"><option v-for="d in dayCount" :key="d" :value="d">{{ d }}일차</option></select>
                  </div>
                  <div class="col-8">
                    <label class="form-label small mb-1" :for="'cost' + element.attractionNo">경비(원)</label>
                    <input :id="'cost' + element.attractionNo" v-model.number="element.cost" type="number" min="0" step="1000" class="form-control form-control-sm" />
                  </div>
                  <div class="col-12">
                    <label class="form-label small mb-1" :for="'memo' + element.attractionNo">메모</label>
                    <input :id="'memo' + element.attractionNo" v-model="element.memo" class="form-control form-control-sm" maxlength="500" />
                  </div>
                </div>
              </div>
            </template>
          </draggable>

          <div v-if="members.length > 1" class="mm-card p-3 mt-3">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <strong class="small"><i class="bi bi-people me-1" aria-hidden="true"></i>함께하는 메이트 {{ members.length }}명</strong>
              <button class="btn btn-sm mm-btn-quiet" @click="findMeeting">중간 집결지 찾기</button>
            </div>
            <span v-for="m in members" :key="m.id" class="badge badge-ink me-1">{{ m.name }}{{ m.role === 'OWNER' ? ' (모집자)' : '' }}</span>
            <ul v-if="meeting?.nearby?.length" class="list-unstyled small mt-2 mb-0">
              <li class="text-secondary">집결지 주변 추천 장소</li>
              <li v-for="n in meeting.nearby" :key="n.no">{{ n.title }} ({{ n.distance }}km)</li>
            </ul>
          </div>
        </div>

        <div class="plan-foot">
          <div class="flex-grow-1 min-w-0">
            <div class="mm-item-meta">총 경비<template v-if="plan.budget"> / 예산 {{ Number(plan.budget).toLocaleString() }}원</template></div>
            <strong class="plan-cost" :class="{ 'text-danger': overBudget }">{{ totalCost.toLocaleString() }}원</strong>
            <div v-if="overBudget" class="small text-danger">예산을 {{ (totalCost - plan.budget).toLocaleString() }}원 넘었습니다.</div>
          </div>
          <button class="btn btn-coral px-4" :disabled="saving" @click="save">
            <span v-if="saving" class="spinner-border spinner-border-sm me-1"></span>{{ isNew ? '플랜 만들기' : '저장' }}
          </button>
        </div>
      </template>
    </aside>
    <div class="mm-map"><KakaoMap :markers="markers" path :route-path="routeInfo?.path" :dashed="mode === 'transit'" :pad-left="padLeft" /></div>
  </div>
</template>

<style scoped>
.plan-panel { width: 440px; }
@media (min-width: 768px) { .mm-split--float .plan-panel { width: 440px; } }
@media (max-width: 767.98px) { .plan-panel { width: 100%; } }

.plan-skel { height: 64px; border-radius: var(--mm-r-md); margin-top: var(--mm-sp-4); }
.plan-head { display: flex; align-items: center; gap: var(--mm-sp-2); padding: var(--mm-sp-4) var(--mm-sp-4) var(--mm-sp-3); }
.plan-title { font-size: var(--mm-fs-lg); font-weight: 700; letter-spacing: -0.02em; color: var(--mm-ink); margin: 0 0 2px; }
.plan-tools { padding: var(--mm-sp-3) var(--mm-sp-4); border-top: 1px solid var(--mm-line); border-bottom: 1px solid var(--mm-line); }
.route-sum { font-size: var(--mm-fs-sm); margin-top: var(--mm-sp-2); color: var(--mm-route); }

/* 이동 수단: 한 줄 분할 선택 */
.seg { display: flex; background: var(--mm-surface); border-radius: var(--mm-r-sm); padding: 3px; }
.seg button { flex: 1; border: 0; background: transparent; border-radius: 6px; padding: 6px 4px; font-size: var(--mm-fs-sm); font-weight: 600; color: var(--mm-muted); white-space: nowrap; }
.seg button.on { background: #fff; color: var(--mm-ink); box-shadow: var(--mm-shadow-1); }

/* 일정표: 번호를 세로 선으로 잇는다 */
.stops { padding-top: var(--mm-sp-4); }
.stop { position: relative; padding-bottom: var(--mm-sp-4); }
.stop::before { content: ''; position: absolute; left: 12px; top: 0; bottom: 0; width: 2px; background: var(--mm-route-tint); }
.stop:first-child::before { top: 24px; }
.stop:last-child::before { bottom: auto; height: 24px; }
.stop:only-child::before { display: none; }
.stop-row { position: relative; display: flex; align-items: center; gap: var(--mm-sp-3); }
.stop-num { position: relative; z-index: 1; box-shadow: 0 0 0 3px #fff; }
.stop-thumb { width: 48px; height: 48px; border-radius: var(--mm-r-sm); object-fit: cover; flex-shrink: 0; background: var(--mm-surface); }
.stop-memo { font-size: var(--mm-fs-sm); color: var(--mm-text); margin-top: 2px; }
.stop-btn { width: 32px; height: 32px; flex-shrink: 0; border: 0; border-radius: 50%; background: transparent; color: var(--mm-muted); display: inline-flex; align-items: center; justify-content: center; }
.stop-btn:hover { background: var(--mm-surface); color: var(--mm-ink); }
.stop-edit { margin: var(--mm-sp-2) 0 0 38px; padding: var(--mm-sp-3); background: var(--mm-surface); border-radius: var(--mm-r-sm); }
.leg { position: relative; z-index: 1; display: inline-block; margin: 0 0 var(--mm-sp-4) 30px; padding: 2px 10px; border-radius: var(--mm-r-pill); background: var(--mm-route-tint); color: var(--mm-route); font-size: var(--mm-fs-xs); font-weight: 600; }
.leg a { color: inherit; }

.plan-foot { display: flex; align-items: center; gap: var(--mm-sp-3); padding: var(--mm-sp-3) var(--mm-sp-4); border-top: 1px solid var(--mm-line); }
.plan-cost { font-size: var(--mm-fs-lg); color: var(--mm-ink); letter-spacing: -0.01em; }
</style>
