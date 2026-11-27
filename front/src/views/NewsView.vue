<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/http'

const KEYWORDS = ['국내 여행', '지역 축제', '부산 여행', '제주 여행', '강원 여행', '맛집 여행']
const keyword = ref(KEYWORDS[0])
const news = ref([])
const loading = ref(false)
const error = ref('')

async function load(k = keyword.value) {
  keyword.value = k
  loading.value = true
  error.value = ''
  try {
    news.value = await http.get('/news', { params: { keyword: k, size: 20 } })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(() => load())

const fmt = (d) => {
  const t = new Date(d)
  return isNaN(t) ? d : t.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })
}
// 외부 링크는 http(s) 만 허용
const safe = (url) => (/^https?:\/\//.test(url) ? url : '#')
</script>

<template>
  <div class="mm-page">
    <h1 class="h3 mm-title mb-1">여행 뉴스</h1>
    <p v-blur-in class="text-secondary">관심 지역의 최신 여행 소식을 모았습니다.</p>
    <div class="d-flex flex-wrap gap-2 mb-3" role="group" aria-label="뉴스 주제">
      <button v-for="k in KEYWORDS" :key="k" class="btn btn-sm rounded-pill" :class="keyword === k ? 'bg-ink' : 'btn-light border'" @click="load(k)">{{ k }}</button>
    </div>
    <div v-if="loading" class="mm-empty"><div class="spinner-border text-secondary" role="status"></div><p class="mt-2 mb-0">뉴스를 모으는 중…</p></div>
    <div v-else-if="error" class="mm-empty"><i class="bi bi-wifi-off"></i>{{ error }}<br /><button class="btn btn-sm btn-outline-coral mt-2" @click="load()">다시 시도</button></div>
    <div v-else-if="!news.length" class="mm-card mm-empty"><i class="bi bi-newspaper"></i>'{{ keyword }}' 관련 뉴스를 찾지 못했습니다.</div>
    <div v-else class="mm-card">
      <a v-for="n in news" :key="n.id" :href="safe(n.link)" target="_blank" rel="noopener noreferrer" class="news-item">
        <div class="fw-semibold">{{ n.title }}</div>
        <div class="small text-secondary">{{ n.source }} · {{ fmt(n.pubDate) }} <i class="bi bi-box-arrow-up-right ms-1" aria-hidden="true"></i></div>
      </a>
    </div>
  </div>
</template>

<style scoped>
.news-item { display: block; padding: 14px 18px; border-bottom: 1px solid var(--mm-line); color: var(--mm-ink); text-decoration: none; }
.news-item:last-child { border-bottom: 0; }
.news-item:hover { background: #fff4ee; }
</style>
