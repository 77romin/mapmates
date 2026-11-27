<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * markers: [{ id, latitude, longitude, title, label?(번호 핀), kind?('hot'|'meet') }]
 * path: true 면 markers 순서대로 경로선을 그린다.
 */
const props = defineProps({
  markers: { type: Array, default: () => [] },
  path: { type: Boolean, default: false },
  selectedId: { type: [Number, String], default: null },
  clickable: { type: Boolean, default: false },
  picked: { type: Object, default: null },
})
const emit = defineEmits(['marker-click', 'map-click'])

const el = ref(null)
const error = ref('')
let map = null
let overlays = []
let polyline = null
let pickedMarker = null
let resizeObserver = null
const KEY = import.meta.env.VITE_KAKAO_MAP_KEY

function loadSdk() {
  return new Promise((resolve, reject) => {
    if (!KEY) return reject(new Error('카카오 지도 키(VITE_KAKAO_MAP_KEY)가 설정되지 않았습니다.'))
    if (window.kakao?.maps?.LatLng) return resolve()
    const done = () => (window.kakao?.maps ? window.kakao.maps.load(resolve) : reject(new Error('지도를 불러오지 못했습니다.')))
    const existing = document.getElementById('kakao-sdk')
    if (existing) {
      existing.addEventListener('load', done)
      existing.addEventListener('error', () => reject(new Error('지도를 불러오지 못했습니다.')))
      if (window.kakao?.maps) done()
      return
    }
    const s = document.createElement('script')
    s.id = 'kakao-sdk'
    s.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(KEY)}&autoload=false`
    s.onload = done
    s.onerror = () => reject(new Error('지도를 불러오지 못했습니다. 카카오 개발자 콘솔의 도메인 등록을 확인해 주세요.'))
    document.head.appendChild(s)
  })
}

function pinEl(m) {
  const b = document.createElement('button')
  b.type = 'button'
  b.className = 'mm-pin' + (m.kind ? ' ' + m.kind : '') + (m.id === props.selectedId ? ' selected' : '')
  b.title = m.title || ''
  b.setAttribute('aria-label', m.title || '장소')
  if (m.label != null) b.textContent = m.label
  else b.innerHTML = '<i class="bi bi-geo-alt-fill"></i>'
  b.addEventListener('click', (e) => {
    e.stopPropagation()
    emit('marker-click', m)
  })
  return b
}

function draw(fit = true) {
  if (!map) return
  const { maps } = window.kakao
  map.relayout() // 컨테이너 크기가 바뀐 뒤에도 범위 계산이 맞도록 한다
  overlays.forEach((o) => o.setMap(null))
  overlays = []
  polyline?.setMap(null)
  polyline = null
  // 원본 데이터에 국내 범위를 벗어난 좌표가 일부 있어, 지도 범위 계산에서 제외한다
  const valid = props.markers.filter((m) => m.latitude > 32.5 && m.latitude < 39.5 && m.longitude > 123.5 && m.longitude < 132.5)
  const bounds = new maps.LatLngBounds()
  const points = valid.map((m) => {
    const pos = new maps.LatLng(m.latitude, m.longitude)
    const overlay = new maps.CustomOverlay({ position: pos, content: pinEl(m), yAnchor: 1, zIndex: m.id === props.selectedId ? 10 : 1 })
    overlay.setMap(map)
    overlays.push(overlay)
    bounds.extend(pos)
    return pos
  })
  if (props.path && points.length > 1) {
    polyline = new maps.Polyline({ path: points, strokeWeight: 4, strokeColor: '#FF6B4A', strokeOpacity: 0.9, strokeStyle: 'solid' })
    polyline.setMap(map)
  }
  if (fit && points.length === 1) {
    if (map.getLevel() > 8) map.setLevel(6) // 전국 축척에서는 핀 하나가 잘 보이도록 확대
    map.setCenter(points[0])
  }
  if (fit && points.length > 1) map.setBounds(bounds, 60, 60, 60, 60)
}

function drawPicked() {
  if (!map) return
  pickedMarker?.setMap(null)
  pickedMarker = null
  if (props.picked?.latitude) {
    const pos = new window.kakao.maps.LatLng(props.picked.latitude, props.picked.longitude)
    pickedMarker = new window.kakao.maps.Marker({ position: pos })
    pickedMarker.setMap(map)
  }
}

onMounted(async () => {
  try {
    await loadSdk()
    const { maps } = window.kakao
    map = new maps.Map(el.value, { center: new maps.LatLng(36.4, 127.8), level: 13 })
    map.addControl(new maps.ZoomControl(), maps.ControlPosition.RIGHT)
    maps.event.addListener(map, 'click', (e) => {
      if (props.clickable) emit('map-click', { latitude: e.latLng.getLat(), longitude: e.latLng.getLng() })
    })
    draw()
    drawPicked()
    // 패널 전환·창 크기 변경으로 지도 영역이 바뀌면 다시 배치한다
    resizeObserver = new ResizeObserver(() => {
      if (!map) return
      const center = map.getCenter()
      map.relayout()
      map.setCenter(center)
    })
    resizeObserver.observe(el.value)
  } catch (e) {
    error.value = e.message
  }
})

watch(() => props.markers, () => draw(), { deep: true })
watch(() => props.selectedId, (id) => {
  draw(false)
  const m = props.markers.find((x) => x.id === id)
  if (m && map) map.panTo(new window.kakao.maps.LatLng(m.latitude, m.longitude))
})
watch(() => props.picked, drawPicked, { deep: true })
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  overlays.forEach((o) => o.setMap(null))
  map = null
})
</script>

<template>
  <div class="kmap-wrap">
    <div ref="el" class="kmap" role="application" aria-label="지도"></div>
    <div v-if="error" class="kmap-error">
      <i class="bi bi-map" aria-hidden="true"></i>
      <p class="mb-1 fw-semibold">지도를 표시할 수 없습니다</p>
      <p class="small text-secondary mb-0">{{ error }}</p>
    </div>
  </div>
</template>

<style>
.kmap-wrap { position: absolute; inset: 0; }
.kmap { width: 100%; height: 100%; }
.kmap-error { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; background: #f4efe7; text-align: center; padding: 24px; }
.kmap-error i { font-size: 2.4rem; color: #cbbda8; }
.mm-pin { position: relative; min-width: 30px; height: 30px; padding: 0 7px; margin-bottom: 7px; border-radius: 15px; border: 2px solid #fff; background: #1f2a44; color: #fff; font-weight: 700; font-size: 13px; line-height: 1; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3); cursor: pointer; display: flex; align-items: center; justify-content: center; }
.mm-pin::after { content: ''; position: absolute; left: 50%; bottom: -7px; margin-left: -5px; border: 5px solid transparent; border-top-color: #1f2a44; border-bottom: 0; }
.mm-pin i { font-size: 13px; }
.mm-pin.selected, .mm-pin:hover, .mm-pin.hot { background: #ff6b4a; }
.mm-pin.selected::after, .mm-pin:hover::after, .mm-pin.hot::after { border-top-color: #ff6b4a; }
.mm-pin.selected { transform: scale(1.2); transform-origin: bottom center; }
.mm-pin.meet { background: #0f9d76; }
.mm-pin.meet::after { border-top-color: #0f9d76; }
</style>
