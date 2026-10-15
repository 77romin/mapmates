// Reproduce README screenshots using real map/API responses in an isolated context.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs/promises');const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1});
  await page.addInitScript(()=>localStorage.setItem('neorang-intro-seen-v1','1'));
  await page.route('http://localhost:5500/**',async route=>{
   const url=new URL(route.request().url());const root=path.resolve(__dirname,'..');const file=path.resolve(root,'.'+decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname));
   if(!file.startsWith(root+path.sep))return route.abort();
   try {let body=await fs.readFile(file);if(url.pathname==='/js/app.js')body=body.toString().replace('kakaoMap.setDraggable(true);','window.__captureMap=kakaoMap;kakaoMap.setDraggable(true);');
    const types={'.html':'text/html','.js':'application/javascript','.css':'text/css','.png':'image/png'};
    await route.fulfill({body,contentType:types[path.extname(file)]||'application/octet-stream'});
   }catch{await route.fulfill({status:404,body:''});}
  });
  await page.goto('http://localhost:5500/index.html');
  await page.waitForFunction(()=>window.__captureMap,{timeout:45000});
  await page.evaluate(()=>{window.__captureMap.setCenter(new kakao.maps.LatLng(37.532,126.975));window.__captureMap.setLevel(6);});
  await page.waitForFunction(()=>document.querySelectorAll('.place-card').length>3 && document.querySelector('#place-list').textContent.includes('용산'),{timeout:45000});
  await page.waitForSelector('.place-category-marker[data-category="food"]');
  await page.waitForSelector('.place-category-marker[data-category="attraction"]');
  const paths=await page.locator('.place-category-marker svg path').evaluateAll(nodes=>new Set(nodes.map(n=>n.getAttribute('d'))).size);
  if(paths<2)throw Error('카테고리별 아이콘이 구분되지 않습니다.');
  await page.click('#map-type-toggle');
  await page.screenshot({path:'docs/screenshots/guide/35-map-normal.png'});
  await page.click('[data-map-base="skyview"]');
  await page.waitForTimeout(1200);
  await page.screenshot({path:'docs/screenshots/guide/36-map-satellite.png'});
  await page.click('[data-map-base="roadmap"]');
  await page.click('#terrain-toggle');
  await page.waitForTimeout(1200);
  await page.screenshot({path:'docs/screenshots/guide/37-map-terrain.png'});
  await page.click('#terrain-toggle');await page.click('#map-type-toggle');
  await page.screenshot({path:'docs/screenshots/guide/06-main-explore.png'});
  const pin=page.locator('.place-category-marker').first();
  const selectedId=await pin.getAttribute('data-place-id');
  await pin.focus();await page.keyboard.press('Enter');
  await page.waitForSelector('.modal');
  if(await page.locator(`.place-category-marker.is-active[data-place-id="${selectedId}"]`).count()!==1)throw Error('키보드 장소 선택 실패');
  await page.click('.modal-close');
  await page.click('#place-filter-toggle');
  await page.click('[data-place-category="all"]');
  await page.click('[data-place-category="food"]');
  await page.screenshot({path:'docs/screenshots/guide/07-place-filter.png'});
  if(await page.locator('.place-category-marker:not([data-category="food"])').count())throw Error('필터와 핀 분류 불일치');
  await page.click('#place-filter-toggle');
  await page.click('#place-filter-toggle');
  await page.click('[data-place-category="food"]');
  await page.click('#place-filter-toggle');
  await page.click('#search-panel-toggle');
  await page.waitForFunction(()=>Number(getComputedStyle(document.querySelector('.search-panel')).opacity)===0);
  await page.evaluate(()=>{window.__captureMap.relayout();window.__captureMap.setCenter(new kakao.maps.LatLng(37.532,126.975));});
  await page.click('[data-layer="charger"]');
  await page.waitForSelector('.charger-map-marker',{timeout:60000});
  await page.waitForFunction(()=>document.querySelector('#charger-status').textContent.includes('최대 80곳'),{timeout:60000});
  await page.locator('.charger-map-marker').first().click();
  await page.screenshot({path:'docs/screenshots/guide/10-chargers.png'});
  await page.click('[data-layer="charger"]');
  await page.evaluate(async()=>{
   const {places}=await import('/js/data.js');
   const pool=places.filter(p=>p.lat && p.lng && Math.abs(p.lat-37.532)<.025 && Math.abs(p.lng-126.975)<.03);
   const candidates=pool.splice(0,1);
   while(candidates.length<10 && pool.length){pool.sort((a,b)=>Math.min(...candidates.map(p=>(b.lat-p.lat)**2+(b.lng-p.lng)**2))-Math.min(...candidates.map(p=>(a.lat-p.lat)**2+(a.lng-p.lng)**2)));candidates.push(pool.shift());}
   if(!candidates.length)throw Error('용산 관광지 데이터가 없습니다.');
   const key='neorang-galjido-v1',s=JSON.parse(localStorage.getItem(key));
   s.plans=candidates.flatMap((p,i)=>Array.from({length:Math.max(1,10-i)},(_,j)=>({id:`capture-${i}-${j}`,ownerId:`capture-member-${j}`,tripSchedule:[{placeId:p.id}],itineraryPlaces:{[p.id]:p}})));
   localStorage.setItem(key,JSON.stringify(s));
  });
  await page.click('[data-layer="hotplace"]');
  await page.waitForSelector('.hotplace-map-marker');
  await page.waitForFunction(()=>document.querySelectorAll('.toast').length===0);
  await page.screenshot({path:'docs/screenshots/guide/11-hotplaces.png'});
  await page.goto('http://localhost:5500/planner.html');
  await page.waitForSelector('#schedule-list');
  await page.evaluate(async()=>{
   const {places}=await import('/js/data.js');const key='neorang-galjido-v1',s=JSON.parse(localStorage.getItem(key));
   s.trip={id:'capture-route',title:'제주 하루 동선 비교',startDate:'2026-10-15',endDate:'2026-10-15',people:4,budget:0};
   s.itinerary=places.slice(0,8).map(p=>p.id);s.itineraryPlaces=Object.fromEntries(places.slice(0,8).map(p=>[p.id,p]));
   s.tripSchedule=s.itinerary.map((id,i)=>({placeId:id,day:1,time:`${String(9+i).padStart(2,'0')}:00`,memo:''}));localStorage.setItem(key,JSON.stringify(s));
  });
  await page.reload();
  await page.waitForFunction(()=>document.querySelector('#planner-map').querySelector('img'));
  await page.click('#optimize-route');
  await page.locator('#route-comparison').scrollIntoViewIfNeeded();
  await page.waitForFunction(()=>document.querySelectorAll('.comparison-pin').length===16);
  await page.locator('#route-comparison').screenshot({path:'docs/screenshots/guide/34-route-map-comparison.png'});
  console.log('Captured Yongsan real map: explore, filters, chargers, hotplaces. Hotplace counts use isolated demonstration plans.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exit(1);});
