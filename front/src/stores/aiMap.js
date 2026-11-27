import { defineStore } from 'pinia'

/**
 * AI 길잡이가 만든 코스·추천 장소를 메인 지도에 보여 주기 위한 연결 고리.
 * 채팅창(ChatWidget)이 show() 로 넘기면 지도 탐색(ExploreView)이 받아 지도와 패널을 바꾼다.
 */
export const useAiMapStore = defineStore('aiMap', {
  state: () => ({
    course: null, // { title, days, stops: [{ no, title, type, address, latitude, longitude, dayNo, image?, summary? }], dayKm?, planId? }
    seq: 0, // 같은 코스를 다시 보여 달라고 해도 지도가 반응하도록 올리는 번호
    chatBox: null, // 열려 있는 채팅창의 위치와 크기 { x, y, w, h }. 지도가 채팅창에 가려진 폭을 피해 맞추는 데 쓴다
  }),
  actions: {
    show(course) {
      this.course = course
      this.seq++
    },
    clear() {
      this.course = null
    },
  },
})
