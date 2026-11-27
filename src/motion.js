/**
 * 화면 모션 모음.
 * 아래 효과는 "모션 UI 메뉴판"(https://motion-ui.altolab.ai/)의 데모 코드를 바탕으로 이 프로젝트에 맞게 옮긴 것이다.
 *   M20 순차 등장        -> v-reveal
 *   M12 커서 조명 카드   -> .spot (커서 좌표를 CSS 변수 --x, --y 로 전달)
 *   M09 흐림에서 선명하게 -> v-blur-in
 *   M27 줄어드는 헤더    -> NavBar.vue 의 .small
 *   M18 밑줄 미끄러지기  -> sunset.css 의 .nav-link::after
 * 원칙: JS 는 클래스와 CSS 변수만 바꾸고 계산은 CSS 에 맡긴다. 터치 기기와 '움직임 줄이기' 설정에서는 끈다.
 */
const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
const touch = window.matchMedia('(max-width: 640px) and (pointer: coarse)').matches
const off = reduce || touch

// M20 순차 등장: 화면에 들어온 요소에 .in 을 붙이고, 함께 들어온 요소끼리는 순번만큼 지연을 준다.
const io = off
  ? null
  : new IntersectionObserver(
      (entries) => {
        let k = 0
        entries.forEach((e) => {
          if (!e.isIntersecting) return
          e.target.style.transitionDelay = Math.min(k++, 8) * 90 + 'ms'
          e.target.classList.add('in')
          io.unobserve(e.target)
          // 등장이 끝나면 지연을 없애 호버 효과가 늦게 반응하지 않게 한다
          setTimeout(() => (e.target.style.transitionDelay = ''), 1600)
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

// M09 흐림에서 선명하게: 문장을 단어로 나눠 차례로 선명해지게 한다.
export const blurIn = {
  mounted(el) {
    if (off) return
    const text = el.textContent
    el.setAttribute('aria-label', text)
    el.textContent = ''
    text.split(' ').forEach((word, i) => {
      const span = document.createElement('span')
      span.className = 'blur-word'
      span.style.transitionDelay = i * 120 + 'ms'
      span.textContent = word
      span.setAttribute('aria-hidden', 'true')
      el.append(span, ' ')
    })
    requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('blur-on')))
  },
}

// M12 커서 조명 카드: 모든 .spot 카드가 같은 커서 좌표를 자기 기준으로 받는다.
export function installSpotlight() {
  if (off) return
  let frame = 0
  window.addEventListener(
    'mousemove',
    (e) => {
      if (frame) return
      frame = requestAnimationFrame(() => {
        frame = 0
        document.querySelectorAll('.spot').forEach((c) => {
          const r = c.getBoundingClientRect()
          c.style.setProperty('--x', e.clientX - r.left + 'px')
          c.style.setProperty('--y', e.clientY - r.top + 'px')
        })
      })
    },
    { passive: true },
  )
}

export default {
  install(app) {
    app.directive('reveal', reveal)
    app.directive('blur-in', blurIn)
    installSpotlight()
  },
}
