/**
 * 화면 모션. 순차 등장(v-reveal) 하나만 둔다.
 * 줄어드는 헤더와 메뉴 밑줄은 CSS(motion.css)와 NavBar.vue 가 맡는다.
 * 원칙: JS 는 클래스만 바꾸고 계산은 CSS 에 맡긴다. 터치 기기와 '움직임 줄이기' 설정에서는 끈다.
 */
const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const touch = window.matchMedia('(max-width: 640px) and (pointer: coarse)').matches
const off = reduce || touch

// 화면에 들어온 요소에 .in 을 붙이고, 함께 들어온 요소끼리는 순번만큼 지연을 준다.
const io = off
  ? null
  : new IntersectionObserver(
      (entries) => {
        let k = 0
        entries.forEach((e) => {
          if (!e.isIntersecting) return
          e.target.style.transitionDelay = Math.min(k++, 6) * 60 + 'ms'
          e.target.classList.add('in')
          io.unobserve(e.target)
          // 등장이 끝나면 지연을 없애 호버 효과가 늦게 반응하지 않게 한다
          setTimeout(() => (e.target.style.transitionDelay = ''), 1000)
        })
      },
      { threshold: 0.12 },
    )

export const reveal = {
  mounted(el) {
    if (off) return
    el.classList.add('reveal')
    io.observe(el)
  },
  unmounted(el) {
    io?.unobserve(el)
  },
}

export default {
  install(app) {
    app.directive('reveal', reveal)
  },
}
