<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const posts = ref([])
const loading = ref(true)
const error = ref('')
const detail = ref(null) // { post, items, members, applications }
const showForm = ref(false)
const myPlans = ref([])
const form = reactive({ planId: '', title: '', content: '', capacity: 4, tags: '' })
const formError = ref('')
const applyMessage = ref('')
const TAGS = ['바다', '등산', '맛집', '사진', '역사', '도시', '휴양']

async function load() {
  loading.value = true
  error.value = ''
  try {
    posts.value = await http.get('/mates')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function open(id) {
  try {
    detail.value = await http.get(`/mates/${id}`)
    applyMessage.value = ''
  } catch (e) {
    alert(e.message)
  }
}

async function openForm() {
  if (!auth.isLogin) return router.push({ name: 'login', query: { redirect: '/mates' } })
  myPlans.value = await http.get('/plans').catch(() => [])
  formError.value = ''
  showForm.value = true
}
function toggleTag(t) {
  const set = new Set(form.tags.split(',').filter(Boolean))
  set.has(t) ? set.delete(t) : set.size < 3 && set.add(t)
  form.tags = [...set].join(',')
}
async function submit() {
  if (!form.planId) return (formError.value = '모집할 플랜을 선택해 주세요.')
  if (!form.title.trim()) return (formError.value = '제목을 입력해 주세요.')
  if (form.capacity < 2 || form.capacity > 10) return (formError.value = '정원은 2~10명으로 입력해 주세요.')
  try {
    await http.post('/mates', { ...form })
    showForm.value = false
    Object.assign(form, { planId: '', title: '', content: '', capacity: 4, tags: '' })
    load()
  } catch (e) {
    formError.value = e.message
  }
}

async function apply() {
  if (!auth.isLogin) return router.push({ name: 'login', query: { redirect: '/mates' } })
  try {
    await http.post(`/mates/${detail.value.post.id}/apply`, { message: applyMessage.value })
    await open(detail.value.post.id)
    load()
  } catch (e) {
    alert(e.message)
  }
}
async function decide(app, decision) {
  try {
    await http.post(`/mates/applications/${app.id}/${decision}`)
    await open(detail.value.post.id)
    load()
  } catch (e) {
    alert(e.message)
  }
}
async function remove() {
  if (!confirm('모집글을 삭제할까요?')) return
  await http.delete(`/mates/${detail.value.post.id}`).catch((e) => alert(e.message))
  detail.value = null
  load()
}
const STATUS = { PENDING: '수락 대기 중', ACCEPTED: '참여 확정', REJECTED: '거절됨' }
</script>

<template>
  <div class="mm-page">
    <div class="d-flex justify-content-between align-items-center mb-1">
      <h1 class="h3 mm-title mb-0">메이트 찾기</h1>
      <button class="btn btn-coral" @click="openForm"><i class="bi bi-megaphone me-1" aria-hidden="true"></i>메이트 모집하기</button>
    </div>
    <p class="text-secondary">같은 길을 갈 동행을 만나 보세요. <span v-if="auth.isLogin">내 취향 태그와 잘 맞는 모집글이 위에 보입니다.</span></p>

    <div v-if="loading" class="mm-empty"><div class="spinner-border text-secondary" role="status"></div></div>
    <div v-else-if="error" class="mm-empty"><i class="bi bi-wifi-off"></i>{{ error }}<br /><button class="btn btn-sm btn-outline-coral mt-2" @click="load">다시 시도</button></div>
    <div v-else-if="!posts.length" class="mm-card mm-empty"><i class="bi bi-people"></i>아직 모집 중인 여행이 없습니다.<br />내 플랜으로 첫 모집글을 올려 보세요.</div>
    <div v-else class="row g-3">
      <div v-for="p in posts" :key="p.id" class="col-md-6 col-lg-4">
        <button class="mm-card p-3 h-100 w-100 text-start mate-card" @click="open(p.id)">
          <div class="d-flex justify-content-between mb-2">
            <span class="badge" :class="p.status === 'OPEN' ? 'badge-soft' : 'bg-secondary'">{{ p.status === 'OPEN' ? '모집 중' : '마감' }}</span>
            <span v-if="p.matchRate != null" class="small fw-bold text-coral"><i class="bi bi-heart-fill" aria-hidden="true"></i> 취향 일치 {{ p.matchRate }}%</span>
          </div>
          <h2 class="h5 mm-title mb-1">{{ p.title }}</h2>
          <p class="small text-secondary mb-2"><i class="bi bi-signpost-2" aria-hidden="true"></i> {{ p.planTitle }}<br /><i class="bi bi-calendar3" aria-hidden="true"></i> {{ p.startDate || '날짜 미정' }}<span v-if="p.endDate"> ~ {{ p.endDate }}</span></p>
          <div class="mb-2"><span v-for="t in (p.tags || '').split(',').filter(Boolean)" :key="t" class="badge badge-ink me-1">#{{ t }}</span></div>
          <div class="small d-flex justify-content-between"><span>{{ p.writerName }}</span><span><i class="bi bi-person-fill" aria-hidden="true"></i> {{ p.joinedCount }} / {{ p.capacity }}명</span></div>
          <div v-if="p.myStatus" class="small text-coral mt-1">{{ STATUS[p.myStatus] }}</div>
        </button>
      </div>
    </div>

    <!-- 상세 -->
    <div v-if="detail" class="mm-modal" role="dialog" aria-modal="true" aria-label="모집글 상세" @click.self="detail = null">
      <div class="mm-modal-body">
        <div class="d-flex justify-content-between align-items-start">
          <h2 class="h4 mm-title">{{ detail.post.title }}</h2>
          <button class="btn-close" aria-label="닫기" @click="detail = null"></button>
        </div>
        <p class="small text-secondary">{{ detail.post.writerName }} · {{ detail.post.startDate || '날짜 미정' }}<span v-if="detail.post.endDate"> ~ {{ detail.post.endDate }}</span> · {{ detail.post.joinedCount }}/{{ detail.post.capacity }}명</p>
        <p class="pre-line">{{ detail.post.content || '소개 글이 없습니다.' }}</p>
        <h3 class="h6 fw-bold">여행 경로</h3>
        <ol v-if="detail.items.length" class="small ps-3">
          <li v-for="i in detail.items" :key="i.id">{{ i.dayNo }}일차 · {{ i.title }}</li>
        </ol>
        <p v-else class="small text-secondary">아직 방문지가 없습니다.</p>
        <h3 class="h6 fw-bold">참여 메이트</h3>
        <p><span v-for="m in detail.members" :key="m.id" class="badge badge-ink me-1">{{ m.name }}</span></p>

        <template v-if="auth.member?.id === detail.post.memberId">
          <h3 class="h6 fw-bold">참여 신청</h3>
          <p v-if="!detail.applications?.length" class="small text-secondary">아직 신청이 없습니다.</p>
          <div v-for="a in detail.applications" :key="a.id" class="mm-card p-2 mb-2 d-flex align-items-center gap-2">
            <div class="flex-grow-1 small"><strong>{{ a.memberName }}</strong> <span class="text-secondary">{{ a.memberTags }}</span><div>{{ a.message }}</div></div>
            <template v-if="a.status === 'PENDING'">
              <button class="btn btn-sm btn-coral" @click="decide(a, 'accept')">수락</button>
              <button class="btn btn-sm btn-light" @click="decide(a, 'reject')">거절</button>
            </template>
            <span v-else class="badge badge-ink">{{ STATUS[a.status] }}</span>
          </div>
          <div class="d-flex gap-2 mt-3">
            <RouterLink :to="`/plans/${detail.post.planId}`" class="btn btn-outline-coral">플랜 편집</RouterLink>
            <button class="btn btn-light text-danger" @click="remove">모집글 삭제</button>
          </div>
        </template>
        <template v-else>
          <div v-if="detail.post.myStatus" class="alert alert-light border mb-0">
            신청 상태: <strong>{{ STATUS[detail.post.myStatus] }}</strong>
            <RouterLink v-if="detail.post.myStatus === 'ACCEPTED'" :to="`/plans/${detail.post.planId}`" class="btn btn-sm btn-coral ms-2">함께 플랜 편집하기</RouterLink>
          </div>
          <form v-else-if="detail.post.status === 'OPEN'" @submit.prevent="apply">
            <label class="form-label small fw-semibold" for="applyMsg">모집자에게 한마디</label>
            <textarea id="applyMsg" v-model="applyMessage" class="form-control mb-2" rows="2" maxlength="500" placeholder="간단한 자기소개를 남겨 주세요"></textarea>
            <button class="btn btn-coral w-100">참여 신청하기</button>
          </form>
          <div v-else class="alert alert-secondary mb-0">모집이 마감되었습니다.</div>
        </template>
      </div>
    </div>

    <!-- 모집 작성 -->
    <div v-if="showForm" class="mm-modal" role="dialog" aria-modal="true" aria-label="메이트 모집하기" @click.self="showForm = false">
      <form class="mm-modal-body" novalidate @submit.prevent="submit">
        <div class="d-flex justify-content-between"><h2 class="h4 mm-title">메이트 모집하기</h2><button type="button" class="btn-close" aria-label="닫기" @click="showForm = false"></button></div>
        <div v-if="!myPlans.length" class="alert alert-light border">먼저 플랜을 만들어야 모집할 수 있습니다. <RouterLink to="/plans/new">플랜 만들기</RouterLink></div>
        <template v-else>
          <label class="form-label small fw-semibold" for="mPlan">함께 갈 플랜</label>
          <select id="mPlan" v-model="form.planId" class="form-select mb-2"><option value="" disabled>플랜 선택</option><option v-for="p in myPlans" :key="p.id" :value="p.id">{{ p.title }}</option></select>
          <label class="form-label small fw-semibold" for="mTitle">제목</label>
          <input id="mTitle" v-model="form.title" class="form-control mb-2" maxlength="100" placeholder="예) 부산 맛집 투어 같이 가요" />
          <label class="form-label small fw-semibold" for="mContent">소개</label>
          <textarea id="mContent" v-model="form.content" class="form-control mb-2" rows="3" maxlength="2000" placeholder="여행 스타일, 경비 분담 방식 등을 적어 주세요"></textarea>
          <label class="form-label small fw-semibold" for="mCap">정원(나 포함)</label>
          <input id="mCap" v-model.number="form.capacity" type="number" min="2" max="10" class="form-control mb-2" />
          <div class="form-label small fw-semibold">여행 스타일 (최대 3개)</div>
          <div class="d-flex flex-wrap gap-1 mb-3">
            <button v-for="t in TAGS" :key="t" type="button" class="btn btn-sm rounded-pill" :class="form.tags.split(',').includes(t) ? 'bg-ink' : 'btn-light'" :aria-pressed="form.tags.split(',').includes(t)" @click="toggleTag(t)">#{{ t }}</button>
          </div>
          <div v-if="formError" class="alert alert-danger py-2 small" role="alert">{{ formError }}</div>
          <button class="btn btn-coral w-100">모집글 올리기</button>
        </template>
      </form>
    </div>
  </div>
</template>

<style scoped>
.mate-card { transition: transform 0.15s, box-shadow 0.15s; }
.mate-card:hover { transform: translateY(-2px); box-shadow: 0 6px 18px rgba(31, 42, 68, 0.1); }
</style>
