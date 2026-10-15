const assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
(async()=>{
  const browser=await chromium.launch({channel:'chrome',headless:true});
  try {
    const capturing=process.env.CAPTURE_README==='1';
    const page=await browser.newPage({viewport:capturing?{width:1440,height:1100}:{width:390,height:844}});
    const errors=[]; page.on('pageerror',error=>errors.push(error.message));
    await page.addInitScript(()=>localStorage.setItem('neorang-intro-seen-v1','1'));
    // Serve local assets through Playwright so this test also works without socket access.
    await page.route('**/*',async route=>{
      const url=new URL(route.request().url());
      if(capturing && route.request().resourceType()==='image' && url.hostname!=='localhost') return route.fulfill({body:await fs.readFile(path.resolve(__dirname,'../assets/sunset-clouds.png')),contentType:'image/png'});
      if(url.hostname!=='localhost') return route.abort();
      if(url.pathname==='/config.js') return route.fulfill({body:'window.APP_CONFIG={}',contentType:'application/javascript'});
      const root=path.resolve(__dirname,'..');
      const file=path.resolve(root,'.'+decodeURIComponent(url.pathname));
      if(!file.startsWith(root+path.sep)) return route.abort();
      try {
        const types={'.html':'text/html','.js':'application/javascript','.css':'text/css','.png':'image/png'};
        await route.fulfill({body:await fs.readFile(file),contentType:types[path.extname(file)]||'application/octet-stream'});
      } catch {await route.fulfill({status:404,body:''});}
    });
    await page.goto('http://localhost:5500/planner.html');
    await page.waitForSelector('#schedule-list');
    await page.evaluate(async()=>{
      const {places}=await import('/js/data.js');
      const key='neorang-galjido-v1',state=JSON.parse(localStorage.getItem(key));
      state.loggedIn=true;state.user=state.members[0];
      state.trip={id:'algorithm-ui',title:'최적화 검증',startDate:'2026-10-15',endDate:'2026-10-16',people:4,budget:0};
      state.itinerary=places.slice(0,8).map(p=>p.id);
      state.itineraryPlaces=Object.fromEntries(places.slice(0,8).map(p=>[p.id,{...p,image:'./assets/sunset-clouds.png'}]));
      state.tripSchedule=state.itinerary.map((id,i)=>({placeId:id,day:1,time:`${String(9+i).padStart(2,'0')}:00`,memo:`메모 ${id}`}));
      localStorage.setItem(key,JSON.stringify(state));
    });
    await page.reload();
    const read=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('neorang-galjido-v1')).tripSchedule);
    const before=await read();
    await page.click('#optimize-route');
    assert.match(await page.locator('#optimization-preview').innerText(),/31.0%/);
    assert.equal(await page.locator('[data-comparison-map] svg').count(),2);
    assert.notEqual(await page.locator('[data-comparison-map] polyline').nth(0).getAttribute('points'),await page.locator('[data-comparison-map] polyline').nth(1).getAttribute('points'));
    assert.match(await page.locator('[data-comparison-order]').nth(1).innerText(),/2. 아쿠아플라넷/);
    assert.deepEqual(await read(),before);
    async function capture(name) {
      const panel=page.locator('.route-optimization');
      await panel.evaluate(element=>window.scrollTo(0,element.getBoundingClientRect().top+window.scrollY-80));
      const box=await panel.boundingBox();
      const end=await page.locator('.schedule-row').nth(2).boundingBox();
      await page.screenshot({path:`docs/screenshots/guide/${name}.png`,clip:{x:box.x,y:box.y,width:box.width,height:end.y+end.height-box.y}});
    }
    if(capturing) await capture('32-route-optimization-preview');
    if(capturing) await page.locator('#route-comparison').screenshot({path:'tmp/route-comparison-fallback.png'});
    await page.click('#apply-optimization');
    const after=await read();
    assert.notDeepEqual(after,before);
    assert.deepEqual(after.map(p=>p.time),before.map(p=>p.time));
    assert.equal(after[0].placeId,before[0].placeId);assert.equal(after.at(-1).placeId,before.at(-1).placeId);
    assert.ok(after.every(p=>p.memo===`메모 ${p.placeId}`));
    if(capturing) await capture('33-route-optimization-applied');
    await page.click('#undo-optimization');assert.deepEqual(await read(),before);
    assert.equal(await page.locator('[data-comparison-map] svg').count(),1);
    await page.click('#optimize-route');await page.click('#day-tabs [data-day="2"]');
    assert.ok(await page.locator('#apply-optimization').isHidden());
    await page.click('#day-tabs [data-day="1"]');await page.click('#optimize-route');await page.click('#apply-optimization');
    await page.click('#save-plan');await page.reload();assert.deepEqual(await read(),after);
    assert.equal(await page.locator('.schedule-row').first().getAttribute('data-place-id'),String(before[0].placeId));
    await page.setViewportSize({width:390,height:844});
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await page.screenshot({path:'tmp/route-optimizer-mobile.png',fullPage:true});
    assert.deepEqual(errors,[]);
    console.log('PASS preview, apply, undo, day invalidation, notes/time slots, save/reload, mobile width, no JS errors');
  } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
