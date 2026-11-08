// Same-origin Servlet API. A 404 on /auth/me means the standalone frontend server.
const root = new URL('../api/', import.meta.url);
export let serverMode = false;
let snapshot = null;
export async function request(path, method = 'GET', body) {
  const response = await fetch(new URL(path.replace(/^\//, ''), root), {
    method, credentials: 'same-origin', headers: { Accept: 'application/json', ...(method !== 'GET' ? {'Content-Type':'application/json'} : {}) },
    ...(body === undefined ? {} : {body:JSON.stringify(body)}), signal:AbortSignal.timeout(20000),
  });
  const data = response.status === 204 || response.headers.get('content-length') === '0' ? null : await response.json().catch(()=>null);
  if (!response.ok) throw new Error(data?.message || `서버 요청에 실패했어요 (${response.status}).`);
  return data;
}
const member = value => value ? {...value,id:String(value.id)} : {id:'',name:'',nickname:'',photo:''};
export async function refresh() {
  const auth=await request('auth/me');
  const page=location.pathname.split('/').pop();
  const empty={items:[],total:0,page:1,size:20};
  const [postPage,companionPage,plans,favorites,activity]=await Promise.all([
    auth.loggedIn&&page==='mypage.html'?request('posts?owner=me&size=100'):Promise.resolve(empty),
    ['index.html','companions.html','planner.html','mypage.html',''].includes(page)?request('companions?size=20'+(['planner.html','mypage.html'].includes(page)&&auth.loggedIn?'&related=me':'')):Promise.resolve(empty),
    auth.loggedIn&&['planner.html','index.html',''].includes(page)?request('plans'):Promise.resolve([]),
    auth.loggedIn&&['planner.html','index.html',''].includes(page)?request('favorites'):Promise.resolve([]),
    auth.loggedIn&&page==='mypage.html'?request('members/me/activity'):Promise.resolve(null)]);
  const posts=postPage.items,companions=companionPage.items;
  snapshot={postPage,companionPage,activity,loggedIn:auth.loggedIn,user:member(auth.member),members:auth.member?[member(auth.member)]:[],posts,companions,plans,favorites:favorites.map(p=>Number(p.id)),favoritePlaces:Object.fromEntries(favorites.map(p=>[p.id,p])),postComments:{},companionComments:{},joinedCompanions:companions.filter(p=>p.participants.some(v=>v.id===String(auth.member?.id))).map(p=>p.id)};
  const params=new URLSearchParams(location.search),id=params.get('id'),edit=params.get('edit');
  if(location.pathname.endsWith('post-detail.html')&&id){try{const post=await request(`posts/${id}`);snapshot.posts=snapshot.posts.filter(p=>String(p.id)!==id);snapshot.posts.push(post);snapshot.postComments[id]=await request(`posts/${id}/comments`);}catch(e){if(!snapshot.posts.some(p=>String(p.id)===id))return snapshot;throw e;}}
  if(location.pathname.endsWith('post-write.html')&&edit&&!snapshot.posts.some(p=>String(p.id)===edit))snapshot.posts.push(await request(`posts/${edit}`));
  if(location.pathname.endsWith('companion-detail.html')&&id){try{const cp=await request(`companions/${id}`);snapshot.companions=snapshot.companions.filter(p=>String(p.id)!==id);snapshot.companions.push(cp);snapshot.companionComments[id]=await request(`companions/${id}/comments`);}catch(e){if(!snapshot.companions.some(p=>String(p.id)===id))return snapshot;throw e;}}
  return snapshot;
}
export function serverSnapshot(){return snapshot;}
export const ready=(async()=>{
  const response=await fetch(new URL('auth/me',root),{credentials:'same-origin',signal:AbortSignal.timeout(10000)});
  if(response.status===404)return;
  if(!response.ok)throw new Error('서버에 연결하지 못했어요. 새로고침해주세요.');
  serverMode=true;await refresh();
})();
export function placePayload(place){
 const types={attraction:12,culture:14,festival:15,course:25,stay:32,shopping:38,food:39};
 return {id:Number(place.contentId || place.id),title:place.title,lat:Number(place.lat),lng:Number(place.lng),image:place.image||'',address:place.address||place.region||'',contentTypeId:Number(place.contentTypeId)||types[place.category || place.type]||12};
}
export function planPayload(state){
 return {version:state.trip.version??null,title:state.trip.title,startDate:state.trip.startDate,endDate:state.trip.endDate,capacity:Number(state.trip.people),budget:Number(state.trip.budget||0),region:state.trip.region,theme:state.trip.theme,description:state.trip.description,items:state.tripSchedule.map(s=>({day:Number(s.day),time:s.time||null,memo:s.memo||'',place:placePayload(state.itineraryPlaces[s.placeId])}))};
}
