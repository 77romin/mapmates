import axios from 'axios'

export const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'
export const AI_SERVER = import.meta.env.VITE_AI_SERVER || 'http://localhost:8000'

const http = axios.create({ baseURL: API_BASE + '/api', timeout: 60000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('mm_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const status = err.response?.status
    if (status === 401 && localStorage.getItem('mm_token')) {
      // 만료된 토큰 정리
      localStorage.removeItem('mm_token')
      localStorage.removeItem('mm_member')
    }
    const message = err.response?.data?.message || (err.request ? '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' : err.message)
    return Promise.reject(Object.assign(new Error(message), { status }))
  },
)

export default http

/** 업로드 이미지 등 서버 상대 경로를 절대 URL 로 */
export const fileUrl = (path) => (path ? (path.startsWith('http') ? path : API_BASE + path) : '')
