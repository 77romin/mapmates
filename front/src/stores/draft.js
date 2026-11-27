import { defineStore } from 'pinia'

const KEY = 'mm_draft'

/** 탐색 중 담아 둔 장소(작성 중인 플랜). 로그인 전에도 쓸 수 있게 로컬 스토리지에 보관한다. */
export const useDraftStore = defineStore('draft', {
  state: () => {
    try {
      return { items: JSON.parse(localStorage.getItem(KEY)) || [] }
    } catch {
      return { items: [] }
    }
  },
  getters: {
    count: (s) => s.items.length,
    has: (s) => (no) => s.items.some((i) => i.attractionNo === no),
  },
  actions: {
    persist() {
      localStorage.setItem(KEY, JSON.stringify(this.items))
    },
    add(a) {
      const no = a.no ?? a.attractionNo
      if (this.has(no)) return false
      this.items.push({
        attractionNo: no, title: a.title, addr1: a.addr1 ?? a.address ?? '', firstImage1: a.firstImage1 ?? '',
        latitude: a.latitude, longitude: a.longitude, contentTypeName: a.contentTypeName ?? a.type ?? '', dayNo: 1, cost: 0, memo: '',
      })
      this.persist()
      return true
    },
    /** AI 가 짠 코스처럼 일차가 정해진 장소 묶음을 일차를 유지한 채 담는다. 이미 담긴 장소는 일차만 맞춘다. */
    addCourse(stops) {
      for (const s of stops) {
        const found = this.items.find((i) => i.attractionNo === s.no)
        if (found) found.dayNo = s.dayNo
        else
          this.items.push({
            attractionNo: s.no, title: s.title, addr1: s.address ?? '', firstImage1: '',
            latitude: s.latitude, longitude: s.longitude, contentTypeName: s.type ?? '', dayNo: s.dayNo || 1, cost: 0, memo: '',
          })
      }
      this.persist()
    },
    remove(no) {
      this.items = this.items.filter((i) => i.attractionNo !== no)
      this.persist()
    },
    clear() {
      this.items = []
      this.persist()
    },
  },
})
