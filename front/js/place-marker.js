// Inline vectors keep category symbols identical across platforms and zoom levels.
export const PLACE_MARKERS = {
  food: {label:'음식점',color:'#c25224',path:'M5 3v5m3-5v5M3 3v5a3 3 0 0 0 6 0V3M6 11v10M18 3c-3 3-3 7 0 8h2V3h-2Zm2 8v10'},
  stay: {label:'숙소',color:'#2563a6',path:'M3 18V7m18 11V9M3 14h18v4H3Zm2-4h5v4H5Zm7-1h7v5h-7M3 18v3m18-3v3'},
  culture: {label:'문화',color:'#7951ad',path:'M4 3h16v18H4ZM8 3v18M16 3v18M4 7h4m-4 5h4m-4 5h4m8-10h4m-4 5h4m-4 5h4'},
  festival: {label:'공연·행사',color:'#ba3474',path:'M14 3a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM4 5l5 4 4-1 4 3 4-4M13 8l-2 6 5 3 2 5M11 14l-5 3-3-1'},
  attraction: {label:'관광지',color:'#197a66',path:'M3 21h18M5 21V11h14v10M2 11h20M4 7l8-4 8 4M2 7h20M6 7v4m6-4v4m6-4v4M10 21v-6h4v6M7 14v3m10-3v3'},
  shopping: {label:'쇼핑',color:'#a36614',path:'M5 8h14l2 13H3L5 8Zm3 0V6a4 4 0 0 1 8 0v2'},
  course: {label:'여행코스',color:'#39752b',path:'M5 4a2 2 0 1 0 0 4 2 2 0 0 0 0-4Zm14 12a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM7 6h8a4 4 0 0 1 0 8H9a2 2 0 0 0 0 4h8'},
};
export function markerStyle(category) {return PLACE_MARKERS[category] || PLACE_MARKERS.attraction;}
export function markerIcon(category) {
  const {path}=markerStyle(category);
  return `<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${path}"/></svg>`;
}
