import { onBeforeUnmount, ref } from 'vue'

/**
 * 넓은 화면에서 지도 위에 뜨는 패널이 가리는 폭(px). 좁은 화면에서는 0.
 * KakaoMap 의 padLeft 로 넘겨 범위 맞춤·중앙 정렬·영역 검색에서 그만큼을 뺀다.
 * @param {number} panelWidth 패널 폭. 좌우 여백 16px 씩이 더해진다.
 */
export function usePanelPad(panelWidth = 400) {
  const wide = window.matchMedia('(min-width: 768px)')
  const full = panelWidth + 32
  const pad = ref(wide.matches ? full : 0)
  const onChange = (e) => (pad.value = e.matches ? full : 0)
  wide.addEventListener('change', onChange)
  onBeforeUnmount(() => wide.removeEventListener('change', onChange))
  return pad
}
