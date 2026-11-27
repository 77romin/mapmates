<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import http from '@/api/http'
import KakaoMap from '@/components/KakaoMap.vue'
import AttractionDetail from '@/components/AttractionDetail.vue'
import { useDraftStore } from '@/stores/draft'
import { typeIcon } from '@/utils/place'

const draft = useDraftStore()
const sidos = ref([])
const guguns = ref([])
const types = ref([])
const filter = reactive({ sidoCode: '', gugunCode: '', contentTypeId: '', keyword: '' })
const items = ref([])
const total = ref(0)
const page = ref(1)
const size = 30
const loading = ref(false)
const error = ref('')
const selected = ref(null) // 상세 보기 중인 관광지 번호
const extraMarkers = ref([]) // 상세의 주변 장소/충전소
// 검색 기준: region(시도·구군) | area(지도에 보이는 영역)
const mode = ref('region')
const view = ref(null) // 지도에 보이는 영역
const moved = ref(false) // 마지막 검색 뒤 사용자가 지도를 옮겼는지
const fitKey = ref(0) // 바뀔 때만 지도가 검색 결과에 맞춰 이동한다
const hovered = ref(null) // 목록에서 가리키고 있는 장소 번호
// 넓은 화면에서는 패널이 지도 위에 뜬다. 패널이 가리는 폭(패널 400 + 여백 16 × 2)
const wide = window.matchMedia('(min-width: 768px)')
const padLeft = ref(wide.matches ? 432 : 0)
const onWide = (e) => (padLeft.value = e.matches ? 432 : 0)
wide.addEventListener('change', onWide)
onBeforeUnmount(() => wide.removeEventListener('change', onWide))

const markers = computed(() => [
  ...items.value.map((a) => ({ id: a.no, latitude: a.latitude, longitude: a.longitude, title: a.title, image: a.firstImage1 || null, icon: typeIcon(a.contentTypeId) })),
  ...extraMarkers.value,
])

/**
 * by: 'region' = 시도·구군 기준으로 찾고 지도를 결과에 맞춘다.
 *     'area'   = 지금 지도에 보이는 영역 안에서 찾고 지도는 그대로 둔다.
 *     생략     = 직전 기준을 유지하되, 사용자가 지도를 옮겼으면 보이는 영역 기준으로 바꾼다.
 */
