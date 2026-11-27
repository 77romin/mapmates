<script setup>
import AuthLayout from '@/components/AuthLayout.vue'
import { reactive, ref } from 'vue'
import http from '@/api/http'

const form = reactive({ userId: '', email: '' })
const error = ref('')
const result = ref(null)
const loading = ref(false)

async function submit() {
  error.value = ''
  if (!form.userId.trim() || !form.email.trim()) return (error.value = '아이디와 가입한 이메일을 입력해 주세요.')
  loading.value = true
  try {
    result.value = await http.post('/auth/find-password', form)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <div>
      <h1 class="h2 mm-title mb-1">비밀번호 찾기</h1>
      <p class="text-secondary mb-4">아이디와 가입 이메일이 일치하면 임시 비밀번호를 발급합니다.</p>
      <div v-if="result" role="status">
        <div class="alert alert-success">
          <div class="small mb-1">{{ result.message }}</div>
          임시 비밀번호: <strong class="user-select-all">{{ result.tempPassword }}</strong>
        </div>
        <RouterLink to="/login" class="btn btn-coral w-100">로그인하러 가기</RouterLink>
      </div>
      <form v-else novalidate @submit.prevent="submit">
        <label class="form-label small fw-semibold" for="fId">아이디</label>
        <input id="fId" v-model.trim="form.userId" class="form-control mb-3" autocomplete="username" />
        <label class="form-label small fw-semibold" for="fEmail">이메일</label>
        <input id="fEmail" v-model.trim="form.email" type="email" class="form-control mb-3" autocomplete="email" />
        <div v-if="error" class="alert alert-danger py-2 small" role="alert">{{ error }}</div>
        <button class="btn btn-coral w-100" :disabled="loading">임시 비밀번호 발급</button>
        <p class="small text-center mt-3 mb-0"><RouterLink to="/login">로그인으로 돌아가기</RouterLink></p>
      </form>
    </div>
  </AuthLayout>
</template>
