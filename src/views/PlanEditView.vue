<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import http from '@/api/http'
import KakaoMap from '@/components/KakaoMap.vue'
import { useDraftStore } from '@/stores/draft'

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
  if (!plan.title.trim()) return flash('warning', '플랜 제목을 입력해 주세요.')
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
  <div class="mm-split">
    <aside class="mm-panel plan-panel">
      <div v-if="loading" class="mm-empty"><div class="spinner-border text-secondary" role="status"></div></div>
      <div v-else-if="error" class="mm-empty"><i class="bi bi-exclamation-circle"></i>{{ error }}<br /><RouterLink to="/plans" class="btn btn-sm btn-outline-coral mt-2">목록으로</RouterLink></div>
      <template v-else>
        <form class="p-3 border-bottom" novalidate @submit.prevent="save">
          <label class="form-label small fw-semibold mb-1" for="planTitle">플랜 제목</label>
          <input id="planTitle" v-model="plan.title" class="form-control mb-2" maxlength="100" placeholder="예) 너랑 갈 부산 1박 2일" required />
          <div class="row g-2">
            <div class="col-6"><label class="form-label small mb-1" for="sd">시작일</label><input id="sd" v-model="plan.startDate" type="date" class="form-control form-control-sm" /></div>
            <div class="col-6"><label class="form-label small mb-1" for="ed">종료일</label><input id="ed" v-model="plan.endDate" type="date" class="form-control form-control-sm" :min="plan.startDate" /></div>
            <div class="col-6"><label class="form-label small mb-1" for="bg">예산(원)</label><input id="bg" v-model.number="plan.budget" type="number" min="0" step="10000" class="form-control form-control-sm" /></div>
            <div class="col-6 d-flex align-items-end"><div class="form-check form-switch mb-1"><input id="pub" v-model="plan.isPublic" class="form-check-input" type="checkbox" /><label class="form-check-label small" for="pub">공개(메이트 모집 가능)</label></div></div>
            <div class="col-12"><textarea v-model="plan.memo" class="form-control form-control-sm" rows="2" maxlength="1000" placeholder="메모 (준비물, 예약 정보 등)" aria-label="메모"></textarea></div>
          </div>
        </form>

        <div class="px-3 pt-2 d-flex align-items-center gap-1 flex-wrap">
          <button v-for="d in dayCount" :key="d" class="btn btn-sm rounded-pill" :class="day === d ? 'bg-ink' : 'btn-light'" @click="day = d">{{ d }}일차</button>
          <button class="btn btn-sm btn-outline-coral ms-auto" @click="optimize"><i class="bi bi-magic me-1" aria-hidden="true"></i>동선 최적화</button>
        </div>
        <div v-if="!isNew && draft.count" class="px-3 pt-2">
          <button class="btn btn-sm btn-light border w-100" @click="addDraft"><i class="bi bi-bag-plus me-1" aria-hidden="true"></i>지도에서 담은 장소 {{ draft.count }}곳을 {{ day }}일차에 추가</button>
        </div>
        <div v-if="notice" :class="['alert py-2 px-3 mx-3 mt-2 mb-0 small', 'alert-' + notice.type]" role="status">{{ notice.text }}</div>

        <div class="mm-panel-body p-3">
          <div v-if="!dayItems.length" class="mm-empty py-4">
            <i class="bi bi-geo"></i>{{ day }}일차에 담긴 장소가 없습니다.
            <div class="mt-2"><RouterLink to="/" class="btn btn-sm btn-outline-coral">지도에서 장소 담기</RouterLink></div>
            <p v-if="!isNew" class="small mt-2 mb-0">지도에서 담은 장소는 '담은 장소'에서 새 플랜으로 만들 수 있습니다.</p>
          </div>
          <draggable v-else v-model="dayItems" item-key="attractionNo" handle=".drag-handle" :animation="180">
            <template #item="{ element, index }">
              <div class="mm-card p-2 mb-2">
                <div class="d-flex align-items-center gap-2">
                  <i class="bi bi-grip-vertical drag-handle fs-5" title="드래그해서 순서 변경" aria-hidden="true"></i>
                  <span class="mm-num">{{ index + 1 }}</span>
                  <div class="flex-grow-1 min-w-0">
                    <div class="fw-bold text-truncate">{{ element.title }}</div>
                    <div class="small text-secondary text-truncate">{{ element.contentTypeName }} · {{ element.addr1 }}</div>
                  </div>
                  <button class="btn btn-sm btn-light" :aria-label="element.title + ' 삭제'" @click="removeItem(element)"><i class="bi bi-x-lg" aria-hidden="true"></i></button>
                </div>
                <div class="row g-1 mt-1">
                  <div class="col-3">
                    <select v-model.number="element.dayNo" class="form-select form-select-sm" aria-label="일차">
                      <option v-for="d in dayCount" :key="d" :value="d">{{ d }}일차</option>
                    </select>
                  </div>
                  <div class="col-4"><input v-model.number="element.cost" type="number" min="0" step="1000" class="form-control form-control-sm" placeholder="경비(원)" aria-label="경비" /></div>
                  <div class="col-5"><input v-model="element.memo" class="form-control form-control-sm" maxlength="500" placeholder="메모" aria-label="메모" /></div>
                </div>
              </div>
            </template>
          </draggable>

          <div v-if="members.length > 1" class="mm-card p-3 mt-3">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <strong class="small"><i class="bi bi-people me-1" aria-hidden="true"></i>함께하는 메이트 {{ members.length }}명</strong>
              <button class="btn btn-sm btn-outline-coral" @click="findMeeting">중간 집결지 찾기</button>
            </div>
            <span v-for="m in members" :key="m.id" class="badge badge-ink me-1">{{ m.name }}{{ m.role === 'OWNER' ? ' (모집자)' : '' }}</span>
            <ul v-if="meeting?.nearby?.length" class="list-unstyled small mt-2 mb-0">
              <li class="text-secondary">집결지 주변 추천 장소</li>
              <li v-for="n in meeting.nearby" :key="n.no">· {{ n.title }} ({{ n.distance }}km)</li>
            </ul>
          </div>
        </div>

        <div class="p-3 border-top d-flex align-items-center gap-2">
          <div class="small flex-grow-1">
            총 경비 <strong :class="{ 'text-danger': overBudget }">{{ totalCost.toLocaleString() }}원</strong>
            <span v-if="plan.budget" class="text-secondary"> / 예산 {{ Number(plan.budget).toLocaleString() }}원</span>
            <div v-if="overBudget" class="text-danger">예산을 {{ (totalCost - plan.budget).toLocaleString() }}원 초과했습니다.</div>
          </div>
          <button class="btn btn-coral" :disabled="saving" @click="save">
            <span v-if="saving" class="spinner-border spinner-border-sm me-1"></span>{{ isNew ? '플랜 만들기' : '저장' }}
          </button>
        </div>
      </template>
    </aside>
    <div class="mm-map"><KakaoMap :markers="markers" path /></div>
  </div>
</template>

<style scoped>
.plan-panel { width: 440px; }
@media (max-width: 767.98px) { .plan-panel { width: 100%; } }
</style>
