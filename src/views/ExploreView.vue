<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
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

const markers = computed(() => [
  ...items.value.map((a) => ({ id: a.no, latitude: a.latitude, longitude: a.longitude, title: a.title, image: a.firstImage1 || null, icon: typeIcon(a.contentTypeId) })),
  ...extraMarkers.value,
])

async function search(p = 1) {
  loading.value = true
  error.value = ''
  try {
    const params = { page: p, size }
    for (const k of ['sidoCode', 'gugunCode', 'contentTypeId', 'keyword']) if (filter[k] !== '') params[k] = filter[k]
    const res = await http.get('/attractions', { params })
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

onMounted(async () => {
  try {
    ;[sidos.value, types.value] = await Promise.all([http.get('/attractions/sidos'), http.get('/attractions/content-types')])
  } catch (e) {
    error.value = e.message
  }
  filter.sidoCode = 1
  search()
})

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
  <div class="mm-split">
    <aside class="mm-panel">
      <!-- 상세 -->
      <AttractionDetail v-if="selected" :no="selected" @close="selected = null; extraMarkers = []" @extra="extraMarkers = $event" @loaded="onLoaded" />

      <!-- 검색 + 목록 -->
      <template v-else>
        <form class="p-3 border-bottom" @submit.prevent="search(1)">
          <h1 class="h6 mm-title mb-2">어디로 갈까요?</h1>
          <div class="row g-2">
            <div class="col-6">
              <select v-model="filter.sidoCode" class="form-select form-select-sm" aria-label="시도">
                <option value="">전국</option>
                <option v-for="s in sidos" :key="s.code" :value="s.code">{{ s.name }}</option>
              </select>
            </div>
            <div class="col-6">
              <select v-model="filter.gugunCode" class="form-select form-select-sm" aria-label="구군" :disabled="!guguns.length">
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

        <div class="px-3 py-2 small text-secondary border-bottom d-flex justify-content-between">
          <span>검색 결과 <strong class="text-coral">{{ total.toLocaleString() }}</strong>곳</span>
          <span v-if="total">{{ page }} / {{ totalPages }}</span>
        </div>

        <div class="mm-panel-body">
          <div v-if="loading" class="mm-empty"><div class="spinner-border text-secondary" role="status"></div><p class="mt-2 mb-0">불러오는 중…</p></div>
          <div v-else-if="error" class="mm-empty"><i class="bi bi-wifi-off"></i>{{ error }}<br /><button class="btn btn-sm btn-outline-coral mt-2" @click="search(page)">다시 시도</button></div>
          <div v-else-if="!items.length" class="mm-empty"><i class="bi bi-search"></i>조건에 맞는 장소가 없습니다.<br />지역이나 유형을 바꿔 보세요.</div>
          <template v-else>
            <div v-for="a in items" :key="a.no" class="mm-item" role="button" tabindex="0" @click="open(a.no)" @keyup.enter="open(a.no)">
              <img v-if="a.firstImage1" :src="a.firstImage1" class="mm-thumb" alt="" loading="lazy" @error="a.firstImage1 = ''" />
              <div v-else class="mm-thumb"><i :class="['bi', typeIcon(a.contentTypeId)]" aria-hidden="true"></i></div>
              <div class="flex-grow-1 min-w-0">
                <span class="badge badge-soft">{{ a.contentTypeName }}</span>
                <div class="fw-bold text-truncate">{{ a.title }}</div>
                <div class="small text-secondary text-truncate">{{ a.addr1 || '주소 정보 없음' }}</div>
              </div>
              <button class="btn btn-sm align-self-center" :class="draft.has(a.no) ? 'btn-coral' : 'btn-outline-coral'" :aria-label="draft.has(a.no) ? '담기 취소' : '플랜에 담기'" @click.stop="draft.has(a.no) ? draft.remove(a.no) : draft.add(a)">
                <i :class="['bi', draft.has(a.no) ? 'bi-check-lg' : 'bi-plus-lg']" aria-hidden="true"></i>
              </button>
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
      <KakaoMap :markers="markers" :selected-id="selected" @marker-click="(m) => typeof m.id === 'number' && open(m.id)" />
    </div>
  </div>
</template>
