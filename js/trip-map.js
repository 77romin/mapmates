import { api } from './api.js';
import { escapeHtml } from './shared.js';
export const REGIONS = ['서울','경기','강원','충남','충북','경남','경북','전남','전북','제주','기타'];
export function tripDays(trip) {
  const duration = Math.round((new Date(trip.endDate)-new Date(trip.startDate))/86400000)+1;
  return Array.from({length:Math.min(14,Math.max(1,duration || 1))},(_,i)=>i+1);
}
export function dayDate(trip,day) { const date=new Date(`${trip.startDate}T00:00:00Z`); date.setUTCDate(date.getUTCDate()+day-1); return Number.isNaN(+date)?'':date.toISOString().slice(0,10); }
export function applyType(map,type) {
  if (!map) return;
  const ids=window.kakao.maps.MapTypeId;
  map.removeOverlayMapTypeId(ids.ROADVIEW); map.removeOverlayMapTypeId(ids.TERRAIN);
  map.setMapTypeId(type==='skyview'?ids.SKYVIEW:ids.ROADMAP);
  if(type==='roadview') map.addOverlayMapTypeId(ids.ROADVIEW);
  if(type==='terrain') map.addOverlayMapTypeId(ids.TERRAIN);
}
export function bindRoadview(map,getType) {
  window.kakao.maps.event.addListener(map,'dblclick',event=>{if(getType()==='roadview') openRoadview(event.latLng);});
}
export function openRoadview(position) {
  document.querySelector('#roadview-dialog')?.close(); document.querySelector('#roadview-dialog')?.remove();
  const previous=document.activeElement;
  const dialog=document.createElement('dialog'); dialog.id='roadview-dialog'; dialog.className='roadview-dialog';
  dialog.innerHTML='<div class="section-title-row"><h2>로드뷰</h2><button type="button" aria-label="로드뷰 닫기">×</button></div><p role="status">가까운 도로의 로드뷰를 찾고 있어요.</p><div class="roadview-panorama" aria-label="방향과 위치를 움직일 수 있는 로드뷰"></div>';
  document.body.append(dialog); dialog.showModal(); dialog.querySelector('button').onclick=()=>dialog.close();
  dialog.addEventListener('close',()=>{dialog.remove();previous?.focus();});
  dialog.addEventListener('click',event=>{if(event.target===dialog){const r=dialog.getBoundingClientRect();if(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom)dialog.close();}});
  new window.kakao.maps.RoadviewClient().getNearestPanoId(position,100,panoId=>{
    if(!dialog.isConnected)return;
    if(!panoId){dialog.querySelector('p').textContent='이 위치에는 로드뷰가 없어요. 파란색 도로를 다시 더블클릭해주세요.';return;}
    const view=new window.kakao.maps.Roadview(dialog.querySelector('.roadview-panorama'));
    window.kakao.maps.event.addListener(view,'init',()=>{dialog.dataset.ready='true';});
    view.setPanoId(panoId,position); dialog.querySelector('p').textContent='화면을 드래그해 둘러보고 도로의 이동 화살표로 다른 위치로 이동하세요.';
  });
}
export class TripRoute {
  constructor(getMap, fallback, status) {this.getMap=getMap;this.fallback=fallback;this.status=status;this.overlays=[];this.line=null;this.revision=0;this.cache=new Map();}
  clear(){this.revision++;this.overlays.forEach(x=>x.setMap(null));this.overlays=[];this.line?.setMap(null);this.line=null;if(this.fallback&&!this.getMap())this.fallback.innerHTML='';}
  async draw(entries,mode='straight') {
    this.clear(); const revision=this.revision;const points=entries.filter(e=>e.place&&Number.isFinite(Number(e.place.lat))&&Number.isFinite(Number(e.place.lng)));
    const map=this.getMap(); this.status.textContent=points.length?`${points.length}곳의 방문 동선 · ${mode==='car'?'차량 경로 조회 중':'빨간 실선: 직선 경로'}`:'이 날짜에는 등록된 장소가 없어요.';
    if(!points.length)return;
    if(!map){
      const lats=points.map(e=>Number(e.place.lat)),lngs=points.map(e=>Number(e.place.lng));
      const coords=points.map(e=>[60+(e.place.lng-Math.min(...lngs))/(Math.max(...lngs)-Math.min(...lngs)||1)*680,235-(e.place.lat-Math.min(...lats))/(Math.max(...lats)-Math.min(...lats)||1)*180]);
      if(this.fallback)this.fallback.innerHTML=`<svg viewBox="0 0 800 300" aria-label="직선 동선 미리보기"><polyline points="${coords.map(c=>c.join(',')).join(' ')}" fill="none" stroke="#dc3545" stroke-width="4"/>${coords.map((c,i)=>`<circle cx="${c[0]}" cy="${c[1]}" r="18" fill="#dc3545"/><text x="${c[0]}" y="${c[1]+5}" fill="white" text-anchor="middle">${i+1}</text>`).join('')}</svg>`;
      this.status.textContent='지도 연결 실패 · 좌표 기반 직선 미리보기';return;
    }
    const maps=window.kakao.maps,bounds=new maps.LatLngBounds();
    let path=points.map((entry,i)=>{const pos=new maps.LatLng(Number(entry.place.lat),Number(entry.place.lng));bounds.extend(pos);const node=document.createElement('button');node.className='companion-route-marker';node.textContent=i+1;node.title=entry.place.title;node.onclick=()=>{this.status.textContent=`${entry.day || 1}일차 ${entry.time || ''} · ${entry.place.title}`;};this.overlays.push(new maps.CustomOverlay({map,position:pos,content:node,yAnchor:1,zIndex:8}));return pos;});
    if(path.length===1){map.setCenter(path[0]);map.setLevel(5);}else map.setBounds(bounds,50,50,50,50);
    let color='#dc3545';
    if(mode==='car'&&points.length>1){
      // Each leg is requested separately so a long itinerary never exceeds the waypoint limit.
      try {
        const vertices=[];
        for(let i=1;i<points.length;i++){
          const origin={lat:Number(points[i-1].place.lat),lng:Number(points[i-1].place.lng)},destination={lat:Number(points[i].place.lat),lng:Number(points[i].place.lng)};
          const key=JSON.stringify([origin,destination]);let result=this.cache.get(key);
          if(!result){result=await api.getCarDirections({origin,destination,waypoints:[]});if(result)this.cache.set(key,result);}
          if(revision!==this.revision)return;
          const leg=result?.sections?.flatMap(s=>s.roads?.flatMap(r=>r.vertexes || []) || []) || [];
          if(!leg.length)throw new Error('no route');vertices.push(...leg);
        }
        path=[];for(let i=0;i<vertices.length;i+=2)path.push(new maps.LatLng(vertices[i+1],vertices[i]));color='#0872ef';
        this.status.textContent=`${points.length}곳의 방문 동선 · 파란 실선: 실제 차량 경로`;
      }catch{if(revision!==this.revision)return;this.status.textContent='차량 경로 조회 실패 · 빨간 직선 경로로 표시합니다.';}
    }
    if(revision!==this.revision)return;
    this.line=new maps.Polyline({map,path,strokeColor:color,strokeWeight:5,strokeOpacity:.9,strokeStyle:'solid'});
  }
}

