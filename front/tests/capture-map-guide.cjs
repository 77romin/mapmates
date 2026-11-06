const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs/promises');const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1});
  page.on('response',async response=>{if(response.url().includes('getVilageFcst')){try{const data=await response.json();console.log('weather response',JSON.stringify(data.response?.header), 'items',data.response?.body?.items?.item?.length);}catch{console.log('weather response status',response.status());}}});
  await page.addInitScript(()=>localStorage.setItem('neorang-intro-seen-v1','1'));
  await page.route('http://localhost:5500/**',async route=>{
   const url=new URL(route.request().url());
   if(url.pathname==='/api/kakao-directions') {
    const target=new URL('https://apis-navi.kakaomobility.com/v1/directions');target.search=url.search;
    const response=await fetch(target,{headers:{Authorization:route.request().headers().authorization,Accept:'application/json'}});
    return route.fulfill({status:response.status,body:await response.text(),contentType:'application/json'});
   }
   const root=path.resolve(__dirname,'..'),file=path.resolve(root,'.'+decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname));
   if(!file.startsWith(root+path.sep))return route.abort();
   try {let body=await fs.readFile(file);
    if(url.pathname==='/js/app.js')body=body.toString().replace('kakaoMap.setDraggable(true);','window.__captureMap=kakaoMap;kakaoMap.setDraggable(true);');
    if(url.pathname==='/js/trip-map.js')body=body.toString().replace("const view=new window.kakao.maps.Roadview(dialog.querySelector('.roadview-panorama'));","const view=new window.kakao.maps.Roadview(dialog.querySelector('.roadview-panorama'));window.__captureRoadview=view;");
    await route.fulfill({body,contentType:({'.html':'text/html','.js':'application/javascript','.css':'text/css','.png':'image/png'})[path.extname(file)]||'application/octet-stream'});
   }catch{await route.fulfill({status:404,body:''});}
  });
  const shot=async name=>{await page.waitForFunction(()=>document.querySelectorAll('.toast').length===0);await page.screenshot({path:`docs/screenshots/guide/${name}.png`});};
  await page.goto('http://localhost:5500/index.html');await page.waitForFunction(()=>window.__captureMap);
  await page.evaluate(()=>{window.__captureMap.setCenter(new kakao.maps.LatLng(37.532,126.975));window.__captureMap.setLevel(6);});
  if(process.env.SKIP_WEATHER!=='1') {
  await page.click('[data-layer="weather"]');
  for(let attempt=0;attempt<3;attempt++) {
    try {await page.waitForSelector('.weather-forecast-table',{timeout:12000});break;}
    catch(error){if(attempt===2)throw error;await page.click('[data-layer="weather"]');await page.click('[data-layer="weather"]');}
  }
  await shot('09-weather');
  await page.click('[data-layer="weather"]');
  console.log('weather captured');
  }
  // Remaining stages use the same real map in this isolated browser.
  await page.locator('#map-type-toggle').click();
  await shot('12-map-types');
  await page.locator('[data-map-base="roadview"]').click();
  await shot('14-roadview-layer');
  await page.evaluate(()=>{window.__captureMap.setCenter(new kakao.maps.LatLng(37.575,126.9769));window.__captureMap.setLevel(3);});
  const point=await page.evaluate(()=>{const p=window.__captureMap.getProjection().containerPointFromCoords(new kakao.maps.LatLng(37.575,126.9769));return {x:p.x,y:p.y};});
  const box=await page.locator('#kakao-map').boundingBox();await page.mouse.dblclick(box.x+point.x,box.y+point.y);
  await page.waitForSelector('#roadview-dialog[data-ready="true"]',{timeout:30000});
  await page.evaluate(()=>window.__captureRoadview.setViewpoint({pan:0,tilt:3,zoom:0}));
  await page.waitForFunction(()=>document.querySelectorAll('.toast').length===0);
  await page.waitForTimeout(2000);
  await shot('15-roadview-popup');
  await page.locator('[aria-label="로드뷰 닫기"]').click();
  await page.reload();await page.waitForFunction(()=>window.__captureMap);
  await page.evaluate(()=>{const key='neorang-galjido-v1',s=JSON.parse(localStorage.getItem(key));s.loggedIn=true;s.members[0].photo='';s.user=s.members[0];localStorage.setItem(key,JSON.stringify(s));});
  await page.reload();await page.waitForFunction(()=>window.__captureMap);
  await page.click('[data-workspace="browse"]');await page.waitForSelector('[data-select-trip]');
  await page.evaluate(()=>{window.__captureMap.setCenter(new kakao.maps.LatLng(37.532,126.975));window.__captureMap.setLevel(6);});
  await page.waitForTimeout(1000);await shot('16-browse-cards');
  await page.locator('[data-select-trip]').nth(1).click();
  await page.click('[data-workspace-mode="car"]');
  await page.waitForFunction(()=>document.querySelector('#workspace-route-status').textContent.includes('실제 차량 경로'),{timeout:60000});
  console.log('browse route',await page.locator('#workspace-route-status').innerText());
  if(await page.locator('#workspace-join').isEnabled())await page.click('#workspace-join');
  await page.waitForFunction(()=>document.querySelectorAll('.toast').length===0);await shot('18-browse-joined');
  await page.click('[data-workspace="mine"]');await page.locator('[data-select-trip]').first().click();await page.click('[data-workspace-mode="car"]');
  await page.waitForFunction(()=>document.querySelector('#workspace-route-status').textContent.includes('실제 차량 경로'),{timeout:60000});
  console.log('mine route',await page.locator('#workspace-route-status').innerText());
  await shot('21-mine-car');
 }finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exit(1);});
