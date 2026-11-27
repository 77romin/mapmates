<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import http from '@/api/http'
import { useDraftStore } from '@/stores/draft'
import { typeIcon } from '@/utils/place'

/** 장소 한 곳의 정보를 보여주고 플랜에 담는 팝업. 다른 모달 위에 겹쳐 뜬다. */
const props = defineProps({
  no: { type: Number, required: true },
  distance: { type: Number, default: null }, // 기준 지점(공연장 등)으로부터의 거리(km)
})
const emit = defineEmits(['close', 'loaded'])
const draft = useDraftStore()

const a = ref(null)
const error = ref('')
const weather = ref(null)
const closeBtn = ref(null)

async function load() {
  a.value = null
  error.value = ''
  weather.value = null
  try {
    a.value = await http.get(`/attractions/${props.no}`)
    emit('loaded', a.value)
    closeBtn.value?.focus()
    weather.value = await http.get(`/attractions/${props.no}/weather`).catch(() => null)
  } catch (e) {
    error.value = e.message
  }
}
watch(() => props.no, load, { immediate: true })

const onKey = (e) => e.key === 'Escape' && emit('close')
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

// 서버 HTML(overview)은 태그를 제거하고 텍스트로만 보여준다
const plain = (html) => (html || '').replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '')
const homepageUrl = (html) => {
  const m = /https?:\/\/[^\s"'<>]+/.exec(html || '')
  return m ? m[0] : null
}
const kakaoMapUrl = (x) => `https://map.kakao.com/link/map/${encodeURIComponent(x.title.replace(/[,/]/g, ' '))},${x.latitude},${x.longitude}`
</script>

<template>
  <div class="mm-modal place-popup" role="dialog" aria-modal="true" :aria-label="a ? a.title + ' 정보' : '장소 정보'" @click.self="emit('close')">
    <div class="mm-modal-body p-0 overflow-hidden d-flex flex-column">
      <div v-if="error" class="p-4">
        <div class="alert alert-danger mb-3">{{ error }}</div>
        <button class="btn btn-light w-100" @click="emit('close')">닫기</button>
      </div>
      <div v-else-if="!a" class="text-center p-5"><div class="spinner-border text-secondary" role="status"></div></div>
      <template v-else>
        <div class="hero" :class="{ empty: !a.firstImage1 }">
          <img v-if="a.firstImage1" :src="a.firstImage1" :alt="a.title" @error="a.firstImage1 = ''" />
          <div v-else class="no-img"><i :class="['bi', typeIcon(a.contentTypeId)]" aria-hidden="true"></i><span>등록된 사진이 없습니다</span></div>
          <button ref="closeBtn" class="btn btn-light btn-sm close" aria-label="닫기" @click="emit('close')"><i class="bi bi-x-lg" aria-hidden="true"></i></button>
        </div>
        <div class="p-3 body">
          <div class="mb-1">
            <span class="badge badge-soft">{{ a.contentTypeName }}</span>
            <span class="badge badge-ink ms-1">{{ a.sidoName }} {{ a.gugunName }}</span>
            <span v-if="distance != null" class="badge badge-ink ms-1"><i class="bi bi-geo" aria-hidden="true"></i> 공연장에서 {{ distance }}km</span>
          </div>
          <h3 class="h5 mm-title mb-2">{{ a.title }}</h3>
          <ul class="list-unstyled small mb-2">
            <li><i class="bi bi-geo-alt text-coral me-1" aria-hidden="true"></i>{{ a.addr1 || '주소 정보 없음' }} {{ a.addr2 }}</li>
            <li v-if="a.tel"><i class="bi bi-telephone text-coral me-1" aria-hidden="true"></i>{{ a.tel }}</li>
            <li v-if="weather?.available && weather.forecasts[0]">
              <i class="bi bi-cloud-sun text-coral me-1" aria-hidden="true"></i>지금 {{ weather.forecasts[0].TMP }}° · {{ weather.forecasts[0].summary }} · 강수확률 {{ weather.forecasts[0].POP }}%
            </li>
          </ul>
          <p v-if="a.overview" class="small pre-line overview mb-2">{{ plain(a.overview) }}</p>
          <p v-else class="small text-secondary mb-2">등록된 소개 글이 없습니다.</p>
          <div class="d-flex gap-3 small">
            <a :href="kakaoMapUrl(a)" target="_blank" rel="noopener noreferrer">{{ a.firstImage1 ? '카카오맵에서 보기' : '카카오맵에서 사진·정보 보기' }} <i class="bi bi-box-arrow-up-right" aria-hidden="true"></i></a>
            <a v-if="homepageUrl(a.homepage)" :href="homepageUrl(a.homepage)" target="_blank" rel="noopener noreferrer">홈페이지 <i class="bi bi-box-arrow-up-right" aria-hidden="true"></i></a>
          </div>
        </div>
        <div class="p-3 border-top d-flex gap-2">
          <button class="btn btn-light flex-shrink-0" @click="emit('close')">닫기</button>
          <button class="btn flex-grow-1" :class="draft.has(a.no) ? 'btn-outline-coral' : 'btn-coral'" @click="draft.has(a.no) ? draft.remove(a.no) : draft.add(a)">
            <i :class="['bi me-1', draft.has(a.no) ? 'bi-check-lg' : 'bi-plus-lg']" aria-hidden="true"></i>{{ draft.has(a.no) ? '플랜에 담김 (취소)' : '플랜에 담기' }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.place-popup { z-index: 1060; }
.place-popup .mm-modal-body { max-width: 440px; }
.hero { position: relative; height: 200px; background: var(--mm-surface); flex-shrink: 0; }
.hero.empty { height: 96px; }
.hero img { width: 100%; height: 100%; object-fit: cover; }
.no-img { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--mm-muted); gap: 4px; font-size: var(--mm-fs-sm); }
.no-img i { font-size: 1.5rem; }
.close { position: absolute; top: 10px; right: 10px; border-radius: 50%; width: 32px; height: 32px; padding: 0; box-shadow: var(--mm-shadow-1); }
.body { overflow-y: auto; }
.overview { max-height: 9.5em; overflow-y: auto; line-height: 1.6; }
</style>
