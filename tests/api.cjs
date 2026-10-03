const assert = require('node:assert/strict');
const {pathToFileURL} = require('node:url');
const path = require('node:path');
(async()=> {
 global.window={APP_CONFIG:{EV_CHARGER_API_KEY:'test-key', WEATHER_API_KEY:'test-key'}};
 let requests=0, active=0, maxActive=0, fail=false;
 global.fetch=async url=>{
   const u=new URL(url);assert.equal(u.searchParams.get('zcode'),u.pathname.includes('Charger')?'11':null);
   requests++;active++;maxActive=Math.max(active,maxActive);
   await new Promise(resolve=>setTimeout(resolve,3));active--;
   if(fail)return {ok:false,status:503};
   if(u.pathname.includes('getVilageFcst'))return {ok:true,json:async()=>({response:{body:{items:{item:[{category:'TMP',fcstValue:'20'}]}}}})};
   const n=Number(u.searchParams.get('numOfRows'));return {ok:true,json:async()=>({items:{item:Array.from({length:n},(_,i)=>({statId:`${u.searchParams.get('pageNo')}-${i}`,lat:'37.5',lng:'127',stat:'2'}))},totalCount:6000,resultCode:'00'})};
 };
 const {api}=await import(pathToFileURL(path.resolve('js/api.js')));
 let progress=0;const result=await Promise.all([api.getRegionalEvChargers({zcode:'11',onProgress:()=>progress++}),api.getRegionalEvChargers({zcode:'11'})]);
 assert.equal(requests,8);assert.equal(result[0].items.length,4000);assert.equal(result[0].partial,true);assert.ok(maxActive<=2);assert.ok(progress>=2);
 await api.getRegionalEvChargers({zcode:'11'});assert.equal(requests,8);
 const now=Date.now;Date.now=()=>now()+121000;fail=true;
 await assert.rejects(api.getRegionalEvChargers({zcode:'11'}));fail=false;await api.getRegionalEvChargers({zcode:'11'});assert.equal(requests,17);Date.now=now;
 const weather={nx:60,ny:127,baseDate:'20261003',baseTime:'0200'};
 await api.getWeather(weather);const count=requests;await api.getWeather(weather);assert.equal(requests,count);
 console.log('PASS region filter, request merging, max 2 simultaneous requests, 8-page/4000-row cap, progressive results, partial flag, 2-minute cache, failure retry, weather cache');
})().catch(e=>{console.error(e);process.exit(1)});
