<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const form = reactive({ userId: localStorage.getItem('mm_saved_id') || '', password: '' })
const remember = ref(!!localStorage.getItem('mm_saved_id'))
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  if (!form.userId.trim() || !form.password) return (error.value = '아이디와 비밀번호를 입력해 주세요.')
  loading.value = true
  try {
    await auth.login(form.userId.trim(), form.password)
    // 아이디 저장 (로컬 스토리지)
    remember.value ? localStorage.setItem('mm_saved_id', form.userId.trim()) : localStorage.removeItem('mm_saved_id')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-wrap">
    <form class="mm-card auth-card" novalidate @submit.prevent="submit">
      <img src="/logo.webp" alt="너랑 갈.지도 로고 — 너랑 갈 다음 여행을 지도 위에" class="auth-logo" />
      <p class="text-center text-secondary small mb-2">너랑 갈 다음 여행을 지도 위에</p>
      <h1 class="h4 mm-title mb-3 text-center">로그인</h1>
      <label class="form-label small fw-semibold" for="uid">아이디</label>
      <input id="uid" v-model="form.userId" class="form-control mb-3" autocomplete="username" />
      <label class="form-label small fw-semibold" for="pw">비밀번호</label>
      <input id="pw" v-model="form.password" type="password" class="form-control mb-2" autocomplete="current-password" />
      <div class="form-check mb-3"><input id="rem" v-model="remember" type="checkbox" class="form-check-input" /><label for="rem" class="form-check-label small">아이디 저장</label></div>
      <div v-if="error" class="alert alert-danger py-2 small" role="alert">{{ error }}</div>
      <button class="btn btn-coral w-100" :disabled="loading"><span v-if="loading" class="spinner-border spinner-border-sm me-1"></span>로그인</button>
      <div class="d-flex justify-content-between small mt-3">
        <RouterLink to="/find-password">비밀번호 찾기</RouterLink>
        <RouterLink to="/join">회원가입</RouterLink>
      </div>
    </form>
  </div>
</template>
