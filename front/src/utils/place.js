/** 콘텐츠 유형별 아이콘 (사진이 없는 장소의 자리 표시용) */
const TYPE_ICONS = {
  12: 'bi-binoculars', // 관광지
  14: 'bi-bank', // 문화시설
  15: 'bi-balloon', // 축제공연행사
  25: 'bi-signpost-split', // 여행코스
  28: 'bi-bicycle', // 레포츠
  32: 'bi-house-door', // 숙박
  38: 'bi-bag', // 쇼핑
  39: 'bi-cup-hot', // 음식점
}

export const typeIcon = (contentTypeId) => TYPE_ICONS[contentTypeId] || 'bi-geo-alt'
