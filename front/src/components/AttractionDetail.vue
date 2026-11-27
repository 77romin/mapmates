<script setup>
import { ref, watch } from 'vue'
import http from '@/api/http'
import { useDraftStore } from '@/stores/draft'
import { typeIcon } from '@/utils/place'

const props = defineProps({ no: { type: Number, required: true } })
const emit = defineEmits(['close', 'extra', 'loaded'])
const draft = useDraftStore()

const a = ref(null)
const error = ref('')
const tab = ref('info')
const weather = ref(null)
const sun = ref(null)
const nearby = ref({ kind: '', list: null })
const chargers = ref(null)
const extraLoading = ref(false)

async function load() {
  a.value = null
  error.value = ''
  tab.value = 'info'
  weather.value = null
  chargers.value = null
  nearby.value = { kind: '', list: null }
  try {
    a.value = await http.get(`/attractions/${props.no}`)
    emit('loaded', a.value)
    sun.value = await http.get(`/attractions/${props.no}/sun`).catch(() => null)
    weather.value = await http.get(`/attractions/${props.no}/weather`).catch(() => ({ available: false, message: '날씨 정보를 불러오지 못했습니다.' }))
  } catch (e) {
    error.value = e.message
  }
}
watch(() => props.no, load, { immediate: true })

async function showNearby(kind) {
  tab.value = kind
  extraLoading.value = true
  try {
    if (kind === 'charger') {
      chargers.value = await http.get(`/attractions/${props.no}/chargers`)
      emit('extra', (chargers.value.stations || []).map((s, i) => ({ id: 'ev' + i, latitude: s.latitude, longitude: s.longitude, title: s.name, kind: 'meet', label: '⚡' })))
    } else {
      const list = await http.get(`/attractions/${props.no}/nearby`, { params: { kind, radius: kind === 'festival' ? 20 : 3 } })
      nearby.value = { kind, list }
      emit('extra', list.map((n) => ({ id: 'n' + n.no, latitude: n.latitude, longitude: n.longitude, title: n.title, kind: 'hot' })))
    }
  } catch (e) {
    nearby.value = { kind, list: [] }
    chargers.value = { available: false, message: e.message }
  } finally {
    extraLoading.value = false
  }
}

const fmtTime = (t) => `${t.slice(4, 6)}/${t.slice(6, 8)} ${t.slice(9, 11)}시`
// 서버 HTML(overview)은 태그를 제거하고 텍스트로만 보여준다
const plain = (html) => (html || '').replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '')
</script>

