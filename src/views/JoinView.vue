<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const form = reactive({ userId: '', password: '', password2: '', name: '', email: '', tags: '' })
const touched = reactive({})
const error = ref('')
const loading = ref(false)
const TAGS = ['바다', '등산', '맛집', '사진', '역사', '도시', '휴양']

// 폼 유효성 검사
const errors = computed(() => ({
  userId: /^[A-Za-z0-9_]{4,20}$/.test(form.userId) ? '' : '영문·숫자·밑줄 4~20자로 입력해 주세요.',
  password: form.password.length >= 8 ? '' : '8자 이상 입력해 주세요.',
  password2: form.password2 && form.password === form.password2 ? '' : '비밀번호가 일치하지 않습니다.',
  name: form.name.trim() ? '' : '이름을 입력해 주세요.',
  email: /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ? '' : '이메일 형식이 올바르지 않습니다.',
}))
const valid = computed(() => Object.values(errors.value).every((e) => !e))
const cls = (f) => (touched[f] ? (errors.value[f] ? 'is-invalid' : 'is-valid') : '')

function toggleTag(t) {
  const set = new Set(form.tags.split(',').filter(Boolean))
  set.has(t) ? set.delete(t) : set.size < 3 && set.add(t)
  form.tags = [...set].join(',')
}

async function submit() {
  Object.keys(errors.value).forEach((k) => (touched[k] = true))
  if (!valid.value) return
  loading.value = true
  error.value = ''
  try {
    await http.post('/auth/join', { userId: form.userId, password: form.password, name: form.name.trim(), email: form.email, tags: form.tags })
    await auth.login(form.userId, form.password)
    router.replace('/')
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
      <h1 class="h4 mm-title mb-4">회원가입</h1>
      <div class="mb-3">
        <label class="form-label small fw-semibold" for="jId">아이디</label>
        <input id="jId" v-model.trim="form.userId" :class="['form-control', cls('userId')]" autocomplete="username" @blur="touched.userId = true" />
        <div class="invalid-feedback">{{ errors.userId }}</div>
      </div>
      <div class="mb-3">
        <label class="form-label small fw-semibold" for="jPw">비밀번호</label>
        <input id="jPw" v-model="form.password" type="password" :class="['form-control', cls('password')]" autocomplete="new-password" @blur="touched.password = true" />
        <div class="invalid-feedback">{{ errors.password }}</div>
      </div>
      <div class="mb-3">
        <label class="form-label small fw-semibold" for="jPw2">비밀번호 확인</label>
        <input id="jPw2" v-model="form.password2" type="password" :class="['form-control', cls('password2')]" autocomplete="new-password" @blur="touched.password2 = true" />
        <div class="invalid-feedback">{{ errors.password2 }}</div>
      </div>
      <div class="mb-3">
        <label class="form-label small fw-semibold" for="jName">이름</label>
        <input id="jName" v-model="form.name" :class="['form-control', cls('name')]" maxlength="30" autocomplete="name" @blur="touched.name = true" />
        <div class="invalid-feedback">{{ errors.name }}</div>
      </div>
      <div class="mb-3">
        <label class="form-label small fw-semibold" for="jEmail">이메일</label>
        <input id="jEmail" v-model.trim="form.email" type="email" :class="['form-control', cls('email')]" autocomplete="email" @blur="touched.email = true" />
        <div class="invalid-feedback">{{ errors.email }}</div>
      </div>
      <div class="form-label small fw-semibold">여행 스타일 <span class="text-secondary fw-normal">(선택, 최대 3개 · 메이트 추천에 사용)</span></div>
      <div class="d-flex flex-wrap gap-1 mb-4">
        <button v-for="t in TAGS" :key="t" type="button" class="btn btn-sm rounded-pill" :class="form.tags.split(',').includes(t) ? 'bg-ink' : 'btn-light border'" :aria-pressed="form.tags.split(',').includes(t)" @click="toggleTag(t)">#{{ t }}</button>
      </div>
      <div v-if="error" class="alert alert-danger py-2 small" role="alert">{{ error }}</div>
      <button class="btn btn-coral w-100" :disabled="loading"><span v-if="loading" class="spinner-border spinner-border-sm me-1"></span>가입하기</button>
      <p class="small text-center mt-3 mb-0">이미 계정이 있나요? <RouterLink to="/login">로그인</RouterLink></p>
    </form>
  </div>
</template>
