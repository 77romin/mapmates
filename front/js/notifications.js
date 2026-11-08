import {serverMode,request} from './server-api.js';
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function mountNotifications(loggedIn){
 if(!serverMode||!loggedIn||document.querySelector('#notification-menu'))return;
 const profile=document.querySelector('.profile-button');if(!profile)return;
 const menu=document.createElement('details');menu.id='notification-menu';menu.className='notification-menu';menu.innerHTML='<summary aria-label="알림함">알림 <span id="notification-badge"></span></summary><section class="notification-panel"><h2>알림함</h2><button type="button" id="notification-read-all">모두 읽음</button><div id="notification-list" aria-live="polite"></div><button type="button" id="notification-more" hidden>더 보기</button></section>';profile.before(menu);
 let page=1,items=[];async function load(reset=true){try{if(reset){page=1;items=[];}const data=await request(`notifications?page=${page}&size=20`);items.push(...data.items);menu.querySelector('#notification-badge').textContent=data.unread?`(${data.unread})`:'';menu.querySelector('#notification-list').innerHTML=items.map(n=>`<a href="./${escape(n.link)}" data-read="${n.id}" class="${n.readAt?'':'is-unread'}">${escape(n.title)}<small>${escape(n.createdAt)}</small></a>`).join('')||'<p>새 알림이 없어요.</p>';menu.querySelector('#notification-more').hidden=items.length>=data.total;}catch(e){menu.querySelector('#notification-list').textContent=e.message;}}
 menu.addEventListener('toggle',()=>{if(menu.open)load();});menu.querySelector('#notification-read-all').onclick=async()=>{try{await request('notifications/read-all','PUT',{});await load();}catch(e){menu.querySelector('#notification-list').textContent=e.message;}};
 menu.querySelector('#notification-more').onclick=()=>{page++;load(false);};
 menu.addEventListener('click',async e=>{const a=e.target.closest('[data-read]');if(!a)return;e.preventDefault();try{await request(`notifications/${a.dataset.read}/read`,'PUT',{});location.href=a.href;}catch(error){menu.querySelector('#notification-list').textContent=error.message;}});
 load();setInterval(()=>{if(!document.hidden&&!menu.open)load();},30000);
}