<template>
  <div class="d-flex flex-column h-100 min-h-0">
    <div class="p-2 border-bottom d-flex align-items-center gap-2">
      <button class="btn btn-sm btn-light" @click="emit('close')"><i class="bi bi-arrow-left" aria-hidden="true"></i> 목록</button>
      <span class="fw-bold text-truncate">{{ a?.title }}</span>
    </div>
    <div class="mm-panel-body">
      <div v-if="error" class="mm-empty"><i class="bi bi-exclamation-circle"></i>{{ error }}</div>
      <div v-else-if="!a" class="mm-empty"><div class="spinner-border text-secondary" role="status"></div></div>
      <template v-else>
        <img v-if="a.firstImage1" :src="a.firstImage1" class="w-100 detail-img" :alt="a.title" @error="a.firstImage1 = ''" />
        <div v-else class="detail-noimg"><i :class="['bi', typeIcon(a.contentTypeId)]" aria-hidden="true"></i><span>등록된 사진이 없습니다</span></div>
        <div class="p-3">
          <span class="badge badge-soft">{{ a.contentTypeName }}</span>
          <span class="badge badge-ink ms-1">{{ a.sidoName }} {{ a.gugunName }}</span>
          <h2 class="h5 mm-title mt-2 mb-1">{{ a.title }}</h2>
          <p class="small text-secondary mb-2"><i class="bi bi-geo-alt" aria-hidden="true"></i> {{ a.addr1 }} {{ a.addr2 }}<span v-if="a.tel"> · <i class="bi bi-telephone" aria-hidden="true"></i> {{ a.tel }}</span></p>
          <button class="btn w-100 mb-3" :class="draft.has(a.no) ? 'btn-light' : 'btn-coral'" @click="draft.has(a.no) ? draft.remove(a.no) : draft.add(a)">
            <i :class="['bi me-1', draft.has(a.no) ? 'bi-check-lg' : 'bi-plus-lg']" aria-hidden="true"></i>{{ draft.has(a.no) ? '플랜에 담김 (취소)' : '플랜에 담기' }}
          </button>

          <!-- 날씨 / 일출·일몰 -->
          <div class="mm-card p-3 mb-3">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <strong class="small">날씨 · 일출/일몰</strong>
              <span v-if="sun" class="small text-secondary"><i class="bi bi-sunrise text-coral" aria-hidden="true"></i> {{ sun.sunrise }} <i class="bi bi-sunset text-coral ms-1" aria-hidden="true"></i> {{ sun.sunset }}</span>
            </div>
            <div v-if="!weather" class="small text-secondary">날씨를 불러오는 중…</div>
            <div v-else-if="!weather.available" class="small text-secondary">{{ weather.message }}</div>
            <div v-else class="weather-row">
              <div v-for="f in weather.forecasts" :key="f.time" class="weather-cell">
                <div class="text-secondary">{{ fmtTime(f.time) }}</div>
                <div class="fw-bold">{{ f.TMP }}°</div>
                <div>{{ f.summary }}</div>
                <div class="text-secondary"><i class="bi bi-droplet" aria-hidden="true"></i>{{ f.POP }}%</div>
              </div>
            </div>
          </div>

          <ul class="nav nav-pills nav-fill small mb-3" role="tablist">
            <li class="nav-item"><button class="nav-link py-1" :class="{ active: tab === 'info' }" @click="tab = 'info'; emit('extra', [])">소개</button></li>
            <li class="nav-item"><button class="nav-link py-1" :class="{ active: tab === 'festival' }" @click="showNearby('festival')">축제</button></li>
            <li class="nav-item"><button class="nav-link py-1" :class="{ active: tab === 'store' }" @click="showNearby('store')">상권</button></li>
            <li class="nav-item"><button class="nav-link py-1" :class="{ active: tab === 'charger' }" @click="showNearby('charger')">충전소</button></li>
          </ul>

          <div v-if="tab === 'info'">
            <p v-if="a.overview" class="small pre-line mb-2">{{ plain(a.overview) }}</p>
            <p v-else class="small text-secondary">등록된 소개 글이 없습니다.</p>
          </div>
          <div v-else-if="extraLoading" class="text-center py-3"><div class="spinner-border spinner-border-sm text-secondary" role="status"></div></div>
          <div v-else-if="tab === 'charger'">
            <p v-if="!chargers?.available" class="small text-secondary">{{ chargers?.message }}</p>
            <p v-else-if="!chargers.stations.length" class="small text-secondary">반경 3km 안에 충전소가 없습니다.</p>
            <ul v-else class="list-unstyled small mb-0">
              <li v-for="(s, i) in chargers.stations" :key="i" class="py-2 border-bottom">
                <div class="fw-semibold">⚡ {{ s.name }} <span class="text-secondary fw-normal">{{ s.distance }}km</span></div>
                <div class="text-secondary">{{ s.addr }}</div>
                <span class="badge" :class="s.availableCount ? 'bg-success' : 'bg-secondary'">사용 가능 {{ s.availableCount }} / {{ s.total }}대</span>
              </li>
            </ul>
          </div>
          <div v-else>
            <p v-if="!nearby.list?.length" class="small text-secondary">{{ tab === 'festival' ? '반경 20km 안에 등록된 축제·행사가 없습니다.' : '반경 3km 안에 등록된 쇼핑·음식점이 없습니다.' }}</p>
            <ul v-else class="list-unstyled small mb-0">
              <li v-for="n in nearby.list" :key="n.no" class="py-2 border-bottom d-flex justify-content-between gap-2">
                <div class="min-w-0">
                  <div class="fw-semibold text-truncate">{{ n.title }}</div>
                  <div class="text-secondary text-truncate">{{ n.contentTypeName }} · {{ n.distance }}km · {{ n.addr1 }}</div>
                </div>
                <button class="btn btn-sm btn-outline-coral flex-shrink-0" :disabled="draft.has(n.no)" @click="draft.add(n)">{{ draft.has(n.no) ? '담김' : '담기' }}</button>
              </li>
            </ul>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.detail-img { height: 190px; object-fit: cover; }
.detail-noimg { height: 84px; background: var(--mm-surface); color: var(--mm-muted); display: flex; align-items: center; justify-content: center; gap: 8px; font-size: var(--mm-fs-sm); }
.detail-noimg i { font-size: 1.6rem; }
.weather-row { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; }
.weather-cell { flex: 0 0 auto; text-align: center; font-size: var(--mm-fs-xs); background: var(--mm-bg); border-radius: var(--mm-r-md); padding: 6px 10px; min-width: 70px; }
.min-h-0 { min-height: 0; }
</style>
