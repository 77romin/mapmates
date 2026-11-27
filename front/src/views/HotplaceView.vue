<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http, { fileUrl } from '@/api/http'
import KakaoMap from '@/components/KakaoMap.vue'
import { usePanelPad } from '@/utils/panelPad'
import { useAuthStore } from '@/stores/auth'

const padLeft = usePanelPad(400) // 넓은 화면에서 패널이 지도를 가리는 폭
const auth = useAuthStore()
const router = useRouter()
const list = ref([])
const loading = ref(true)
const error = ref('')
const selected = ref(null)
const adding = ref(false)
const form = reactive({ title: '', description: '', placeType: '카페', visitedDate: '', image: null })
const picked = ref(null)
const formError = ref('')
const saving = ref(false)
const preview = ref('')
const TYPES = ['카페', '맛집', '자연', '전망', '문화', '기타']
const today = new Date().toISOString().slice(0, 10)

const markers = computed(() => list.value.map((h) => ({ id: h.id, latitude: h.latitude, longitude: h.longitude, title: h.title, kind: 'hot' })))

async function load() {
  loading.value = true
  try {
    list.value = await http.get('/hotplaces')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

function startAdd() {
  if (!auth.isLogin) return router.push({ name: 'login', query: { redirect: '/hotplaces' } })
  adding.value = true
  picked.value = null
  formError.value = ''
}
function onFile(e) {
  const file = e.target.files[0]
  if (file && file.size > 10 * 1024 * 1024) {
    formError.value = '사진은 10MB 이하만 올릴 수 있습니다.'
    e.target.value = ''
    return
  }
  form.image = file || null
  preview.value = file ? URL.createObjectURL(file) : ''
}
async function submit() {
  if (!form.title.trim()) return (formError.value = '장소 이름을 입력해 주세요.')
  if (!picked.value) return (formError.value = '지도를 클릭해 위치를 선택해 주세요.')
  saving.value = true
  try {
    const data = new FormData()
    data.append('title', form.title)
    data.append('description', form.description)
    data.append('placeType', form.placeType)
    data.append('visitedDate', form.visitedDate)
    data.append('latitude', picked.value.latitude)
    data.append('longitude', picked.value.longitude)
    if (form.image) data.append('image', form.image)
    const created = await http.post('/hotplaces', data)
    list.value.unshift(created)
    adding.value = false
    picked.value = null
    preview.value = ''
    Object.assign(form, { title: '', description: '', placeType: '카페', visitedDate: '', image: null })
    selected.value = created.id
  } catch (e) {
    formError.value = e.message
  } finally {
    saving.value = false
  }
}
async function remove(h) {
  if (!confirm(`'${h.title}' 핫플레이스를 삭제할까요?`)) return
  try {
    await http.delete(`/hotplaces/${h.id}`)
    list.value = list.value.filter((x) => x.id !== h.id)
  } catch (e) {
    alert(e.message)
  }
}
</script>

<template>
  <div class="mm-split mm-split--float">
    <aside class="mm-panel">
      <div class="p-3 border-bottom d-flex justify-content-between align-items-center">
        <h1 class="h5 mm-title mb-0">{{ adding ? '핫플 등록' : '우리의 핫플레이스' }}</h1>
        <button v-if="!adding" class="btn btn-sm btn-coral" @click="startAdd"><i class="bi bi-plus-lg me-1" aria-hidden="true"></i>등록</button>
        <button v-else class="btn btn-sm btn-light" @click="adding = false; picked = null">취소</button>
      </div>

      <form v-if="adding" class="mm-panel-body p-3" novalidate @submit.prevent="submit">
        <div class="alert py-2 small" :class="picked ? 'alert-success' : 'alert-warning'">
          <i class="bi bi-geo-alt-fill me-1" aria-hidden="true"></i>{{ picked ? `위치 선택됨 (${picked.latitude.toFixed(4)}, ${picked.longitude.toFixed(4)})` : '오른쪽 지도를 클릭해 위치를 선택하세요.' }}
        </div>
        <label class="form-label small fw-semibold" for="hTitle">장소 이름</label>
        <input id="hTitle" v-model="form.title" class="form-control mb-2" maxlength="100" placeholder="나만 아는 그곳" />
        <div class="row g-2 mb-2">
          <div class="col-6"><label class="form-label small fw-semibold" for="hType">장소 유형</label><select id="hType" v-model="form.placeType" class="form-select"><option v-for="t in TYPES" :key="t">{{ t }}</option></select></div>
          <div class="col-6"><label class="form-label small fw-semibold" for="hDate">다녀온 날짜</label><input id="hDate" v-model="form.visitedDate" type="date" class="form-control" :max="today" /></div>
        </div>
        <label class="form-label small fw-semibold" for="hImg">사진</label>
        <input id="hImg" type="file" accept="image/*" class="form-control mb-2" @change="onFile" />
        <img v-if="preview" :src="preview" class="w-100 rounded mb-2 hot-preview" alt="선택한 사진 미리보기" />
        <label class="form-label small fw-semibold" for="hDesc">상세 설명</label>
        <textarea id="hDesc" v-model="form.description" class="form-control mb-3" rows="3" maxlength="2000" placeholder="어떤 점이 좋았나요?"></textarea>
        <div v-if="formError" class="alert alert-danger py-2 small" role="alert">{{ formError }}</div>
        <button class="btn btn-coral w-100" :disabled="saving"><span v-if="saving" class="spinner-border spinner-border-sm me-1"></span>등록하기</button>
      </form>

      <div v-else class="mm-panel-body">
        <div v-if="loading" role="status" aria-busy="true" aria-label="핫플레이스를 불러오는 중"><div v-for="n in 4" :key="n" class="mm-item" aria-hidden="true"><div class="mm-thumb mm-skel"></div><div class="flex-grow-1 align-self-center"><div class="mm-skel mm-skel-line w-75"></div><div class="mm-skel mm-skel-line w-50"></div></div></div></div>
        <div v-else-if="error" class="mm-empty"><i class="bi bi-wifi-off"></i>{{ error }}</div>
        <div v-else-if="!list.length" class="mm-empty"><i class="bi bi-camera"></i>아직 등록된 핫플레이스가 없습니다.<br />다녀온 곳의 사진을 첫 번째로 남겨 보세요.</div>
        <div v-for="h in list" :key="h.id" class="mm-item" :class="{ active: selected === h.id }" role="button" tabindex="0" @click="selected = h.id" @keyup.enter="selected = h.id">
          <img v-if="h.imagePath" :src="fileUrl(h.imagePath)" class="mm-thumb" alt="" loading="lazy" />
          <div v-else class="mm-thumb"><i class="bi bi-camera" aria-hidden="true"></i></div>
          <div class="flex-grow-1 min-w-0 align-self-center">
            <div class="mm-item-title text-truncate">{{ h.title }}</div>
            <div class="mm-item-kind">{{ h.placeType }}</div>
            <div class="mm-item-meta">{{ h.writerName }}<span v-if="h.visitedDate">, {{ h.visitedDate }} 방문</span></div>
            <div v-if="selected === h.id" class="hot-desc pre-line">{{ h.description }}</div>
          </div>
          <button v-if="auth.member?.id === h.memberId || auth.isAdmin" class="btn btn-sm btn-light align-self-start" aria-label="삭제" @click.stop="remove(h)"><i class="bi bi-trash" aria-hidden="true"></i></button>
        </div>
      </div>
    </aside>
    <div class="mm-map">
      <KakaoMap :markers="markers" :selected-id="selected" :clickable="adding" :picked="picked" :pad-left="padLeft" @marker-click="selected = $event.id" @map-click="picked = $event" />
    </div>
  </div>
</template>

<style scoped>
.hot-preview { max-height: 160px; object-fit: cover; }
.hot-desc { font-size: var(--mm-fs-sm); color: var(--mm-text); margin-top: var(--mm-sp-2); }
</style>