function latLngToGrid(lat, lon) {
  const RE = 6371.00877, GRID = 5, SLAT1 = 30, SLAT2 = 60, OLON = 126, OLAT = 38, XO = 43, YO = 136;
  const DEGRAD = Math.PI / 180, re = RE / GRID, slat1 = SLAT1 * DEGRAD, slat2 = SLAT2 * DEGRAD;
  let sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(Math.tan(Math.PI * .25 + slat2 * .5) / Math.tan(Math.PI * .25 + slat1 * .5));
  const sf = Math.pow(Math.tan(Math.PI * .25 + slat1 * .5), sn) * Math.cos(slat1) / sn;
  const ro = re * sf / Math.pow(Math.tan(Math.PI * .25 + OLAT * DEGRAD * .5), sn);
  const ra = re * sf / Math.pow(Math.tan(Math.PI * .25 + lat * DEGRAD * .5), sn);
  let theta = lon * DEGRAD - OLON * DEGRAD;
  if (theta > Math.PI) theta -= 2 * Math.PI;
  if (theta < -Math.PI) theta += 2 * Math.PI;
  theta *= sn;
  return { nx: Math.floor(ra * Math.sin(theta) + XO + .5), ny: Math.floor(ro - ra * Math.cos(theta) + YO + .5) };
}

function weatherBase() {
  const now = new Date(Date.now() + 9 * 60 * 60 * 1000);
  const bases = [2, 5, 8, 11, 14, 17, 20, 23];
  let baseHour = [...bases].reverse().find((hour) => hour <= now.getUTCHours());
  if (baseHour === undefined) { now.setUTCDate(now.getUTCDate() - 1); baseHour = 23; }
  return { baseDate: `${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, "0")}${String(now.getUTCDate()).padStart(2, "0")}`, baseTime: `${String(baseHour).padStart(2, "0")}00` };
}