async function search(p = 1, by) {
  const next = by || (moved.value && view.value ? 'area' : mode.value)
  loading.value = true
  error.value = ''
  try {
    const params = { page: p, size }
    for (const k of ['contentTypeId', 'keyword']) if (filter[k] !== '') params[k] = filter[k]
    if (next === 'area' && view.value) {
      const { minLat, maxLat, minLng, maxLng } = view.value
      Object.assign(params, { minLat, maxLat, minLng, maxLng })
    } else {
      for (const k of ['sidoCode', 'gugunCode']) if (filter[k] !== '') params[k] = filter[k]
    }
    const res = await http.get('/attractions', { params })
    mode.value = next === 'area' && view.value ? 'area' : 'region'
    if (mode.value === 'region') fitKey.value++ // 지역 검색일 때만 지도를 결과에 맞춘다
    moved.value = false
    items.value = res.items
    total.value = res.total
    page.value = p
    selected.value = null
    extraMarkers.value = []
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

watch(() => filter.sidoCode, async (code) => {
  filter.gugunCode = ''
  guguns.value = code === '' ? [] : await http.get('/attractions/guguns', { params: { sidoCode: code } }).catch(() => [])
})

/** 시도·구군을 바꾸면 그 지역으로 이동해 검색한다 */
function changeRegion() {
  search(1, 'region')
}
function onViewChange(v) {
  view.value = v
  if (v.byUser) moved.value = true
}

onMounted(async () => {
  try {
    ;[sidos.value, types.value] = await Promise.all([http.get('/attractions/sidos'), http.get('/attractions/content-types')])
  } catch (e) {
    error.value = e.message
  }
  filter.sidoCode = 1
  search(1, 'region')
})

/** '서울특별시 종로구 북촌로 57' -> '서울 종로구' */
const area = (addr) => (addr || '').split(' ').slice(0, 2).join(' ').replace(/특별자치도|특별자치시|특별시|광역시/, '')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
function open(no) {
  selected.value = no
  extraMarkers.value = []
}
/** 상세에서 새로 찾은 사진을 목록 썸네일에도 반영한다 */
function onLoaded(detail) {
  const item = items.value.find((i) => i.no === detail.no)
  if (item && detail.firstImage1) item.firstImage1 = detail.firstImage1
}
</script>

<template>
  <div class="mm-split mm-split--float">
    <aside class="mm-panel">
      <!-- 상세 -->
      <AttractionDetail v-if="selected" :no="selected" @close="selected = null; extraMarkers = []" @extra="extraMarkers = $event" @loaded="onLoaded" />

      <!-- 검색 + 목록 -->
      <template v-else>
        <form class="p-3 border-bottom" @submit.prevent="search(1)">
          <h1 class="h5 mm-title mb-3">어디로 갈까요?</h1>
          <div class="row g-2">
            <div class="col-6">
              <select v-model="filter.sidoCode" class="form-select form-select-sm" aria-label="시도" @change="changeRegion">
                <option value="">전국</option>
                <option v-for="s in sidos" :key="s.code" :value="s.code">{{ s.name }}</option>
              </select>
            </div>
            <div class="col-6">
              <select v-model="filter.gugunCode" class="form-select form-select-sm" aria-label="구군" :disabled="!guguns.length" @change="changeRegion">
                <option value="">구군 전체</option>
                <option v-for="g in guguns" :key="g.code" :value="g.code">{{ g.name }}</option>
              </select>
            </div>
            <div class="col-12">
              <div class="d-flex flex-wrap gap-1" role="group" aria-label="콘텐츠 유형">
                <button type="button" class="btn btn-sm rounded-pill" :class="filter.contentTypeId === '' ? 'bg-ink' : 'btn-light'" @click="filter.contentTypeId = ''; search(1)">전체</button>
                <button v-for="t in types" :key="t.code" type="button" class="btn btn-sm rounded-pill" :class="filter.contentTypeId === t.code ? 'bg-ink' : 'btn-light'" @click="filter.contentTypeId = t.code; search(1)">{{ t.name }}</button>
              </div>
            </div>
            <div class="col-12">
              <div class="input-group input-group-sm">
                <input v-model.trim="filter.keyword" class="form-control" placeholder="장소명 또는 주소 검색" aria-label="검색어" />
                <button class="btn btn-coral" :disabled="loading"><i class="bi bi-search" aria-hidden="true"></i> 검색</button>
              </div>
            </div>
          </div>
        </form>

        <div class="px-3 py-2 mm-result border-bottom d-flex justify-content-between align-items-center">
          <span><span v-if="mode === 'area'" class="badge badge-ink me-1"><i class="bi bi-bounding-box" aria-hidden="true"></i> 지도에 보이는 영역</span>검색 결과 <strong>{{ total.toLocaleString() }}</strong>곳</span>
          <span v-if="total" class="mm-item-meta">{{ page }} / {{ totalPages }}</span>
        </div>

        <div class="mm-panel-body">
          <div v-if="loading" class="mm-empty"><div class="spinner-border text-secondary" role="status"></div><p class="mt-2 mb-0">불러오는 중…</p></div>
          <div v-else-if="error" class="mm-empty"><i class="bi bi-wifi-off"></i>{{ error }}<br /><button class="btn btn-sm btn-outline-coral mt-2" @click="search(page)">다시 시도</button></div>
          <div v-else-if="!items.length" class="mm-empty"><i class="bi bi-search"></i>조건에 맞는 장소가 없습니다.<br />지역이나 유형을 바꿔 보세요.</div>
          <template v-else>
            <div class="place-grid">
              <div v-for="a in items" :key="a.no" class="place-card" role="button" tabindex="0" @click="open(a.no)" @keyup.enter="open(a.no)" @mouseenter="hovered = a.no" @mouseleave="hovered = null" @focus="hovered = a.no" @blur="hovered = null">
                <div class="place-photo">
                  <img v-if="a.firstImage1" :src="a.firstImage1" alt="" loading="lazy" @error="a.firstImage1 = ''" />
                  <i v-else :class="['bi', typeIcon(a.contentTypeId)]" aria-hidden="true"></i>
                  <button class="mm-add place-add" :class="{ on: draft.has(a.no) }" :aria-label="draft.has(a.no) ? '담기 취소' : '플랜에 담기'" @click.stop="draft.has(a.no) ? draft.remove(a.no) : draft.add(a)">
                    <i :class="['bi', draft.has(a.no) ? 'bi-check-lg' : 'bi-plus-lg']" aria-hidden="true"></i>
                  </button>
                </div>
                <div class="mm-item-title text-truncate">{{ a.title }}</div>
                <div class="mm-item-meta text-truncate">{{ a.contentTypeName }}<template v-if="area(a.addr1)">, {{ area(a.addr1) }}</template></div>
              </div>
            </div>
            <nav class="d-flex justify-content-center gap-2 p-3" aria-label="페이지">
              <button class="btn btn-sm btn-light" :disabled="page <= 1" @click="search(page - 1)">이전</button>
              <button class="btn btn-sm btn-light" :disabled="page >= totalPages" @click="search(page + 1)">다음</button>
            </nav>
          </template>
        </div>
      </template>
    </aside>

    <div class="mm-map">
      <KakaoMap :markers="markers" :selected-id="selected" :fit-key="fitKey" :pad-left="padLeft" :hover-id="hovered" @marker-click="(m) => typeof m.id === 'number' && open(m.id)" @view-change="onViewChange" />
      <button v-if="moved && !selected" class="btn btn-sm bg-ink research" @click="search(1, 'area')">
        <i class="bi bi-arrow-clockwise me-1" aria-hidden="true"></i>이 지역에서 다시 검색
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 사진이 주인공인 2열 카드 */
.place-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--mm-sp-5) var(--mm-sp-3); padding: var(--mm-sp-4); }
.place-card { min-width: 0; cursor: pointer; border-radius: var(--mm-r-md); }
.place-card:focus-visible { outline: 0; box-shadow: 0 0 0 0.2rem rgba(255, 107, 74, 0.3); }
.place-photo { position: relative; aspect-ratio: 1 / 1; border-radius: var(--mm-r-md); overflow: hidden; background: var(--mm-surface); display: flex; align-items: center; justify-content: center; color: var(--mm-placeholder); font-size: 2rem; margin-bottom: var(--mm-sp-2); }
.place-photo img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.3s; }
.place-card:hover .place-photo img { transform: scale(1.04); }
.place-add { position: absolute; top: var(--mm-sp-2); right: var(--mm-sp-2); border-color: transparent; box-shadow: var(--mm-shadow-1); font-size: var(--mm-fs-md); }
@media (prefers-reduced-motion: reduce) { .place-photo img { transition: none; } }
.research { position: absolute; top: 16px; left: 50%; transform: translateX(-50%); z-index: 5; border-radius: var(--mm-r-pill); padding: 8px 18px; font-weight: 600; box-shadow: var(--mm-shadow-2); }
/* 패널이 지도 위에 뜨는 넓은 화면에서는 가려지지 않은 영역의 가운데에 둔다 */
@media (min-width: 768px) { .research { left: calc(50% + 216px); } }
</style>
