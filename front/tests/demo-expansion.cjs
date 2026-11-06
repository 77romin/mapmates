const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const page = await browser.newPage(); const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.addInitScript(() => localStorage.setItem('neorang-intro-seen-v1', '1'));
    await page.route('**/config.js', r => r.fulfill({ body: 'window.APP_CONFIG={}', contentType: 'application/javascript' }));
    await page.goto('http://localhost:5500/index.html');
    const check = async () => {
      const state = await page.evaluate(() => JSON.parse(localStorage.getItem('neorang-galjido-v1')));
      assert.equal(state.posts.length, 50); assert.equal(state.companions.length, 40); assert.equal(state.plans.length, 40);
      assert.equal(new Set(state.posts.map(p => p.id)).size, 50);
      assert.equal(new Set(state.companions.map(p => p.id)).size, 40);
      for (const trip of state.companions.filter(t => t.id >= 900000000001)) {
        assert.ok(state.members.some(m => m.id === trip.ownerId));
        assert.ok(state.plans.some(p => p.id === trip.tripId && p.ownerId === trip.ownerId));
        assert.equal(trip.schedule.length, 3); assert.deepEqual([...new Set(trip.schedule.map(e => e.day))], [1, 2]);
        assert.ok(trip.schedule.every(e => Number.isFinite(trip.places[e.placeId].lat) && Number.isFinite(trip.places[e.placeId].lng)));
        assert.equal(trip.image, trip.places[trip.schedule[0].placeId].image);
      }
    };
    await check(); await page.reload(); await check();
    await page.evaluate(() => {
      const key='neorang-galjido-v1', state=JSON.parse(localStorage.getItem(key));
      state.posts=state.posts.filter(p=>p.id<900000000001);state.companions=state.companions.filter(p=>p.id<900000000001);state.plans=state.plans.filter(p=>!p.id.startsWith('demo-extra-plan-'));
      delete state.demoExpansionVersion;state.posts[0].title='기존 사용자 수정';state.companions[0].description='기존 모집글 수정';state.postComments[1]=[{id:'keep',content:'보존할 댓글'}];
      localStorage.setItem(key,JSON.stringify(state));
    });
    await page.reload(); await check();
    assert.ok(await page.evaluate(() => {const s=JSON.parse(localStorage.getItem('neorang-galjido-v1'));return s.posts[0].title==='기존 사용자 수정' && s.companions[0].description==='기존 모집글 수정' && s.postComments[1][0].id==='keep';}));
    await page.goto('http://localhost:5500/companion-detail.html?id=900000000001');
    assert.equal(await page.locator('#companion-day-buttons button').count(), 2);
    assert.ok((await page.locator('#companion-content').innerText()).includes('월정리'));
    await page.goto('http://localhost:5500/post-detail.html?id=900000000001');
    assert.ok((await page.locator('#post-article').innerText()).includes('바다를 따라'));
    assert.deepEqual(errors, []);
    console.log('PASS +30 posts/trips/plans; unique IDs; owner links; two-day routes/photos; repeat-load idempotency; existing edits/comments preserved; detail pages; no JS errors');
  } finally { await browser.close(); }
})().catch(e => {console.error(e);process.exit(1);});
