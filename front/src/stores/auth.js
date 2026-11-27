import { defineStore } from 'pinia'
import http from '@/api/http'

const saved = () => {
  try {
    return JSON.parse(localStorage.getItem('mm_member'))
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({ member: localStorage.getItem('mm_token') ? saved() : null }),
  getters: {
    isLogin: (s) => !!s.member,
    isAdmin: (s) => s.member?.role === 'ADMIN',
  },
  actions: {
    async login(userId, password) {
      const { token, member } = await http.post('/auth/login', { userId, password })
      localStorage.setItem('mm_token', token)
      this.setMember(member)
    },
    setMember(member) {
      this.member = member
      localStorage.setItem('mm_member', JSON.stringify(member))
    },
    async logout() {
      try {
        await http.post('/auth/logout')
      } catch {
        /* 서버 응답과 무관하게 로컬 상태를 지운다 */
      }
      localStorage.removeItem('mm_token')
      localStorage.removeItem('mm_member')
      this.member = null
    },
  },
})
