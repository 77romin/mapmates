const assert=require('node:assert/strict'),fs=require('node:fs');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const base=process.env.TEST_URL||'http://localhost:8080/mapmates';
const mail=process.env.MAILPIT_URL||'http://localhost:8026';
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 const context=await browser.newContext({viewport:{width:320,height:844},isMobile:true,hasTouch:true});
 await context.addInitScript(()=>localStorage.setItem('neorang-intro-seen-v1','1'));
 await context.route('**/config.js',r=>r.fulfill({contentType:'text/javascript',body:'window.APP_CONFIG={};'}));
 const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 const email='mobile-'+Date.now()+'@mapmates.test';let password='mobile1234',created=false;
 const check=s=>console.log('PASS',s);
 try{
  const response=await context.request.post(base+'/api/members',{data:{email,password,name:'모바일 검증',nickname:'모바일'+Date.now(),birthDate:'2000-01-01',gender:'female'}});assert.equal(response.status(),201);created=true;
  await context.request.post(base+'/api/auth/logout',{data:{}});
  await page.goto(base+'/signup.html');await page.locator('.recovery-card summary').click();
  await page.locator('#recover-email').fill(email);const button=page.locator('#recover-form button');
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));check('320px recovery form has no horizontal overflow');
  let release,intercepted;const gate=new Promise(r=>release=r),seen=new Promise(r=>intercepted=r);let submissions=0;
  await page.route('**/api/auth/password-reset/request',async route=>{submissions++;intercepted();await gate;await route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:'UNAVAILABLE',message:'메일 요청을 다시 시도해주세요.'})});});
  await button.click();await seen;assert.ok(await button.isDisabled());
  await page.locator('#recover-form').dispatchEvent('submit');assert.equal(submissions,1);check('slow recovery request prevents duplicate submission');
  release();await page.getByText('메일 요청을 다시 시도해주세요.',{exact:true}).waitFor();await page.waitForFunction(()=>!document.querySelector('#recover-form button').disabled);check('recovery error is visible and retry is enabled');
  await page.unroute('**/api/auth/password-reset/request');
  await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/auth/password-reset/request')&&r.status()===200),button.click()]);
  let token;
  for(let n=0;n<40&&!token;n++){
   const data=await (await context.request.get(mail+'/api/v1/messages')).json();
   for(const message of data.messages||[]){if(!(message.To||[]).some(to=>to.Address===email))continue;const body=await (await context.request.get(mail+'/api/v1/message/'+message.ID)).json();token=body.Text?.match(/reset=([A-Za-z0-9_-]{43})/)?.[1];}
   if(!token)await new Promise(r=>setTimeout(r,250));
  }
  assert.ok(token,'Local SMTP message received');check('recovery email arrives through local SMTP');
  await page.goto(base+'/signup.html?reset='+token);await page.locator('#recover-password').fill('mobileChanged1234');
  assert.ok(await page.locator('#recover-email').isHidden());
  await page.locator('#recover-form button').click();await page.waitForURL('**/signup.html?mode=login&resetDone=1');password='mobileChanged1234';check('mobile reset form saves new password');
  await page.locator('#login-email').fill(email);await page.locator('#login-password').fill(password);await page.locator('#login-form button[type=submit]').click();await page.waitForURL('**/mypage.html');await page.locator('#profile-heading').getByText(/모바일/).waitFor();check('new password signs in on mobile');
  await context.request.post(base+'/api/auth/logout',{data:{}});
  await page.goto(base+'/signup.html');await page.locator('.demo-card > .helper-text').waitFor({state:'attached'});assert.match(await page.locator('.demo-card > .helper-text').textContent(),/서버에 저장/);check('server demo instructions describe server storage');
  fs.mkdirSync('docs/screenshots/mobile',{recursive:true});await page.screenshot({path:'docs/screenshots/mobile/04-account.png',fullPage:true});
  assert.deepEqual(errors,[]);check('no mobile JavaScript errors');
 }finally{
  if(created){await context.request.post(base+'/api/auth/login',{data:{email,password}});await context.request.delete(base+'/api/members/me',{data:{currentPassword:password}});}
  await context.close();await browser.close();
 }
})().catch(e=>{console.error(e.name+': '+e.message.replace(/reset=[A-Za-z0-9_-]+/g,'reset=[redacted]'));process.exitCode=1;});
