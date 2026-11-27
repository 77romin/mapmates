<script setup>
import { onMounted, ref } from 'vue'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const plans = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    plans.value = await http.get('/plans')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function remove(p) {
  if (!confirm(`'${p.title}' 플랜을 삭제할까요? 되돌릴 수 없습니다.`)) return
  try {
    await http.delete(`/plans/${p.id}`)
    plans.value = plans.value.filter((x) => x.id !== p.id)
  } catch (e) {
    alert(e.message)
  }
}
</script>

<template>
  <div class="mm-page">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h1 class="h3 mm-title mb-0">내 플랜</h1>
      <RouterLink to="/plans/new" class="btn btn-coral"><i class="bi bi-plus-lg me-1" aria-hidden="true"></i>새 플랜</RouterLink>
    </div>

    <div v-if="loading" class="mm-empty"><div class="spinner-border text-secondary" role="status"></div></div>
    <div v-else-if="error" class="mm-empty"><i class="bi bi-wifi-off"></i>{{ error }}<br /><button class="btn btn-sm btn-outline-coral mt-2" @click="load">다시 시도</button></div>
    <div v-else-if="!plans.length" class="mm-card mm-empty">
      <i class="bi bi-signpost-2"></i>아직 만든 플랜이 없습니다.<br />지도에서 가고 싶은 곳을 담아 첫 플랜을 만들어 보세요.
      <div class="mt-3"><RouterLink to="/" class="btn btn-outline-coral">지도에서 장소 찾기</RouterLink></div>
    </div>
    <div v-else class="row g-3">
      <div v-for="p in plans" :key="p.id" class="col-md-6 col-lg-4">
        <div v-reveal class="mm-card spot p-3 h-100 d-flex flex-column">
          <div class="mb-1">
            <span v-if="p.memberId !== auth.member.id" class="badge badge-ink me-1">메이트 참여</span>
            <span v-if="p.isPublic" class="badge badge-soft">공개</span>
          </div>
          <h2 class="h5 mm-title">{{ p.title }}</h2>
          <p class="small text-secondary mb-1"><i class="bi bi-calendar3" aria-hidden="true"></i> {{ p.startDate || '날짜 미정' }}<span v-if="p.endDate"> ~ {{ p.endDate }}</span></p>
          <p class="small text-secondary mb-3"><i class="bi bi-geo" aria-hidden="true"></i> 방문지 {{ p.itemCount }}곳 · 예산 {{ (p.budget || 0).toLocaleString() }}원</p>
          <div class="mt-auto d-flex gap-2">
            <RouterLink :to="`/plans/${p.id}`" class="btn btn-sm btn-outline-coral flex-grow-1">열기</RouterLink>
            <button v-if="p.memberId === auth.member.id" class="btn btn-sm btn-light" aria-label="플랜 삭제" @click="remove(p)"><i class="bi bi-trash" aria-hidden="true"></i></button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
