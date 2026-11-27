<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
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
  { to: '/culture', label: '공연·행사', icon: 'bi-ticket-perforated' },
  { to: '/hotplaces', label: '핫플', icon: 'bi-camera' },
  { to: '/board', label: '커뮤니티', icon: 'bi-chat-square-text' },
  { to: '/news', label: '뉴스', icon: 'bi-newspaper' },
]

// 줄어드는 헤더: 조금이라도 내려가면 낮아지고 그림자가 생긴다
const small = ref(false)
const onScroll = () => (small.value = window.scrollY > 16)
onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

async function logout() {
  await auth.logout()
  router.push('/')
}
</script>

<template>
  <nav class="navbar navbar-expand-lg fixed-top mm-nav" :class="{ small }" aria-label="주 메뉴">
    <div class="container-fluid px-3">
      <RouterLink class="navbar-brand mm-brand" to="/" aria-label="너랑 갈.지도 홈">
        <img src="/logo-wordmark.png" alt="너랑 갈.지도" class="brand-img" />
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
.mm-nav { background: #fff; border-bottom: 1px solid var(--mm-line); min-height: var(--mm-nav-h); }
.mm-brand { padding: 0; margin-right: 14px; }
.brand-img { height: 30px; width: auto; display: block; }
.nav-link { font-weight: 600; color: var(--mm-muted); border-radius: var(--mm-r-sm); padding-inline: 12px !important; }
.nav-link.active { color: var(--mm-ink); }
</style>
