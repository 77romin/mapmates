<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * markers: [{ id, latitude, longitude, title, label?(번호 핀), kind?('hot'|'meet'), image?(사진 핀), icon?(사진이 없을 때 아이콘 클래스) }]
 * path: true 면 markers 순서대로 경로선을 그린다.
 */
const props = defineProps({
  markers: { type: Array, default: () => [] },
  path: { type: Boolean, default: false },
  // 실제 경로 좌표 [[위도, 경도], ...]. 있으면 직선 대신 이 경로를 그린다
  routePath: { type: Array, default: null },
  dashed: { type: Boolean, default: false },
  // 주어지면 이 값이 바뀔 때만 마커에 맞춰 지도를 이동한다. (없으면 마커가 바뀔 때마다 맞춘다)
  fitKey: { type: Number, default: undefined },
  selectedId: { type: [Number, String], default: null },
  clickable: { type: Boolean, default: false },
  picked: { type: Object, default: null },
})
const emit = defineEmits(['marker-click', 'map-click', 'view-change'])

const el = ref(null)
const error = ref('')
let map = null
let overlays = []
let polyline = null
let pickedMarker = null
let resizeObserver = null
let lastFitKey
let programmatic = false // 코드가 지도를 옮기는 중인지 (사용자 조작과 구분)
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
  const iconEl = () => {
    const i = document.createElement('i')
    i.className = 'bi ' + (m.icon || 'bi-geo-alt-fill')
    return i
  }
  if (m.label != null) {
    b.textContent = m.label
  } else if (m.image) {
    // 사진 핀: 불러오지 못하면 아이콘 핀으로 바꾼다
    b.classList.add('photo')
    const img = document.createElement('img')
    img.src = m.image
    img.alt = ''
    img.loading = 'lazy'
    img.addEventListener('error', () => {
      b.classList.remove('photo')
      b.replaceChildren(iconEl())
    })
    b.appendChild(img)
  } else {
    b.appendChild(iconEl())
  }
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
    const el = pinEl(m)
    const baseZ = m.id === props.selectedId ? 10 : 1
    const overlay = new maps.CustomOverlay({ position: pos, content: el, yAnchor: 1, zIndex: baseZ })
    // 겹친 핀도 확인할 수 있게, 마우스를 올리거나 포커스한 핀을 맨 위로 올린다
    el.addEventListener('mouseenter', () => overlay.setZIndex(20))
    el.addEventListener('mouseleave', () => overlay.setZIndex(baseZ))
    el.addEventListener('focus', () => overlay.setZIndex(20))
    el.addEventListener('blur', () => overlay.setZIndex(baseZ))
    overlay.setMap(map)
    overlays.push(overlay)
    bounds.extend(pos)
    return pos
  })
  if (props.path && points.length > 1) {
    const linePath = props.routePath?.length > 1 ? props.routePath.map((p) => new maps.LatLng(p[0], p[1])) : points
    polyline = new maps.Polyline({ path: linePath, strokeWeight: 5, strokeColor: '#FF6B4A', strokeOpacity: 0.9, strokeStyle: props.dashed ? 'shortdash' : 'solid' })
    polyline.setMap(map)
  }
  if (fit && points.length) programmatic = true
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
    // 핀에 포커스가 가면(키보드 Tab 등) 브라우저가 지도 내부 영역을 스크롤시켜 지도가 어긋난다. 스크롤을 원위치로 되돌린다.
    el.value.addEventListener('scroll', (e) => {
      const t = e.target
      if (t.scrollLeft || t.scrollTop) {
        t.scrollLeft = 0
        t.scrollTop = 0
      }
    }, true)
    // 지도가 멈출 때마다 현재 보이는 영역을 알린다. byUser: 사용자가 직접 옮기거나 확대·축소했는지
    maps.event.addListener(map, 'idle', () => {
      if (!map) return // 화면을 떠난 뒤 늦게 도착한 이벤트는 무시한다
      emit('view-change', { ...currentView(), byUser: !programmatic })
      programmatic = false
    })
    lastFitKey = props.fitKey
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

function shouldFit() {
  if (props.fitKey === undefined) return true
  const changed = props.fitKey !== lastFitKey
  lastFitKey = props.fitKey
  return changed
}
function currentView() {
  const b = map.getBounds()
  const sw = b.getSouthWest()
  const ne = b.getNorthEast()
  return { minLat: sw.getLat(), maxLat: ne.getLat(), minLng: sw.getLng(), maxLng: ne.getLng(), level: map.getLevel() }
}
watch(() => props.markers, () => draw(shouldFit()), { deep: true })
watch(() => props.selectedId, (id) => {
  draw(false)
  const m = props.markers.find((x) => x.id === id)
  // 재배치 직후의 panTo(애니메이션)는 무시되는 경우가 있어 setCenter 로 바로 옮긴다
  if (m && map && m.latitude > 32.5 && m.latitude < 39.5) programmatic = true
  if (m && map && m.latitude > 32.5 && m.latitude < 39.5) map.setCenter(new window.kakao.maps.LatLng(m.latitude, m.longitude))
})
watch(() => props.picked, drawPicked, { deep: true })
watch(() => [props.routePath, props.dashed], () => draw(false))
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
.kmap-error { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; background: var(--mm-surface); text-align: center; padding: 24px; }
.kmap-error i { font-size: 2.4rem; color: var(--mm-placeholder); }
.mm-pin { position: relative; min-width: 30px; height: 30px; padding: 0 7px; margin-bottom: 7px; border-radius: 15px; border: 2px solid #fff; background: var(--mm-ink); color: #fff; font-weight: 700; font-size: 13px; line-height: 1; box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3); cursor: pointer; display: flex; align-items: center; justify-content: center; }
.mm-pin::after { content: ''; position: absolute; left: 50%; bottom: -7px; margin-left: -5px; border: 5px solid transparent; border-top-color: var(--mm-ink); border-bottom: 0; }
.mm-pin i { font-size: 13px; }
.mm-pin.selected, .mm-pin:hover, .mm-pin.hot { background: var(--mm-coral-dark); }
.mm-pin.selected::after, .mm-pin:hover::after, .mm-pin.hot::after { border-top-color: var(--mm-coral-dark); }
.mm-pin.selected { transform: scale(1.2); transform-origin: bottom center; }
.mm-pin.photo { width: 44px; min-width: 44px; height: 44px; padding: 0; border-radius: 50%; border-width: 3px; background: #fff; overflow: visible; transition: transform 0.12s; transform-origin: bottom center; }
.mm-pin.photo img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; display: block; }
.mm-pin.photo::after { border-top-color: #fff; bottom: -8px; }
.mm-pin.photo:hover, .mm-pin.photo:focus-visible { background: #fff; transform: scale(1.35); }
.mm-pin.photo:hover::after { border-top-color: #fff; }
.mm-pin.photo.selected { border-color: var(--mm-coral); background: var(--mm-coral); transform: scale(1.45); }
.mm-pin.photo.selected::after { border-top-color: var(--mm-coral); }
.mm-pin.meet { background: var(--mm-success); }
.mm-pin.meet::after { border-top-color: var(--mm-success); }
</style>
