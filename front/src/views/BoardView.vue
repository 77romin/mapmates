<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const type = ref('NOTICE')
const mode = ref('list') // list | detail | form
const items = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const post = ref(null)
const form = reactive({ id: null, title: '', content: '' })
const formError = ref('')
const comment = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const canWrite = computed(() => auth.isLogin && (type.value === 'FREE' || auth.isAdmin))
const canEdit = computed(() => post.value && (auth.member?.id === post.value.memberId || auth.isAdmin))

async function load(p = 1) {
  loading.value = true
  error.value = ''
  try {
    const res = await http.get('/boards', { params: { type: type.value, keyword: keyword.value || undefined, page: p, size } })
    items.value = res.items
    total.value = res.total
    page.value = p
    mode.value = 'list'
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(() => load())
watch(type, () => {
  keyword.value = ''
  load(1)
})

async function open(id) {
  try {
    post.value = await http.get(`/boards/${id}`)
    mode.value = 'detail'
    comment.value = ''
  } catch (e) {
    alert(e.message)
  }
}
function write(edit = false) {
  if (!auth.isLogin) return router.push({ name: 'login', query: { redirect: '/board' } })
  Object.assign(form, edit ? { id: post.value.id, title: post.value.title, content: post.value.content } : { id: null, title: '', content: '' })
  formError.value = ''
  mode.value = 'form'
}
async function submit() {
  if (!form.title.trim() || !form.content.trim()) return (formError.value = '제목과 내용을 모두 입력해 주세요.')
  try {
    const saved = form.id
      ? await http.put(`/boards/${form.id}`, { title: form.title, content: form.content })
      : await http.post('/boards', { type: type.value, title: form.title, content: form.content })
    await open(saved.id)
  } catch (e) {
    formError.value = e.message
  }
}
async function remove() {
  if (!confirm('게시글을 삭제할까요?')) return
  try {
    await http.delete(`/boards/${post.value.id}`)
    load(page.value)
  } catch (e) {
    alert(e.message)
  }
}
async function addComment() {
  if (!comment.value.trim()) return
  try {
    post.value = await http.post(`/boards/${post.value.id}/comments`, { content: comment.value })
    comment.value = ''
  } catch (e) {
    alert(e.message)
  }
}
async function removeComment(c) {
  if (!confirm('댓글을 삭제할까요?')) return
  try {
    await http.delete(`/boards/comments/${c.id}`)
    post.value.comments = post.value.comments.filter((x) => x.id !== c.id)
  } catch (e) {
    alert(e.message)
  }
}
</script>

<template>
  <div class="mm-page">
    <h1 class="h3 mm-title mb-3">커뮤니티</h1>
    <ul class="nav nav-pills mb-3" role="tablist">
      <li class="nav-item"><button class="nav-link" :class="{ active: type === 'NOTICE' }" @click="type = 'NOTICE'">공지사항</button></li>
      <li class="nav-item"><button class="nav-link" :class="{ active: type === 'FREE' }" @click="type = 'FREE'">공유 게시판</button></li>
    </ul>

    <!-- 목록 -->
    <div v-if="mode === 'list'" class="mm-card">
      <div class="p-3 d-flex gap-2 border-bottom">
        <form class="input-group input-group-sm search" @submit.prevent="load(1)">
          <input v-model.trim="keyword" class="form-control" placeholder="제목·내용 검색" aria-label="검색어" />
          <button class="btn btn-light border"><i class="bi bi-search" aria-hidden="true"></i></button>
        </form>
        <button v-if="canWrite || (type === 'FREE' && !auth.isLogin)" class="btn btn-sm btn-coral ms-auto flex-shrink-0" @click="write()"><i class="bi bi-pencil me-1" aria-hidden="true"></i>글쓰기</button>
      </div>
      <div v-if="loading" class="mm-empty"><div class="spinner-border text-secondary" role="status"></div></div>
      <div v-else-if="error" class="mm-empty"><i class="bi bi-wifi-off"></i>{{ error }}</div>
      <div v-else-if="!items.length" class="mm-empty"><i class="bi bi-chat-square-text"></i>{{ keyword ? '검색 결과가 없습니다.' : '아직 게시글이 없습니다.' }}</div>
      <div v-else class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead class="small text-secondary"><tr><th scope="col" class="ps-3 num">번호</th><th scope="col">제목</th><th scope="col" class="d-none d-md-table-cell">작성자</th><th scope="col" class="d-none d-md-table-cell">작성일</th><th scope="col" class="num">조회</th></tr></thead>
          <tbody>
            <tr v-for="b in items" :key="b.id" role="button" tabindex="0" @click="open(b.id)" @keyup.enter="open(b.id)">
              <td class="ps-3 text-secondary">{{ b.id }}</td>
              <td><span class="fw-semibold">{{ b.title }}</span> <span v-if="b.commentCount" class="small text-coral">[{{ b.commentCount }}]</span></td>
              <td class="d-none d-md-table-cell small">{{ b.writerName }}</td>
              <td class="d-none d-md-table-cell small text-secondary">{{ b.createdAt?.slice(0, 10) }}</td>
              <td class="small text-secondary">{{ b.hit }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <nav v-if="totalPages > 1" class="d-flex justify-content-center align-items-center gap-2 p-3" aria-label="페이지">
        <button class="btn btn-sm btn-light" :disabled="page <= 1" @click="load(page - 1)">이전</button>
        <span class="small">{{ page }} / {{ totalPages }}</span>
        <button class="btn btn-sm btn-light" :disabled="page >= totalPages" @click="load(page + 1)">다음</button>
      </nav>
    </div>

    <!-- 상세 -->
    <article v-else-if="mode === 'detail'" class="mm-card p-4">
      <button class="btn btn-sm btn-light mb-3" @click="load(page)"><i class="bi bi-arrow-left" aria-hidden="true"></i> 목록</button>
      <h2 class="h4 mm-title">{{ post.title }}</h2>
      <p class="small text-secondary border-bottom pb-3">{{ post.writerName }} · {{ post.createdAt }} · 조회 {{ post.hit }}</p>
      <div class="pre-line mb-4 content">{{ post.content }}</div>
      <div v-if="canEdit" class="d-flex gap-2 mb-4">
        <button class="btn btn-sm btn-outline-coral" @click="write(true)">수정</button>
        <button class="btn btn-sm btn-light text-danger" @click="remove">삭제</button>
      </div>
      <h3 class="h6 fw-bold">댓글 {{ post.comments?.length || 0 }}</h3>
      <div v-for="c in post.comments" :key="c.id" class="border-top py-2 d-flex gap-2">
        <div class="flex-grow-1"><div class="small"><strong>{{ c.writerName }}</strong> <span class="text-secondary">{{ c.createdAt }}</span></div><div class="pre-line">{{ c.content }}</div></div>
        <button v-if="auth.member?.id === c.memberId || auth.isAdmin" class="btn btn-sm btn-light align-self-start" aria-label="댓글 삭제" @click="removeComment(c)"><i class="bi bi-x-lg" aria-hidden="true"></i></button>
      </div>
      <form v-if="auth.isLogin" class="input-group mt-2" @submit.prevent="addComment">
        <input v-model="comment" class="form-control" maxlength="1000" placeholder="댓글을 남겨 보세요" aria-label="댓글 입력" />
        <button class="btn btn-coral" :disabled="!comment.trim()">등록</button>
      </form>
      <p v-else class="small text-secondary mt-2 mb-0"><RouterLink :to="{ name: 'login', query: { redirect: '/board' } }">로그인</RouterLink>하면 댓글을 쓸 수 있습니다.</p>
    </article>

    <!-- 작성/수정 -->
    <form v-else class="mm-card p-4" novalidate @submit.prevent="submit">
      <h2 class="h5 mm-title mb-3">{{ form.id ? '게시글 수정' : type === 'NOTICE' ? '공지 작성' : '글쓰기' }}</h2>
      <label class="form-label small fw-semibold" for="bTitle">제목</label>
      <input id="bTitle" v-model="form.title" class="form-control mb-3" maxlength="200" />
      <label class="form-label small fw-semibold" for="bContent">내용</label>
      <textarea id="bContent" v-model="form.content" class="form-control mb-3" rows="10"></textarea>
      <div v-if="formError" class="alert alert-danger py-2 small" role="alert">{{ formError }}</div>
      <div class="d-flex gap-2 justify-content-end">
        <button type="button" class="btn btn-light" @click="form.id ? (mode = 'detail') : load(page)">취소</button>
        <button class="btn btn-coral">{{ form.id ? '수정 완료' : '등록' }}</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.search { max-width: 320px; }
.num { width: 70px; }
.content { min-height: 120px; line-height: 1.7; }
</style>
