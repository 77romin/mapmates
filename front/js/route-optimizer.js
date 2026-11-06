// Exact shortest open path with the first and last stop held fixed.
export function validCoordinates(place) {
  return place && place.lat != null && place.lng != null && String(place.lat).trim() !== '' && String(place.lng).trim() !== ''
    && Number.isFinite(Number(place.lat)) && Number.isFinite(Number(place.lng))
    && Math.abs(Number(place.lat)) <= 90 && Math.abs(Number(place.lng)) <= 180;
}

export function distanceKm(a, b) {
  const rad = value => value * Math.PI / 180;
  const value = Math.sin(rad(b.lat - a.lat) / 2) ** 2
    + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(rad(b.lng - a.lng) / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, value))));
}

export function optimizeRoute(points) {
  if (points.length < 3 || points.length > 12) throw new Error('하루 3~12곳의 일정에서 사용할 수 있어요.');
  if (!points.every(validCoordinates)) throw new Error('실제 좌표가 없는 장소를 확인해주세요.');
  const n = points.length, m = n - 2, states = 1 << m;
  const distances = points.map(a => points.map(b => distanceKm(a, b)));
  const cost = order => order.slice(1).reduce((sum, id, i) => sum + distances[order[i]][id], 0);
  const dp = new Float64Array(states * m).fill(Infinity);
  const parent = new Int16Array(states * m).fill(-1);
  for (let j = 0; j < m; j++) dp[(1 << j) * m + j] = distances[0][j + 1];
  for (let mask = 1; mask < states; mask++) {
    for (let j = 0; j < m; j++) {
      if (!(mask & (1 << j))) continue;
      const previous = mask ^ (1 << j);
      if (!previous) continue;
      for (let k = 0; k < m; k++) {
        if (!(previous & (1 << k))) continue;
        const candidate = dp[previous * m + k] + distances[k + 1][j + 1];
        if (candidate < dp[mask * m + j]) {
          dp[mask * m + j] = candidate;
          parent[mask * m + j] = k;
        }
      }
    }
  }
  let last = 0, best = Infinity;
  for (let j = 0; j < m; j++) {
    const candidate = dp[(states - 1) * m + j] + distances[j + 1][n - 1];
    if (candidate < best) { best = candidate; last = j; }
  }
  const middle = [];
  let mask = states - 1;
  while (mask) {
    middle.push(last + 1);
    const previous = parent[mask * m + last];
    mask ^= 1 << last;
    last = previous;
  }
  const original = Array.from({length:n}, (_, i) => i);
  let order = [0, ...middle.reverse(), n - 1];
  const beforeKm = cost(original);
  // Keep existing order on ties, including identical coordinates.
  if (cost(order) >= beforeKm - 1e-9) order = original;
  return { order, beforeKm, afterKm: cost(order), allocatedStates: states * m };
}

// Preserve clock slots and per-place notes. Every screen already sorts by time.
export function assignTimeSlots(schedule, order) {
  return order.map((source, slot) => ({...schedule[source], time:schedule[slot].time}));
}
