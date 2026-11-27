<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import http from '@/api/http'
import KakaoMap from '@/components/KakaoMap.vue'
import AttractionPopup from '@/components/AttractionPopup.vue'
import { useDraftStore } from '@/stores/draft'

const draft = useDraftStore()
const tab = ref('performance') // performance: KOPIS 공연, seoul: 서울시 문화행사
const sidos = ref([])
const filter = reactive({ sidoCode: 1, keyword: '' })
const items = ref([])
const page = ref(1)
const hasNext = ref(false)
const loading = ref(false)
const error = ref('')
const detail = ref(null) // 선택한 공연/행사
const detailLoading = ref(false)
const nearby = ref([])
const popup = ref(null) // 주변 장소 팝업: { no, distance }

async function load(p = 1) {
  loading.value = true
  error.value = ''
  try {
    const params = { page: p, size: 12, keyword: filter.keyword || undefined }
    let res
    if (tab.value === 'performance') {
      if (filter.sidoCode !== '') params.sidoCode = filter.sidoCode
      res = await http.get('/culture/performances', { params })
    } else {
      res = await http.get('/culture/seoul-events', { params })
    }
    items.value = res.items
    hasNext.value = res.hasNext
    page.value = p
  } catch (e) {
    items.value = []
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  sidos.value = await http.get('/attractions/sidos').catch(() => [])
  load()
})
watch(tab, () => {
  filter.keyword = ''
  load(1)
})

async function loadNearby(place) {
  nearby.value = []
  if (!place?.latitude || !place?.longitude) return
  nearby.value = await http.get('/culture/nearby', { params: { latitude: place.latitude, longitude: place.longitude } }).catch(() => [])
}

async function open(item) {
  nearby.value = []
  if (tab.value === 'seoul') {
    detail.value = { ...item, kind: 'seoul' }
    return loadNearby(item)
  }
  detail.value = { ...item, kind: 'performance' }
  detailLoading.value = true
  try {
    detail.value = { ...(await http.get(`/culture/performances/${item.id}`)), kind: 'performance' }
    loadNearby(detail.value.facility)
  } catch (e) {
    detail.value.error = e.message
  } finally {
    detailLoading.value = false
  }
}

