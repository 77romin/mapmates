<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api/http'
import KakaoMap from '@/components/KakaoMap.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const form = reactive({ userId: '', name: '', email: '', tags: '', password: '', homeLat: null, homeLng: null })
const home = ref(null)
const message = ref(null)
const loading = ref(true)
const TAGS = ['바다', '등산', '맛집', '사진', '역사', '도시', '휴양']

onMounted(async () => {
  try {
    const me = await http.get('/members/me')
    Object.assign(form, { userId: me.userId, name: me.name, email: me.email, tags: me.tags || '', homeLat: me.homeLat, homeLng: me.homeLng })
    if (me.homeLat) home.value = { latitude: me.homeLat, longitude: me.homeLng }
  } catch (e) {
    message.value = { type: 'danger', text: e.message }
  } finally {
    loading.value = false
  }
})

function toggleTag(t) {
  const set = new Set(form.tags.split(',').filter(Boolean))
  set.has(t) ? set.delete(t) : set.size < 3 && set.add(t)
  form.tags = [...set].join(',')
}
function pick(pos) {
  home.value = pos
  form.homeLat = pos.latitude
  form.homeLng = pos.longitude
}
async function save() {
  message.value = null
  if (!form.name.trim()) return (message.value = { type: 'danger', text: '이름을 입력해 주세요.' })
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return (message.value = { type: 'danger', text: '이메일 형식이 올바르지 않습니다.' })
  if (form.password && form.password.length < 8) return (message.value = { type: 'danger', text: '새 비밀번호는 8자 이상이어야 합니다.' })
  try {
    const me = await http.put('/members/me', { name: form.name, email: form.email, tags: form.tags, homeLat: form.homeLat, homeLng: form.homeLng, password: form.password || null })
    auth.setMember(me)
    form.password = ''
    message.value = { type: 'success', text: '회원 정보를 저장했습니다.' }
  } catch (e) {
    message.value = { type: 'danger', text: e.message }
  }
}
async function withdraw() {
  if (!confirm('정말 탈퇴할까요? 내 플랜, 모집글, 게시글이 모두 삭제되며 되돌릴 수 없습니다.')) return
  try {
    await http.delete('/members/me')
    await auth.logout()
    router.replace('/')
  } catch (e) {
    message.value = { type: 'danger', text: e.message }
  }
}
</script>

<template>
  <div class="mm-page narrow">
    <h1 class="h3 mm-title mb-3">마이페이지</h1>
    <div v-if="loading" role="status" aria-busy="true" aria-label="내 정보를 불러오는 중"><div v-for="n in 5" :key="n" class="mm-skel mm-skel-line my-skel" aria-hidden="true"></div></div>
    <form v-else novalidate @submit.prevent="save">
      <h2 class="my-h">기본 정보</h2>
      <div class="row g-3">
        <div class="col-md-6"><label class="form-label small fw-semibold" for="myId">아이디</label><input id="myId" :value="form.userId" class="form-control" disabled /></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="myName">이름</label><input id="myName" v-model="form.name" class="form-control" maxlength="30" /></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="myEmail">이메일</label><input id="myEmail" v-model.trim="form.email" type="email" class="form-control" /></div>
        <div class="col-md-6"><label class="form-label small fw-semibold" for="myPw">새 비밀번호 <span class="fw-normal text-secondary">(변경할 때만)</span></label><input id="myPw" v-model="form.password" type="password" class="form-control" autocomplete="new-password" /></div>
        <div class="col-12">
          <h2 class="my-h">여행 스타일</h2>
          <p class="mm-item-meta mb-2">최대 3개까지 고를 수 있어요. 잘 맞는 메이트 모집글을 먼저 보여 드립니다.</p>
          <div class="d-flex flex-wrap gap-2">
            <button v-for="t in TAGS" :key="t" type="button" class="btn btn-sm rounded-pill" :class="form.tags.split(',').includes(t) ? 'bg-ink' : 'btn-light border'" :aria-pressed="form.tags.split(',').includes(t)" @click="toggleTag(t)">#{{ t }}</button>
          </div>
        </div>
        <div class="col-12">
          <h2 class="my-h">내 출발지</h2>
          <p class="mm-item-meta mb-2">지도를 눌러 정하면 메이트와 만날 중간 집결지를 계산할 때 씁니다.</p>
          <div class="home-map"><KakaoMap :markers="home ? [{ id: 'home', ...home, title: '내 출발지', kind: 'hot' }] : []" clickable @map-click="pick" /></div>
          <div class="small text-secondary mt-1">{{ home ? `선택한 위치: ${home.latitude.toFixed(4)}, ${home.longitude.toFixed(4)}` : '아직 출발지를 설정하지 않았습니다.' }}</div>
        </div>
      </div>
      <div v-if="message" :class="['alert py-2 small mt-3 mb-0', 'alert-' + message.type]" role="status">{{ message.text }}</div>
      <div class="d-flex justify-content-between align-items-center my-foot">
        <button type="button" class="btn btn-link text-danger text-decoration-none px-0" @click="withdraw">회원 탈퇴</button>
        <button class="btn btn-coral px-4">저장</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.narrow { max-width: 760px; }
.my-h { font-size: var(--mm-fs-lg); font-weight: 700; letter-spacing: -0.02em; color: var(--mm-ink); margin: var(--mm-sp-6) 0 var(--mm-sp-3); padding-top: var(--mm-sp-5); border-top: 1px solid var(--mm-line); }
form > .my-h:first-child { margin-top: 0; padding-top: 0; border-top: 0; }
.my-skel { height: 40px; margin-bottom: var(--mm-sp-4); }
.my-foot { margin-top: var(--mm-sp-6); padding-top: var(--mm-sp-5); border-top: 1px solid var(--mm-line); }
.home-map { position: relative; height: 260px; border-radius: var(--mm-r-md); overflow: hidden; border: 1px solid var(--mm-line); }
</style>
