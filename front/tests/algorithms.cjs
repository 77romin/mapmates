const assert = require('node:assert/strict');
const {performance} = require('node:perf_hooks');
(async () => {
  const {optimizeRoute, distanceKm, assignTimeSlots} = await import('../js/route-optimizer.js');
  const {topK} = await import('../js/top-k.js');
  const {popularPlaces, comparePopularity} = await import('../js/popular-places.js');
  const {places} = await import('../js/data.js');
  const tied=Array.from({length:30},(_,id)=>({id}));
  assert.deepEqual(topK(tied,10,()=>0),tied.slice(0,10));
  let seed = 42;
  const random = () => ((seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0) / 2 ** 32);
  function exhaustive(points) {
    let best = Infinity;
    function visit(last, remaining, cost) {
      if (!remaining.length) { best = Math.min(best, cost + distanceKm(points[last], points.at(-1))); return; }
      for (const next of remaining) visit(next, remaining.filter(id => id !== next), cost + distanceKm(points[last], points[next]));
    }
    visit(0, Array.from({length:points.length-2}, (_, i) => i+1), 0);
    return best;
  }
  for (let n = 3; n <= 8; n++) for (let trial = 0; trial < 5; trial++) {
    const points = Array.from({length:n}, () => ({lat:33+random(), lng:126+random()}));
    const result = optimizeRoute(points);
    assert.ok(Math.abs(result.afterKm-exhaustive(points)) < 1e-8);
    assert.equal(result.order[0], 0); assert.equal(result.order.at(-1), n-1);
    assert.equal(new Set(result.order).size, n);
    assert.ok(result.afterKm <= result.beforeKm+1e-8);
  }
  assert.throws(() => optimizeRoute([{lat:null,lng:1}, {}, {}]));
  assert.throws(() => optimizeRoute(Array(13).fill({lat:0,lng:0})));
  assert.deepEqual(optimizeRoute(Array(4).fill({lat:0,lng:0})).order,[0,1,2,3]);
  const schedule = Array.from({length:4}, (_, i) => ({placeId:i,day:1,time:`${9+i}:00`,memo:`memo${i}`}));
  const changed = assignTimeSlots(schedule,[0,2,1,3]);
  assert.deepEqual(changed.map(item=>item.time),schedule.map(item=>item.time));
  assert.equal(changed[1].memo,'memo2'); assert.equal(schedule[1].placeId,1);
  for (let trial=0; trial<30; trial++) {
    const items = Array.from({length:trial*31}, (_,id)=>({id,title:`장소 ${id}`,userCount:Math.floor(random()*10),planCount:Math.floor(random()*20)}));
    for (const k of [0,1,10,1000]) assert.deepEqual(topK(items,k,comparePopularity),[...items].sort(comparePopularity).slice(0,k));
  }
  const bounds={south:32,west:125,north:35,east:128};
  const plans = Array.from({length:200},(_,i)=>({ownerId:`u${i%50}`,tripSchedule:[{placeId:i%20},{placeId:i%20}],itineraryPlaces:{[i%20]:{id:i%20,title:`장소 ${i%20}`,lat:33.5,lng:126.5}}}));
  assert.deepEqual(popularPlaces(plans,bounds,10),popularPlaces(plans,bounds,1000).slice(0,10));
  const routePoints = places.slice(0,8);
  const route = optimizeRoute(routePoints);
  function medianRun(fn, rounds=31) {
    for(let i=0;i<5;i++) fn();
    return Array.from({length:rounds},()=>{const start=performance.now();fn();return performance.now()-start;}).sort((a,b)=>a-b)[Math.floor(rounds/2)];
  }
  const items=Array.from({length:100000},(_,id)=>({id,title:`장소 ${id}`,userCount:Math.floor(random()*1000),planCount:Math.floor(random()*1000)}));
  let sortComparisons=0,heapComparisons=0;
  const expected=[...items].sort((a,b)=>{sortComparisons++;return comparePopularity(a,b);}).slice(0,10);
  const actual=topK(items,10,(a,b)=>{heapComparisons++;return comparePopularity(a,b);});
  assert.deepEqual(actual,expected);
  const metrics={fixture:'data.js first 8 places, fixed endpoints; synthetic 100000 ranked places, seed 42',node:process.version,
    route:{beforeKm:route.beforeKm,afterKm:route.afterKm,reductionPercent:(1-route.afterKm/route.beforeKm)*100,order:route.order.map(i=>routePoints[i].title),dpMedianMs:medianRun(()=>optimizeRoute(routePoints)),exhaustiveMedianMs:medianRun(()=>exhaustive(routePoints)),allocatedStates:route.allocatedStates},
    ranking:{candidates:items.length,k:10,sortComparisons,heapComparisons,sortMedianMs:medianRun(()=>[...items].sort(comparePopularity)),heapMedianMs:medianRun(()=>topK(items,10,comparePopularity))}};
  console.log('PASS exhaustive cross-check (30 routes), bounds/ties/top K, invalid input, slot and memo preservation');
  console.log(JSON.stringify(metrics,null,2));
})().catch(error=>{console.error(error);process.exit(1);});