function midForecastBase() {
  const now = new Date(Date.now() + 9 * 60 * 60 * 1000);
  let hour = now.getUTCHours() >= 18 ? 18 : now.getUTCHours() >= 6 ? 6 : -6;
  if (hour < 0) { now.setUTCDate(now.getUTCDate() - 1); hour = 18; }
  return `${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, "0")}${String(now.getUTCDate()).padStart(2, "0")}${String(hour).padStart(2, "0")}00`;
}

function midRegionCodes(place) {
  const region = `${place.region || ""} ${place.title || ""}`;
  if (/서울|경기|인천/.test(region)) return { landRegId: "11B00000", temperatureRegId: "11B10101" };
  if (/강원/.test(region)) return { landRegId: "11D10000", temperatureRegId: "11D10301" };
  if (/대전|세종|충남/.test(region)) return { landRegId: "11C20000", temperatureRegId: "11C20401" };
  if (/충북/.test(region)) return { landRegId: "11C10000", temperatureRegId: "11C10301" };
  if (/광주|전남/.test(region)) return { landRegId: "11F20000", temperatureRegId: "11F20501" };
  if (/전북/.test(region)) return { landRegId: "11F10000", temperatureRegId: "11F10201" };
  if (/대구|경북/.test(region)) return { landRegId: "11H10000", temperatureRegId: "11H10701" };
  if (/부산|울산|경남/.test(region)) return { landRegId: "11H20000", temperatureRegId: "11H20201" };
  return { landRegId: "11G00000", temperatureRegId: "11G00201" };
}

function dateDiffFromToday(date) {
  const today = new Date();
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((date - start) / 86400000);
}

function weatherIcon(label) {
  if (/비.*눈|눈.*비/.test(label)) return "🌨️";
  if (label.includes("눈")) return "❄️";
  if (label.includes("비")) return "🌧️";
  if (label.includes("흐림")) return "☁️";
  if (label.includes("구름")) return "🌤️";
  return "☀️";
}


const forecastCache = new Map();
export async function renderDayWeather(node,place,date,region='') {
  node.dataset.date=date; node.textContent=`${date} · 날씨 확인 중`;
  if(!place){node.textContent=`${date} · 장소를 추가하면 해당 지역의 날씨가 표시돼요.`;return;}
  const diff=dateDiffFromToday(new Date(`${date}T00:00:00`));
  if(diff<0 || diff>10){node.textContent=`${date} · ${place.title} · ${diff<0?'지난 날짜의 예보는 제공하지 않아요.':'예보 제공 전 (여행 10일 전부터 확인 가능)'}`;return;}
  const key=JSON.stringify([place.lat,place.lng,date,region]);
  if(!forecastCache.has(key))forecastCache.set(key,Promise.all([api.getWeather({...latLngToGrid(Number(place.lat),Number(place.lng)),...weatherBase()}).catch(()=>null),api.getMidWeather({...midRegionCodes({...place,region:region || place.region}),tmFc:midForecastBase()}).catch(()=>null)]));
  const [items,mid]=await forecastCache.get(key);if(node.dataset.date!==date)return;
  const values=(items || []).filter(x=>x.fcstDate===date.replaceAll('-',''));
  const temperatures=values.filter(x=>x.category==='TMP').map(x=>Number(x.fcstValue)).filter(Number.isFinite);
  const rainy=values.some(x=>x.category==='PTY'&&Number(x.fcstValue)>0);
  const sky=values.find(x=>x.category==='SKY'&&x.fcstTime==='1200')?.fcstValue;
  const label=values.length?(rainy?'비/눈':({1:'맑음',3:'구름 많음',4:'흐림'}[sky] || '날씨')):(mid?.land?.[`wf${diff}Am`] || mid?.land?.[`wf${diff}`]);
  const low=temperatures.length?Math.min(...temperatures):mid?.temperature?.[`taMin${diff}`],high=temperatures.length?Math.max(...temperatures):mid?.temperature?.[`taMax${diff}`];
  node.textContent=`${date} · ${place.title} · ${label || '예보를 불러오지 못했어요.'}${low!=null&&high!=null?` · ${low}° / ${high}°`:''}${label?` · 기상청 ${values.length?'단기':'중기'}예보`:''}`;
}
