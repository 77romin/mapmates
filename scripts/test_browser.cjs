const assert=require('node:assert/strict');
const fs=require('node:fs');const path=require('node:path');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base=process.env.TEST_URL || 'http://localhost:8080/mapmates';
const output=path.resolve('docs/screenshots/backend');fs.mkdirSync(output,{recursive:true});
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});const errors=[];const checks=[];const contexts=[];
 async function context(){const c=await browser.newContext({viewport:{width:1440,height:1000}});contexts.push(c);await c.addInitScript(()=>localStorage.setItem('neorang-intro-seen-v1','1'));await c.route('**/config.js',r=>r.fulfill({contentType:'text/javascript',body:'window.APP_CONFIG={};'}));return c;}
 const owner=await context(),guest=await context();const page=await owner.newPage();page.on('pageerror',e=>errors.push(e.message));
 const go=async(p,url)=>{await p.goto(base+'/'+url);await p.waitForFunction(()=>document.querySelector('.intro-link'));};
 const check=(name,value=true)=>{assert.ok(value,name);checks.push(name);console.log('PASS',name);};
 async function login(p,email){await go(p,'signup.html');await p.locator('#login-email').fill(email);await p.locator('#login-password').fill('travel1234');await p.locator('#login-form button[type=submit]').click();await p.waitForURL('**/mypage.html');await p.waitForFunction(()=>document.querySelector('#profile-heading').textContent.includes('여행친구'));}
 let planId,postId;
 try{
  await login(page,'traveler1@mapmates.test');check('server login and Korean profile',(await page.locator('#profile-heading').innerText()).includes('여행친구1'));
  const payload={title:'브라우저 통합 검증',startDate:'2026-11-14',endDate:'2026-11-14',capacity:3,budget:20000,region:'서울',theme:'문화',description:'DB와 연결된 여행계획',items:[{day:1,time:'10:00',memo:'출발',place:{id:125452,title:'청계산',lat:37.4416867721,lng:127.0543676446,image:'',address:'서울',contentTypeId:12}}]};
  const response=await owner.request.post(base+'/api/plans',{data:payload});assert.equal(response.status(),201);const plan=await response.json();planId=plan.id;
  await go(page,'planner.html');await page.locator(`[data-plan-id="${planId}"]`).click();await page.locator('#plan-title').fill('서버에 저장한 여행');await page.locator('#save-plan').click();await page.getByText('여행계획을 저장했어요.',{exact:true}).waitFor();
  await page.reload();await page.locator(`[data-plan-id="${planId}"]`).click();check('plan update survives reload',await page.locator('#plan-title').inputValue()==='서버에 저장한 여행');
  await page.screenshot({path:path.join(output,'01-plan.png'),fullPage:true});
  await page.locator('#publish-companion').click();await page.waitForURL('**/companion-detail.html?id=*');await page.locator('#owner-edit').waitFor({state:'visible'});const companionId=new URL(page.url()).searchParams.get('id');check('publish persisted plan');
  await page.screenshot({path:path.join(output,'02-companion-owner.png'),fullPage:true});
  const other=await guest.newPage();other.on('pageerror',e=>errors.push(e.message));await login(other,'traveler2@mapmates.test');await go(other,'companion-detail.html?id='+companionId);await other.locator('#join-companion').click();await other.getByRole('button',{name:'참가 취소',exact:true}).waitFor();check('second browser joins shared trip');
  await other.locator('#comment-content').fill('함께 여행해요!');await other.locator('#comment-form button[type=submit]').click();await other.locator('#comment-list').getByText('함께 여행해요!',{exact:true}).waitFor();
  await page.reload();await page.locator('#comment-list').getByText('함께 여행해요!',{exact:true}).waitFor();check('companion comment shared across browsers');
  await page.screenshot({path:path.join(output,'03-companion-joined.png'),fullPage:true});
  await go(page,'post-write.html');await page.locator('#post-category').fill('후기');await page.locator('#post-title').fill('브라우저 게시글');await page.locator('#post-content').fill('실제 서버에 여행 후기를 저장합니다.');await page.locator('#post-submit').click();await page.waitForURL('**/post-detail.html?id=*');postId=new URL(page.url()).searchParams.get('id');await page.locator('#post-article h1').waitFor();check('post creation navigates after server success');
  await page.locator('#comment-content').fill('서버 댓글');await page.locator('#comment-form button[type=submit]').click();await page.locator('#comment-list').getByText('서버 댓글',{exact:true}).waitFor();
  await other.goto(page.url());await other.locator('#comment-list').getByText('서버 댓글',{exact:true}).waitFor();check('post and comment persist across browsers');
  await other.screenshot({path:path.join(output,'04-community.png'),fullPage:true});
  const stored=await page.evaluate(()=>localStorage.getItem('neorang-galjido-v1-drafts'));check('browser storage contains no passwords',!stored.includes('travel1234'));
  const mobile=await context();const small=await mobile.newPage();await small.setViewportSize({width:390,height:844});await go(small,'companions.html');check('mobile companion list loads',await small.locator('#companion-list').isVisible());await small.screenshot({path:path.join(output,'05-mobile.png'),fullPage:true});
  await go(other,'index.html');await other.locator('button[data-workspace="browse"]').click();await other.locator(`[data-select-trip="${companionId}"]`).click();await other.locator('#workspace-join').click();await other.getByRole('button',{name:'동행하기',exact:true}).waitFor();await other.locator('#workspace-join').click();await other.getByRole('button',{name:'참가 중 · 다시 누르면 참가 취소',exact:true}).waitFor();check('map workspace toggles server participation twice');
  await go(other,'mypage.html');await other.locator('#logout-button').click();await other.waitForURL('**/index.html');await other.waitForFunction(()=>document.querySelector('.profile-button')?.getAttribute('href')?.includes('signup'));check('server logout clears session');
  check('no browser JavaScript errors',errors.length===0);console.log(JSON.stringify({checks:checks.length,errors},null,2));
 } finally {if(postId)await owner.request.delete(base+'/api/posts/'+postId);if(planId)await owner.request.delete(base+'/api/plans/'+planId);for(const c of contexts)await c.close();await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
