const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000}});const page=await context.newPage();
 let ev=0,weather=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{const p=new URL(r.url()).pathname;if(p.endsWith('getChargerInfo'))ev++;if(p.endsWith('getVilageFcst'))weather++;});
 await page.addInitScript(()=>localStorage.setItem('neorang-intro-seen-v1','1'));
 await page.goto('http://localhost:5500');await page.waitForSelector('#kakao-map');await page.waitForFunction(()=>document.querySelector('#live-data-status')?.textContent.includes('km'),{timeout:15000});
 console.log('LIVE TourAPI places',await page.locator('.place-card').count(),'weather requests before toggle',weather);
 await page.locator('[data-layer=weather]').click();await page.waitForFunction(()=>!document.querySelector('#weather-map-badge')?.textContent.includes('불러오는 중'),{timeout:15000});console.log('LIVE weather',await page.locator('#weather-map-badge').innerText());
 for(let i=0;i<3;i++){await page.locator('#zoom-in').click();await page.waitForTimeout(800)}
 console.log('before charger');let start=Date.now();await page.locator('[data-layer=charger]').click();await page.waitForTimeout(500);console.log('initial charger status',await page.locator('#charger-status').innerText(),'requests',ev);await page.waitForFunction(()=>/최대 80|조회 실패|지역을 확인하지/.test(document.querySelector('#charger-status')?.textContent),null,{timeout:55000});
 console.log('LIVE chargers cold ms',Date.now()-start,'requests',ev,'markers',await page.locator('.charger-map-marker').count(),'status',await page.locator('#charger-status').innerText());
 const before=ev;await page.locator('[data-layer=charger]').click();await page.locator('[data-layer=charger]').click();await page.waitForTimeout(300);console.log('LIVE charger warm additional requests',ev-before);
 await page.screenshot({path:path.resolve('docs/screenshots/01-map-explore.png')});
 await page.locator('[data-layer=charger]').click();console.log('LIVE toggle off visible markers',await page.locator('.charger-map-marker:visible').count(),'JS errors',errors);
 await page.locator('.place-card').first().click();
 await page.waitForFunction(()=>document.querySelector('.detail-metrics strong:nth-child(2)') || document.querySelector('.detail-description'));
 await page.waitForTimeout(1800);
 console.log('LIVE detail description characters',(await page.locator('.detail-description').innerText()).length,'sun metrics',await page.locator('.detail-metrics').innerText());
 await page.goto('http://localhost:5500/planner.html');await page.waitForTimeout(700);await page.locator('[data-route-mode=car]').click();await page.waitForFunction(()=>/추천 차량 경로|직선 경로로 표시/.test(document.querySelector('#route-status')?.textContent),null,{timeout:15000});console.log('LIVE car route',await page.locator('#route-status').innerText(),await page.locator('#route-distance').innerText());
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
