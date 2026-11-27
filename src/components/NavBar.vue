<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useDraftStore } from '@/stores/draft'

const auth = useAuthStore()
const draft = useDraftStore()
const router = useRouter()

const menus = [
  { to: '/', label: '지도 탐색', icon: 'bi-map' },
  { to: '/plans', label: '내 플랜', icon: 'bi-signpost-2' },
  { to: '/mates', label: '메이트', icon: 'bi-people' },
  { to: '/hotplaces', label: '핫플', icon: 'bi-camera' },
  { to: '/board', label: '커뮤니티', icon: 'bi-chat-square-text' },
  { to: '/news', label: '뉴스', icon: 'bi-newspaper' },
]

async function logout() {
  await auth.logout()
  router.push('/')
}
</script>

<template>
  <nav class="navbar navbar-expand-lg fixed-top mm-nav" aria-label="주 메뉴">
    <div class="container-fluid px-3">
      <RouterLink class="navbar-brand mm-brand" to="/" aria-label="너랑 갈.지도 홈">
        너랑 갈<i class="bi bi-geo-alt-fill brand-pin" aria-hidden="true"></i>지도
      </RouterLink>
      <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#mmNav" aria-controls="mmNav" aria-expanded="false" aria-label="메뉴 열기">
        <span class="navbar-toggler-icon"></span>
      </button>
      <div id="mmNav" class="collapse navbar-collapse">
        <ul class="navbar-nav me-auto">
          <li v-for="m in menus" :key="m.to" class="nav-item">
            <RouterLink class="nav-link" :to="m.to" active-class="active" :exact-active-class="m.to === '/' ? 'active' : ''">
              <i :class="['bi', m.icon, 'me-1']" aria-hidden="true"></i>{{ m.label }}
            </RouterLink>
          </li>
        </ul>
        <div class="d-flex align-items-center gap-2 py-2 py-lg-0">
          <RouterLink to="/plans/new" class="btn btn-sm btn-outline-coral position-relative">
            <i class="bi bi-bag-plus me-1" aria-hidden="true"></i>담은 장소
            <span v-if="draft.count" class="badge rounded-pill bg-ink ms-1">{{ draft.count }}</span>
          </RouterLink>
          <template v-if="auth.isLogin">
            <RouterLink to="/mypage" class="btn btn-sm btn-light">{{ auth.member.name }}님</RouterLink>
            <button class="btn btn-sm btn-link text-secondary text-decoration-none" @click="logout">로그아웃</button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="btn btn-sm btn-coral">로그인</RouterLink>
            <RouterLink to="/join" class="btn btn-sm btn-link text-secondary text-decoration-none">회원가입</RouterLink>
          </template>
        </div>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.mm-nav { background: rgba(255, 255, 255, 0.96); border-bottom: 1px solid var(--mm-line); min-height: var(--mm-nav-h); backdrop-filter: blur(6px); }
.mm-brand { font-weight: 800; letter-spacing: -0.03em; color: var(--mm-ink); font-size: 1.25rem; }
.brand-pin { color: var(--mm-coral); font-size: 0.95rem; margin: 0 1px; vertical-align: baseline; }
.nav-link { font-weight: 600; color: #4b5563; border-radius: 8px; padding-inline: 12px !important; }
.nav-link.active { color: var(--mm-coral-dark); background: #fff1ec; }
</style>