const place = () => (detail.value?.kind === 'seoul' ? detail.value : detail.value?.facility)
const markers = () => {
  const p = place()
  if (!p?.latitude) return []
  return [
    { id: 'venue', latitude: p.latitude, longitude: p.longitude, title: p.name || p.place || '행사장', kind: 'hot', label: '공연' },
    ...nearby.value.map((n) => ({ id: n.no, latitude: n.latitude, longitude: n.longitude, title: n.title })),
  ]
}
const safe = (url) => (/^https?:\/\//.test(url || '') ? url : null)
const STATE_CLASS = { 공연중: 'badge-soft', 공연예정: 'badge-ink' }
</script>

<template>
  <div class="mm-page">
    <h1 class="h3 mm-title mb-1">공연 · 행사</h1>
    <p class="text-secondary">여행지에서 볼 수 있는 공연과 문화행사를 찾고, 주변 장소를 플랜에 함께 담아 보세요.</p>

    <ul class="nav nav-pills mb-3" role="tablist">
      <li class="nav-item"><button class="nav-link" :class="{ active: tab === 'performance' }" @click="tab = 'performance'">전국 공연 <span class="small opacity-75">KOPIS</span></button></li>
      <li class="nav-item"><button class="nav-link" :class="{ active: tab === 'seoul' }" @click="tab = 'seoul'">서울시 문화행사</button></li>
    </ul>

    <form class="row g-2 mb-3" @submit.prevent="load(1)">
      <div v-if="tab === 'performance'" class="col-5 col-md-3">
        <select v-model="filter.sidoCode" class="form-select" aria-label="지역" @change="load(1)">
          <option value="">전국</option>
          <option v-for="s in sidos" :key="s.code" :value="s.code">{{ s.name }}</option>
        </select>
      </div>
      <div class="col">
        <div class="input-group">
          <input v-model.trim="filter.keyword" class="form-control" :placeholder="tab === 'performance' ? '공연명 검색' : '행사명 검색'" aria-label="검색어" />
          <button class="btn btn-coral" :disabled="loading"><i class="bi bi-search" aria-hidden="true"></i> 검색</button>
        </div>
      </div>
    </form>

    <div v-if="loading" class="mm-empty"><div class="spinner-border text-secondary" role="status"></div></div>
    <div v-else-if="error" class="mm-card mm-empty"><i class="bi bi-exclamation-circle"></i>{{ error }}<br /><button class="btn btn-sm btn-outline-coral mt-2" @click="load(page)">다시 시도</button></div>
    <div v-else-if="!items.length" class="mm-card mm-empty"><i class="bi bi-ticket-perforated"></i>조건에 맞는 {{ tab === 'performance' ? '공연' : '행사' }}이 없습니다.<br />지역이나 검색어를 바꿔 보세요.</div>
    <template v-else>
      <div class="row g-3">
        <div v-for="(it, i) in items" :key="it.id || i" class="col-6 col-md-4 col-lg-3">
          <button class="mm-card culture-card w-100 h-100 text-start p-0" @click="open(it)">
            <img v-if="it.poster || it.image" :src="it.poster || it.image" class="poster" alt="" loading="lazy" @error="it.poster = it.image = ''" />
            <div v-else class="poster d-flex align-items-center justify-content-center"><i class="bi bi-ticket-perforated fs-1" aria-hidden="true"></i></div>
            <div class="p-2">
              <span class="badge" :class="STATE_CLASS[it.state] || 'badge-ink'">{{ it.state || it.category }}</span>
              <span v-if="it.genre" class="badge badge-ink ms-1">{{ it.genre }}</span>
              <span v-if="it.isFree" class="badge badge-soft ms-1">{{ it.isFree }}</span>
              <div class="fw-bold mt-1 title">{{ it.title }}</div>
              <div class="small text-secondary text-truncate">{{ it.venue || it.place }}</div>
              <div class="small text-secondary">{{ it.startDate ? `${it.startDate} ~ ${it.endDate}` : it.period }}</div>
            </div>
          </button>
        </div>
      </div>
      <nav class="d-flex justify-content-center align-items-center gap-2 mt-4" aria-label="페이지">
        <button class="btn btn-sm btn-light border" :disabled="page <= 1" @click="load(page - 1)">이전</button>
        <span class="small">{{ page }}</span>
        <button class="btn btn-sm btn-light border" :disabled="!hasNext" @click="load(page + 1)">다음</button>
      </nav>
    </template>

    <!-- 상세 -->
    <div v-if="detail" class="mm-modal" role="dialog" aria-modal="true" aria-label="상세 정보" @click.self="detail = null; popup = null">
      <div class="mm-modal-body wide">
        <div class="d-flex justify-content-between align-items-start gap-2">
          <h2 class="h5 mm-title">{{ detail.title }}</h2>
          <button class="btn-close flex-shrink-0" aria-label="닫기" @click="detail = null"></button>
        </div>
        <div v-if="detailLoading" class="text-center py-4"><div class="spinner-border text-secondary" role="status"></div></div>
        <div v-else-if="detail.error" class="alert alert-danger">{{ detail.error }}</div>
        <div v-else class="row g-3">
          <div class="col-md-4">
            <img v-if="detail.poster || detail.image" :src="detail.poster || detail.image" class="w-100 rounded" :alt="detail.title + ' 포스터'" />
          </div>
          <div class="col-md-8">
            <dl class="row small mb-2">
              <template v-if="detail.kind === 'performance'">
                <dt class="col-3">기간</dt><dd class="col-9">{{ detail.startDate }} ~ {{ detail.endDate }}</dd>
                <dt class="col-3">공연장</dt><dd class="col-9">{{ detail.venue }}</dd>
                <dt v-if="detail.schedule" class="col-3">시간</dt><dd v-if="detail.schedule" class="col-9">{{ detail.schedule }}</dd>
                <dt v-if="detail.runtime" class="col-3">러닝타임</dt><dd v-if="detail.runtime" class="col-9">{{ detail.runtime }}</dd>
                <dt v-if="detail.age" class="col-3">관람 연령</dt><dd v-if="detail.age" class="col-9">{{ detail.age }}</dd>
                <dt v-if="detail.price" class="col-3">가격</dt><dd v-if="detail.price" class="col-9">{{ detail.price }}</dd>
                <dt v-if="detail.cast" class="col-3">출연</dt><dd v-if="detail.cast" class="col-9">{{ detail.cast }}</dd>
                <template v-if="detail.facility?.name">
                  <dt class="col-3">시설</dt>
                  <dd class="col-9">{{ detail.facility.address }}<span v-if="detail.facility.seats"> · {{ Number(detail.facility.seats).toLocaleString() }}석</span><span v-if="detail.facility.tel"> · {{ detail.facility.tel }}</span>
                    <a v-if="safe(detail.facility.homepage)" :href="safe(detail.facility.homepage)" target="_blank" rel="noopener noreferrer" class="ms-1">홈페이지</a></dd>
                </template>
              </template>
              <template v-else>
                <dt class="col-3">기간</dt><dd class="col-9">{{ detail.period }}</dd>
                <dt class="col-3">장소</dt><dd class="col-9">{{ detail.district }} {{ detail.place }}</dd>
                <dt class="col-3">대상</dt><dd class="col-9">{{ detail.target || '-' }}</dd>
                <dt class="col-3">요금</dt><dd class="col-9">{{ detail.fee || detail.isFree || '-' }}</dd>
                <dt v-if="safe(detail.homepage)" class="col-3">링크</dt><dd v-if="safe(detail.homepage)" class="col-9"><a :href="safe(detail.homepage)" target="_blank" rel="noopener noreferrer">상세 페이지 열기</a></dd>
              </template>
            </dl>
            <div v-if="place()?.latitude" class="venue-map mb-2"><KakaoMap :markers="markers()" selected-id="venue" @marker-click="(m) => typeof m.id === 'number' && (popup = { no: m.id, distance: nearby.find((n) => n.no === m.id)?.distance ?? null })" /></div>
            <template v-if="nearby.length">
              <div class="small fw-bold mb-1">주변 2km · 함께 가기 좋은 곳 <span class="fw-normal text-secondary">— 눌러서 정보를 보고 담아 보세요</span></div>
              <ul class="list-unstyled small mb-0">
                <li v-for="n in nearby.slice(0, 5)" :key="n.no" class="border-top">
                  <button class="nearby-item" :aria-label="n.title + ' 정보 보기'" @click="popup = { no: n.no, distance: n.distance }">
                    <img v-if="n.firstImage1" :src="n.firstImage1" class="nearby-thumb" alt="" loading="lazy" @error="n.firstImage1 = ''" />
                    <span v-else class="nearby-thumb d-flex align-items-center justify-content-center"><i class="bi bi-image" aria-hidden="true"></i></span>
                    <span class="flex-grow-1 min-w-0">
                      <span class="d-block fw-semibold text-truncate">{{ n.title }}</span>
                      <span class="d-block text-secondary text-truncate">{{ n.contentTypeName }} · {{ n.distance }}km</span>
                    </span>
                    <span v-if="draft.has(n.no)" class="badge badge-soft flex-shrink-0"><i class="bi bi-check-lg" aria-hidden="true"></i> 담김</span>
                    <i class="bi bi-chevron-right text-secondary flex-shrink-0" aria-hidden="true"></i>
                  </button>
                </li>
              </ul>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 주변 장소 정보 팝업 -->
    <AttractionPopup v-if="popup" :no="popup.no" :distance="popup.distance" @close="popup = null" />
  </div>
</template>

<style scoped>
.culture-card { overflow: hidden; transition: transform 0.15s, box-shadow 0.15s; }
.culture-card:hover { transform: translateY(-2px); box-shadow: 0 6px 18px rgba(31, 42, 68, 0.1); }
.poster { width: 100%; aspect-ratio: 3 / 4; object-fit: cover; background: #f1ebe2; color: #c9bba7; }
.title { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 3em; }
.wide { max-width: 760px; }
.nearby-item { display: flex; align-items: center; gap: 10px; width: 100%; padding: 6px 4px; border: 0; background: transparent; text-align: left; border-radius: 8px; color: inherit; }
.nearby-item:hover, .nearby-item:focus-visible { background: #fff4ee; }
.nearby-thumb { width: 44px; height: 44px; border-radius: 8px; object-fit: cover; background: #f1ebe2; color: #c9bba7; flex-shrink: 0; }
.venue-map { position: relative; height: 200px; border-radius: 12px; overflow: hidden; border: 1px solid var(--mm-line); }
</style>
