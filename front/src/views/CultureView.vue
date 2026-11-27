<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import http from '@/api/http'
import KakaoMap from '@/components/KakaoMap.vue'
import AttractionPopup from '@/components/AttractionPopup.vue'
import { useDraftStore } from '@/stores/draft'
import { typeIcon } from '@/utils/place'

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

/** 팝업에서 새로 찾은 사진을 주변 장소 목록 썸네일에도 반영한다 */
function onPlaceLoaded(detail) {
  const item = nearby.value.find((n) => n.no === detail.no)
  if (item && detail.firstImage1) item.firstImage1 = detail.firstImage1
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

    <div v-if="loading" class="row g-4" role="status" aria-busy="true" aria-label="공연을 불러오는 중">
      <div v-for="n in 8" :key="n" class="col-6 col-md-4 col-lg-3" aria-hidden="true"><div class="poster mm-skel"></div><div class="mm-skel mm-skel-line w-75"></div><div class="mm-skel mm-skel-line w-50"></div></div>
    </div>
    <div v-else-if="error" class="mm-card mm-empty"><i class="bi bi-exclamation-circle"></i>{{ error }}<br /><button class="btn btn-sm btn-outline-coral mt-2" @click="load(page)">다시 시도</button></div>
    <div v-else-if="!items.length" class="mm-card mm-empty"><i class="bi bi-ticket-perforated"></i>조건에 맞는 {{ tab === 'performance' ? '공연' : '행사' }}이 없습니다.<br />지역이나 검색어를 바꿔 보세요.</div>
    <template v-else>
      <div class="row g-4">
        <div v-for="(it, i) in items" :key="it.id || i" class="col-6 col-md-4 col-lg-3">
          <!-- 포스터가 주인공: 테두리 없는 카드, 상태는 포스터 위 알약 -->
          <button v-reveal class="culture-card w-100 text-start" @click="open(it)">
            <div class="poster-wrap">
              <img v-if="it.poster || it.image" :src="it.poster || it.image" class="poster" alt="" loading="lazy" @error="it.poster = it.image = ''" />
              <div v-else class="poster d-flex align-items-center justify-content-center"><i class="bi bi-ticket-perforated fs-1" aria-hidden="true"></i></div>
              <span class="poster-chip" :class="{ now: it.state === '공연중' }">{{ it.state || it.category }}</span>
              <span v-if="it.isFree === '무료'" class="poster-chip free">무료</span>
            </div>
            <div class="mm-item-title title">{{ it.title }}</div>
            <div class="mm-item-kind text-truncate">{{ it.venue || it.place }}</div>
            <div class="mm-item-meta">{{ it.genre ? it.genre + ', ' : '' }}{{ it.startDate ? `${it.startDate} ~ ${it.endDate}` : it.period }}</div>
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
        <div class="d-flex justify-content-between align-items-start gap-3 mb-3">
          <h2 class="cd-title">{{ detail.title }}</h2>
          <button class="mm-add" aria-label="닫기" @click="detail = null"><i class="bi bi-x-lg" aria-hidden="true"></i></button>
        </div>
        <div v-if="detailLoading" class="row g-3" role="status" aria-busy="true" aria-label="상세 정보를 불러오는 중"><div class="col-md-4"><div class="poster mm-skel"></div></div><div class="col-md-8"><div v-for="n in 6" :key="n" class="mm-skel mm-skel-line"></div></div></div>
        <div v-else-if="detail.error" class="alert alert-danger">{{ detail.error }}</div>
        <div v-else class="row g-3">
          <div class="col-md-4">
            <img v-if="detail.poster || detail.image" :src="detail.poster || detail.image" class="w-100 cd-poster" :alt="detail.title + ' 포스터'" />
          </div>
          <div class="col-md-8">
            <dl class="row cd-facts mb-3">
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
              <h3 class="cd-h">주변 2km 안, 함께 가기 좋은 곳</h3>
              <ul class="list-unstyled mb-0">
                <li v-for="n in nearby.slice(0, 5)" :key="n.no" class="border-top">
                  <button class="nearby-item" :aria-label="n.title + ' 정보 보기'" @click="popup = { no: n.no, distance: n.distance }">
                    <img v-if="n.firstImage1" :src="n.firstImage1" class="nearby-thumb" alt="" loading="lazy" @error="n.firstImage1 = ''" />
                    <span v-else class="nearby-thumb d-flex align-items-center justify-content-center"><i :class="['bi', typeIcon(n.contentTypeId)]" aria-hidden="true"></i></span>
                    <span class="flex-grow-1 min-w-0">
                      <span class="d-block mm-item-title text-truncate">{{ n.title }}</span>
                      <span class="d-block mm-item-meta text-truncate">{{ n.contentTypeName }}, {{ n.distance }}km</span>
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
    <AttractionPopup v-if="popup" :no="popup.no" :distance="popup.distance" @close="popup = null" @loaded="onPlaceLoaded" />
  </div>
</template>

<style scoped>
.culture-card { border: 0; background: transparent; padding: 0; border-radius: var(--mm-r-md); }
.culture-card:focus-visible { outline: 0; box-shadow: 0 0 0 0.2rem rgba(255, 107, 74, 0.3); }
.poster-wrap { position: relative; border-radius: var(--mm-r-md); overflow: hidden; margin-bottom: var(--mm-sp-3); background: var(--mm-surface); }
.poster { width: 100%; aspect-ratio: 3 / 4; object-fit: cover; display: block; background: var(--mm-surface); color: var(--mm-placeholder); border-radius: var(--mm-r-md); transition: transform 0.3s; }
.culture-card:hover img.poster { transform: scale(1.03); }
.poster-chip { position: absolute; top: var(--mm-sp-2); left: var(--mm-sp-2); background: #fff; color: var(--mm-ink); border-radius: var(--mm-r-pill); padding: 2px 10px; font-size: var(--mm-fs-xs); font-weight: 700; box-shadow: var(--mm-shadow-1); }
.poster-chip.now { background: var(--mm-coral-dark); color: #fff; }
.poster-chip.free { left: auto; right: var(--mm-sp-2); background: var(--mm-ink); color: #fff; }
.title { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; white-space: normal; }
@media (prefers-reduced-motion: reduce) { .poster { transition: none; } }
.wide { max-width: 820px; }
.cd-title { font-size: var(--mm-fs-xl); font-weight: 700; letter-spacing: -0.02em; line-height: 1.3; color: var(--mm-ink); margin: 0; }
.cd-poster { border-radius: var(--mm-r-md); }
/* 좁은 화면에서는 포스터가 화면을 다 차지하지 않게 줄인다 */
@media (max-width: 767.98px) { .cd-poster { max-width: 180px; display: block; } }
.cd-facts { font-size: var(--mm-fs-sm); row-gap: var(--mm-sp-2); }
.cd-facts dt { font-weight: 400; color: var(--mm-muted); }
.cd-facts dd { margin: 0; color: var(--mm-text); }
.cd-h { font-size: var(--mm-fs-md); font-weight: 700; color: var(--mm-ink); margin: var(--mm-sp-4) 0 var(--mm-sp-1); }
.nearby-item { display: flex; align-items: center; gap: var(--mm-sp-3); width: 100%; padding: var(--mm-sp-2) var(--mm-sp-1); border: 0; background: transparent; text-align: left; border-radius: var(--mm-r-sm); color: inherit; }
.nearby-item:hover, .nearby-item:focus-visible { background: var(--mm-surface); }
.nearby-thumb { width: 44px; height: 44px; border-radius: var(--mm-r-sm); object-fit: cover; background: var(--mm-surface); color: var(--mm-placeholder); flex-shrink: 0; }
.venue-map { position: relative; height: 200px; border-radius: var(--mm-r-md); overflow: hidden; border: 1px solid var(--mm-line); }
</style>
