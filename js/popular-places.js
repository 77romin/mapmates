// Count saved plans, while counting each member only once per place.
export function popularPlaces(plans, bounds, limit = 10) {
  if (!bounds) return [];
  const grouped = new Map();
  for (const plan of plans || []) {
    if (!plan.ownerId) continue;
    const seen = new Set();
    for (const entry of plan.tripSchedule || []) {
      const place = plan.itineraryPlaces?.[entry.placeId];
      if (!place || place.lat == null || place.lng == null) continue;
      const lat = Number(place.lat), lng = Number(place.lng);
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat < bounds.south || lat > bounds.north || lng < bounds.west || lng > bounds.east) continue;
      const key = `${String(place.title || '').replace(/\s+/g,'').toLowerCase()}:${lat.toFixed(4)}:${lng.toFixed(4)}`;
      if (seen.has(key)) continue;
      seen.add(key);
      if (!grouped.has(key)) grouped.set(key, { place: { ...place, id: place.id ?? Number(entry.placeId), lat, lng }, members: new Set(), planCount: 0 });
      const item = grouped.get(key);
      item.members.add(plan.ownerId); item.planCount++;
    }
  }
  return [...grouped.values()].map(item => ({ ...item.place, userCount: item.members.size, planCount: item.planCount }))
    .sort((a,b) => b.userCount-a.userCount || b.planCount-a.planCount || String(a.title).localeCompare(String(b.title),'ko'))
    .slice(0, limit);
}
