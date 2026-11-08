import {serverMode,request} from './server-api.js';
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function mountNotifications(loggedIn){
 if(!serverMode||!loggedIn||document.querySelector('#notification-menu'))return;
 const profile=document.querySelector('.profile-button');if(!profile)return;
 const menu=document.createElement('details');menu.id='notification-menu';menu.className='notification-menu';
 menu.innerHTML='<summary aria-label="알림함">알림 <span id="notification-badge"></span></summary><section class="notification-panel" aria-label="알림 목록"><div class="notification-heading"><h2>알림함</h2><button type="button" id="notification-close" aria-label="알림함 닫기">닫기</button></div><button type="button" id="notification-read-all">모두 읽음</button><div id="notification-list" aria-live="polite"></div><button type="button" id="notification-more" hidden>더 보기</button></section>';profile.before(menu);
 const list=menu.querySelector('#notification-list'),more=menu.querySelector('#notification-more');
 let page=0,items=[],generation=0;
 async function load(reset=true){
  const ticket=++generation,nextPage=reset?1:page+1;
  menu.querySelector('.notification-panel').setAttribute('aria-busy','true');more.disabled=true;
  try{
   const data=await request(`notifications?page=${nextPage}&size=20`);if(ticket!==generation)return;
   page=nextPage;items=reset?data.items:[...items,...data.items];
   menu.querySelector('#notification-badge').textContent=data.unread?`(${data.unread})`:'';
   list.innerHTML=items.map(n=>`<a href="./${escape(n.link)}" data-read="${n.id}" class="${n.readAt?'':'is-unread'}">${escape(n.title)}<small>${escape(n.createdAt)}</small></a>`).join('')||'<p>새 알림이 없어요.</p>';
   more.hidden=items.length>=data.total;
  }catch(e){if(ticket!==generation)return;list.textContent=e.message;const retry=document.createElement('button');retry.type='button';retry.textContent='다시 불러오기';retry.onclick=()=>load();list.append(retry);}
  finally{if(ticket===generation){more.disabled=false;menu.querySelector('.notification-panel').setAttribute('aria-busy','false');}}
 }
 const close=()=>{menu.open=false;menu.querySelector('summary').focus();};
 menu.querySelector('#notification-close').onclick=close;
 document.addEventListener('keydown',e=>{if(e.key==='Escape'&&menu.open)close();});
 document.addEventListener('click',e=>{if(menu.open&&!menu.contains(e.target))menu.open=false;});
 menu.addEventListener('toggle',()=>{if(menu.open)load();});
 menu.querySelector('#notification-read-all').onclick=async e=>{const button=e.currentTarget;button.disabled=true;try{await request('notifications/read-all','PUT',{});await load();}catch(error){list.textContent=error.message;}finally{button.disabled=false;}};
 more.onclick=()=>load(false);
 menu.addEventListener('click',async e=>{const a=e.target.closest('[data-read]');if(!a)return;e.preventDefault();if(a.getAttribute('aria-busy')==='true')return;a.setAttribute('aria-busy','true');try{await request(`notifications/${a.dataset.read}/read`,'PUT',{});location.href=a.href;}catch(error){a.removeAttribute('aria-busy');list.textContent=error.message;}});
 load();setInterval(()=>{if(!document.hidden&&!menu.open)load();},30000);
}
