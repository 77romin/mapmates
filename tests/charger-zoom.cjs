const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(() => localStorage.setItem('neorang-intro-seen-v1', '1'));
    await page.route('**/js/app.js', async route => {
      const response = await route.fetch();
      const body = (await response.text()).replace('kakaoMap.setDraggable(true);', 'window.__testMap=kakaoMap;kakaoMap.setDraggable(true);');
      await route.fulfill({ response, body });
    });
    await page.goto('http://localhost:5500/index.html');
    await page.waitForFunction(() => window.__testMap);
    await page.evaluate(async () => {
      const { api } = await import('/js/api.js');
      api.getRegionalEvChargers = async () => {
        const center = window.__testMap.getCenter();
        return { items: [{ statId: 'zoom-test', statNm: '축척 검증 충전소', lat: center.getLat(), lng: center.getLng(), stat: '2' }], updatedAt: Date.now(), partial: false };
      };
    });
    await page.locator('[data-layer=charger]').click();
    await page.waitForSelector('.charger-map-marker');
    for (const level of [7, 9, 11, 13]) {
      await page.evaluate(level => window.__testMap.setLevel(level), level);
      await page.waitForFunction(() => document.querySelector('#charger-status').textContent.includes('충전소 1곳'));
      assert.equal(await page.locator('.charger-map-marker').count(), 1);
      assert.ok(!(await page.locator('#charger-status').innerText()).includes('확대하세요'));
    }
    await page.locator('[data-layer=charger]').click();
    assert.equal(await page.locator('.charger-map-marker').count(), 0);
    assert.deepEqual(errors, []);
    console.log('PASS chargers visible at map levels 7, 9, 11, 13; toggle cleanup; no JavaScript errors');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });
