const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    page.on('dialog', d => d.accept());
    await page.addInitScript(() => localStorage.setItem('neorang-intro-seen-v1', '1'));
    await page.route('**/config.js', r => r.fulfill({ body: 'window.APP_CONFIG={}', contentType: 'application/javascript' }));
    const go = url => page.goto('http://localhost:5500/' + url);
    const user = async (index, loggedIn = true) => page.evaluate(({ index, loggedIn }) => {
      const key = 'neorang-galjido-v1', state = JSON.parse(localStorage.getItem(key));
      state.loggedIn = loggedIn; state.user = state.members[index]; localStorage.setItem(key, JSON.stringify(state));
    }, { index, loggedIn });
    await go('index.html');
    for (const [url, collection] of [['companion-detail.html?id=1', 'companionComments'], ['post-detail.html?id=1', 'postComments']]) {
      await user(3); await go(url);
      await page.locator('#comment-content').fill('질문 <script>테스트</script>');
      await page.locator('#comment-form button').click();
      const root = page.locator('.comment-item').filter({ hasText: '질문 <script>테스트</script>' });
      await root.locator('[data-action=reply]').click();
      await page.locator('.reply-form textarea').fill('첫 답변');
      await page.locator('.reply-form [type=submit]').click();
      await user(4); await go(url);
      const question = page.locator('.comment-item').filter({ hasText: '질문 <script>테스트</script>' });
      assert.equal(await question.locator('[data-action=edit]').count(), 0);
      assert.equal(await question.locator('[data-action=delete]').count(), 0);
      await question.locator('[data-action=reply]').click();
      await page.locator('.reply-form textarea').fill('다른 회원 답변');
      await page.locator('.reply-form [type=submit]').click();
      const reply = page.locator('.is-reply').filter({ hasText: '다른 회원 답변' });
      await reply.locator('[data-action=edit]').click();
      await page.locator('.comment-edit-form textarea').fill('수정한 답변');
      await page.locator('.comment-edit-form [type=submit]').click();
      await page.reload(); assert.ok((await page.locator('#comment-list').innerText()).includes('수정한 답변'));
      await page.locator('.is-reply').filter({ hasText: '첫 답변' }).locator('[data-action=reply]').click();
      await page.locator('.reply-form textarea').fill('답글에 대한 답변');
      await page.locator('.reply-form [type=submit]').click();
      await user(3); await go(url);
      await page.locator('.comment-item').filter({ hasText: '질문 <script>테스트</script>' }).locator('[data-action=delete]').click();
      assert.ok((await page.locator('#comment-list').innerText()).includes('삭제된 댓글입니다.'));
      assert.ok((await page.locator('#comment-list').innerText()).includes('수정한 답변'));
      await page.reload(); assert.equal(await page.locator('.is-reply').count(), 3);
      assert.ok(await page.evaluate(collection => Object.values(JSON.parse(localStorage.getItem('neorang-galjido-v1'))[collection]).flat().some(c => c.parentId), collection));
      await page.setViewportSize({ width: 390, height: 844 });
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await page.screenshot({ path: 'docs/screenshots/' + (collection === 'companionComments' ? '18-companion-qa-mobile.png' : '19-community-replies-mobile.png'), fullPage: true });
      await user(3, false); await go(url);
      assert.equal(await page.locator('#comment-form').isVisible(), false);
      assert.equal(await page.locator('[data-action=edit]').count(), 0);
      assert.equal(await page.locator('[data-action=delete]').count(), 0);
      assert.ok((await page.locator('#comment-login a').getAttribute('href')).includes(encodeURIComponent(url)));
    }
    assert.deepEqual(errors, []);
    console.log('PASS companion Q&A and community replies: add, nested reply, ownership, edit, delete parent preserving replies, reload, escaped content, guest login return, mobile overflow, no JS errors');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
