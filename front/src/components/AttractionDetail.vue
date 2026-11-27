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
const expanded = ref(false) // 소개 글을 끝까지 펼쳤는지
</script>

<template>
  <div class="d-flex flex-column h-100 min-h-0">
    <div class="mm-panel-body detail">
      <div v-if="error" class="mm-empty"><i class="bi bi-exclamation-circle"></i>{{ error }}<br /><button class="btn btn-sm mm-btn-quiet mt-2" @click="emit('close')">목록으로</button></div>
      <div v-else-if="!a" role="status" aria-busy="true" aria-label="장소 정보를 불러오는 중">
        <div class="hero mm-skel"></div>
        <div class="p-3"><div class="mm-skel mm-skel-line w-75 title-skel"></div><div class="mm-skel mm-skel-line w-50"></div><div class="mm-skel mm-skel-line"></div></div>
      </div>
      <template v-else>
        <!-- 사진이 맨 위를 가득 채우고, 뒤로 가기 버튼이 그 위에 뜬다 -->
        <div class="hero" :class="{ empty: !a.firstImage1 }">
          <img v-if="a.firstImage1" :src="a.firstImage1" :alt="a.title" @error="a.firstImage1 = ''" />
          <div v-else class="hero-empty"><i :class="['bi', typeIcon(a.contentTypeId)]" aria-hidden="true"></i><span>등록된 사진이 없습니다</span></div>
          <button class="mm-add hero-back" aria-label="목록으로" @click="emit('close')"><i class="bi bi-arrow-left" aria-hidden="true"></i></button>
        </div>
        <div class="p-3">
          <div class="mm-item-meta">{{ a.contentTypeName }}, {{ a.sidoName }} {{ a.gugunName }}</div>
          <h2 class="detail-title">{{ a.title }}</h2>
          <p class="detail-addr"><i class="bi bi-geo-alt me-1" aria-hidden="true"></i>{{ a.addr1 }} {{ a.addr2 }}</p>
          <p v-if="a.tel" class="detail-addr"><i class="bi bi-telephone me-1" aria-hidden="true"></i>{{ a.tel }}</p>

          <!-- 날씨 / 일출·일몰 -->
          <section class="detail-sec" aria-label="날씨와 일출·일몰">
            <div class="d-flex justify-content-between align-items-baseline mb-2">
              <h3 class="detail-h">날씨</h3>
              <span v-if="sun" class="mm-item-meta"><i class="bi bi-sunrise me-1" aria-hidden="true"></i>{{ sun.sunrise }}<i class="bi bi-sunset ms-2 me-1" aria-hidden="true"></i>{{ sun.sunset }}</span>
            </div>
            <div v-if="!weather" class="weather-row" aria-hidden="true"><div v-for="n in 4" :key="n" class="weather-cell mm-skel"></div></div>
            <div v-else-if="!weather.available" class="mm-item-meta">{{ weather.message }}</div>
            <div v-else class="weather-row">
              <div v-for="f in weather.forecasts" :key="f.time" class="weather-cell">
                <div class="w-time">{{ fmtTime(f.time) }}</div>
                <div class="w-temp">{{ f.TMP }}°</div>
                <div class="w-sum">{{ f.summary }}</div>
                <div class="w-pop"><i class="bi bi-droplet" aria-hidden="true"></i>{{ f.POP }}%</div>
              </div>
            </div>
          </section>

          <div class="mm-seg mb-3" role="tablist">
            <button role="tab" :aria-selected="tab === 'info'" :class="{ on: tab === 'info' }" @click="tab = 'info'; emit('extra', [])">소개</button>
            <button role="tab" :aria-selected="tab === 'festival'" :class="{ on: tab === 'festival' }" @click="showNearby('festival')">축제</button>
            <button role="tab" :aria-selected="tab === 'store'" :class="{ on: tab === 'store' }" @click="showNearby('store')">상권</button>
            <button role="tab" :aria-selected="tab === 'charger'" :class="{ on: tab === 'charger' }" @click="showNearby('charger')">충전소</button>
          </div>

          <div v-if="tab === 'info'">
            <template v-if="a.overview">
              <p class="overview pre-line" :class="{ clamp: !expanded }">{{ plain(a.overview) }}</p>
              <button v-if="plain(a.overview).length > 180" class="btn btn-link btn-sm p-0 more" :aria-expanded="expanded" @click="expanded = !expanded">{{ expanded ? '접기' : '더 보기' }}</button>
            </template>
            <p v-else class="mm-item-meta">등록된 소개 글이 없습니다.</p>
          </div>
          <div v-else-if="extraLoading" aria-busy="true"><div v-for="n in 3" :key="n" class="mm-skel near-skel"></div></div>
          <div v-else-if="tab === 'charger'">
            <p v-if="!chargers?.available" class="mm-item-meta">{{ chargers?.message }}</p>
            <p v-else-if="!chargers.stations.length" class="mm-item-meta">반경 3km 안에 충전소가 없습니다.</p>
            <ul v-else class="list-unstyled mb-0">
              <li v-for="(s, i) in chargers.stations" :key="i" class="near">
                <div class="min-w-0 flex-grow-1">
                  <div class="near-title text-truncate"><i class="bi bi-lightning-charge-fill me-1" aria-hidden="true"></i>{{ s.name }}</div>
                  <div class="mm-item-meta text-truncate">{{ s.distance }}km, {{ s.addr }}</div>
                </div>
                <span class="badge" :class="s.availableCount ? 'badge-ok' : 'badge-ink'">사용 가능 {{ s.availableCount }} / {{ s.total }}대</span>
              </li>
            </ul>
          </div>
          <div v-else>
            <p v-if="!nearby.list?.length" class="mm-item-meta">{{ tab === 'festival' ? '반경 20km 안에 등록된 축제·행사가 없습니다.' : '반경 3km 안에 등록된 쇼핑·음식점이 없습니다.' }}</p>
            <ul v-else class="list-unstyled mb-0">
              <li v-for="n in nearby.list" :key="n.no" class="near">
                <div class="min-w-0 flex-grow-1">
                  <div class="near-title text-truncate">{{ n.title }}</div>
                  <div class="mm-item-meta text-truncate">{{ n.contentTypeName }}, {{ n.distance }}km</div>
                </div>
                <button class="mm-add" :class="{ on: draft.has(n.no) }" :aria-label="draft.has(n.no) ? n.title + ' 담기 취소' : n.title + ' 플랜에 담기'" @click="draft.has(n.no) ? draft.remove(n.no) : draft.add(n)">
                  <i :class="['bi', draft.has(n.no) ? 'bi-check-lg' : 'bi-plus-lg']" aria-hidden="true"></i>
                </button>
              </li>
            </ul>
          </div>
        </div>
      </template>
    </div>
    <!-- 주요 행동은 패널 아래에 고정 -->
    <div v-if="a" class="detail-foot">
      <button class="btn w-100" :class="draft.has(a.no) ? 'mm-btn-quiet' : 'btn-coral'" @click="draft.has(a.no) ? draft.remove(a.no) : draft.add(a)">
        <i :class="['bi me-1', draft.has(a.no) ? 'bi-check-lg' : 'bi-plus-lg']" aria-hidden="true"></i>{{ draft.has(a.no) ? '플랜에 담김 (취소)' : '플랜에 담기' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.min-h-0 { min-height: 0; }
.hero { position: relative; aspect-ratio: 4 / 3; background: var(--mm-surface); }
.hero.empty { aspect-ratio: 3 / 1; }
.hero img { width: 100%; height: 100%; object-fit: cover; display: block; }
.hero-empty { height: 100%; display: flex; align-items: center; justify-content: center; gap: var(--mm-sp-2); color: var(--mm-muted); font-size: var(--mm-fs-sm); }
.hero-empty i { font-size: 1.6rem; color: var(--mm-placeholder); }
.hero-back { position: absolute; top: var(--mm-sp-3); left: var(--mm-sp-3); border-color: transparent; box-shadow: var(--mm-shadow-1); }
.title-skel { height: 24px; }
.detail-title { font-size: var(--mm-fs-xl); font-weight: 700; letter-spacing: -0.02em; line-height: 1.3; color: var(--mm-ink); margin: 2px 0 var(--mm-sp-2); }
.detail-addr { font-size: var(--mm-fs-sm); color: var(--mm-text); margin: 0 0 2px; }
.detail-sec { margin: var(--mm-sp-5) 0; padding-top: var(--mm-sp-4); border-top: 1px solid var(--mm-line); }
.detail-h { font-size: var(--mm-fs-md); font-weight: 700; color: var(--mm-ink); margin: 0; }
.weather-row { display: flex; gap: var(--mm-sp-2); overflow-x: auto; padding-bottom: var(--mm-sp-1); }
.weather-cell { flex: 0 0 auto; min-width: 72px; min-height: 92px; text-align: center; background: var(--mm-surface); border-radius: var(--mm-r-md); padding: var(--mm-sp-2) var(--mm-sp-3); }
.w-time, .w-pop { font-size: var(--mm-fs-xs); color: var(--mm-muted); }
.w-temp { font-size: var(--mm-fs-lg); font-weight: 700; color: var(--mm-ink); line-height: 1.3; }
.w-sum { font-size: var(--mm-fs-xs); color: var(--mm-text); }
.overview { font-size: var(--mm-fs-sm); line-height: 1.75; color: var(--mm-text); margin-bottom: var(--mm-sp-1); }
.overview.clamp { display: -webkit-box; -webkit-line-clamp: 6; -webkit-box-orient: vertical; overflow: hidden; }
.more { font-weight: 600; color: var(--mm-ink); }
.near { display: flex; align-items: center; gap: var(--mm-sp-3); padding: var(--mm-sp-3) 0; border-bottom: 1px solid var(--mm-line); }
.near:last-child { border-bottom: 0; }
.near-title { font-size: var(--mm-fs-md); font-weight: 600; color: var(--mm-ink); }
.near-skel { height: 48px; border-radius: var(--mm-r-sm); margin-bottom: var(--mm-sp-2); }
.detail-foot { padding: var(--mm-sp-3) var(--mm-sp-4); border-top: 1px solid var(--mm-line); background: #fff; }
</style>
