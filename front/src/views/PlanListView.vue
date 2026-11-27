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

    <div v-if="loading" class="row g-3" role="status" aria-busy="true" aria-label="플랜을 불러오는 중"><div v-for="n in 3" :key="n" class="col-md-6 col-lg-4" aria-hidden="true"><div class="mm-skel plan-skel"></div></div></div>
    <div v-else-if="error" class="mm-empty"><i class="bi bi-wifi-off"></i>{{ error }}<br /><button class="btn btn-sm btn-outline-coral mt-2" @click="load">다시 시도</button></div>
    <div v-else-if="!plans.length" class="mm-card mm-empty">
      <i class="bi bi-signpost-2"></i>아직 만든 플랜이 없습니다.<br />지도에서 가고 싶은 곳을 담아 첫 플랜을 만들어 보세요.
      <div class="mt-3"><RouterLink to="/" class="btn btn-outline-coral">지도에서 장소 찾기</RouterLink></div>
    </div>
    <div v-else class="row g-3">
      <div v-for="p in plans" :key="p.id" class="col-md-6 col-lg-4">
        <!-- 카드 전체가 플랜으로 가는 링크. 삭제는 조용한 버튼으로 구석에 둔다 -->
        <div v-reveal class="mm-card plan-card h-100">
          <RouterLink :to="`/plans/${p.id}`" class="plan-link">
            <div class="plan-count"><strong>{{ p.itemCount }}</strong>곳</div>
            <div class="min-w-0 flex-grow-1">
              <h2 class="plan-name text-truncate">{{ p.title }}</h2>
              <div class="plan-when">{{ p.startDate || '날짜 미정' }}<template v-if="p.endDate && p.endDate !== p.startDate"> ~ {{ p.endDate }}</template></div>
              <div class="mm-item-meta">예산 {{ (p.budget || 0).toLocaleString() }}원</div>
              <div class="mt-2">
                <span v-if="p.memberId !== auth.member.id" class="badge badge-ink me-1">메이트 참여</span>
                <span v-if="p.isPublic" class="badge badge-soft">공개</span>
              </div>
            </div>
          </RouterLink>
          <button v-if="p.memberId === auth.member.id" class="plan-del" :aria-label="p.title + ' 삭제'" @click="remove(p)"><i class="bi bi-trash" aria-hidden="true"></i></button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.plan-skel { height: 132px; border-radius: var(--mm-r-md); }
.plan-card { position: relative; transition: box-shadow 0.15s; }
.plan-card:hover { box-shadow: var(--mm-shadow-2); }
.plan-link { display: flex; gap: var(--mm-sp-4); align-items: flex-start; padding: var(--mm-sp-5); height: 100%; color: inherit; text-decoration: none; border-radius: var(--mm-r-md); }
.plan-link:focus-visible { outline: 0; box-shadow: 0 0 0 0.2rem rgba(255, 107, 74, 0.3); }
.plan-count { flex-shrink: 0; width: 56px; height: 56px; border-radius: var(--mm-r-md); background: var(--mm-route-tint); color: var(--mm-route); display: flex; align-items: baseline; justify-content: center; padding-top: 14px; font-size: var(--mm-fs-xs); font-weight: 600; }
.plan-count strong { font-size: var(--mm-fs-lg); font-weight: 800; margin-right: 1px; }
.plan-name { font-size: var(--mm-fs-lg); font-weight: 700; letter-spacing: -0.02em; color: var(--mm-ink); margin: 0 var(--mm-sp-6) var(--mm-sp-1) 0; }
.plan-when { font-size: var(--mm-fs-md); font-weight: 600; color: var(--mm-text); }
.plan-del { position: absolute; top: var(--mm-sp-3); right: var(--mm-sp-3); width: 32px; height: 32px; border: 0; border-radius: 50%; background: transparent; color: var(--mm-muted); }
.plan-del:hover { background: var(--mm-surface); color: var(--mm-danger); }
</style>
