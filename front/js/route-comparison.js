import { validCoordinates } from './route-optimizer.js';
import { escapeHtml } from './shared.js';

export class RouteComparison {
  constructor(root) { this.root=root; this.maps=[]; this.layers=[]; }
  show(before, after=null) {
    this.layers.forEach(layer=>layer.setMap(null)); this.layers=[];
    const all=[...before,...(after || [])].filter(validCoordinates);
    const status=this.root.querySelector('[data-comparison-status]');
    status.textContent=after?'첫 장소와 마지막 장소 고정 · 같은 지도 범위에서 직선 동선을 비교해요. 번호는 각 경로의 방문 순서입니다.':'동선 최적화를 누르면 오른쪽에 추천 경로가 표시됩니다.';
    const maps=window.kakao?.maps;
    const south=Math.min(...all.map(p=>Number(p.lat))),north=Math.max(...all.map(p=>Number(p.lat)));
    const west=Math.min(...all.map(p=>Number(p.lng))),east=Math.max(...all.map(p=>Number(p.lng)));
    [before,after].forEach((points,index)=>{
      const node=this.root.querySelectorAll('[data-comparison-map]')[index];
      const list=this.root.querySelectorAll('[data-comparison-order]')[index];
      list.textContent=points?.map((p,i)=>`${i+1}. ${p.title}`).join(' → ') || '추천 경로 대기';
      const valid=points?.filter(validCoordinates) || [];
      if(maps && all.length) {
        if(!this.maps[index]) { node.textContent=''; this.maps[index]=new maps.Map(node,{center:new maps.LatLng(all[0].lat,all[0].lng),level:8}); }
        const map=this.maps[index];map.relayout();
        const bounds=new maps.LatLngBounds(new maps.LatLng(south,west),new maps.LatLng(north,east));
        map.setBounds(bounds,45,45,45,45);
        const path=valid.map(p=>new maps.LatLng(Number(p.lat),Number(p.lng)));
        if(path.length) this.layers.push(new maps.Polyline({map,path,strokeWeight:4,strokeColor:index?'#0872ef':'#dc3545',strokeOpacity:.9}));
        valid.forEach((p,i)=>{
          const marker=document.createElement('span'); marker.className=`comparison-pin ${index?'recommended':''}`;marker.textContent=i+1;marker.title=p.title;
          this.layers.push(new maps.CustomOverlay({map,position:path[i],content:marker,yAnchor:1,zIndex:5}));
        });
      } else {
        this.maps[index]=null;
        const xy=p=>[35+(Number(p.lng)-west)/(east-west || 1)*430,255-(Number(p.lat)-south)/(north-south || 1)*220];
        const coords=valid.map(xy),color=index?'#0872ef':'#dc3545';
        node.innerHTML=coords.length?`<svg viewBox="0 0 500 290" role="img" aria-label="${index?'최적화 후':'최적화 전'} 좌표 기반 동선"><polyline points="${coords.map(p=>p.join(',')).join(' ')}" fill="none" stroke="${color}" stroke-width="3"/>${coords.map((p,i)=>`<g><title>${escapeHtml(valid[i].title)}</title><circle cx="${p[0]}" cy="${p[1]}" r="12" fill="${color}"/><text x="${p[0]}" y="${p[1]+4}" text-anchor="middle" fill="white" font-size="12">${i+1}</text></g>`).join('')}</svg><small>지도 연결 전 · 좌표 기반 직선 동선</small>`:'<p class="helper-text">'+(index?'동선 최적화를 눌러 추천 경로를 확인하세요.':'좌표가 있는 장소를 추가해주세요.')+'</p>';
      }
    });
  }
}
