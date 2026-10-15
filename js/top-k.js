// compare(a,b) < 0 means a ranks before b. Root stores the worst retained item.
export function topK(items, limit, compare) {
  const k = Math.max(0, Math.floor(limit));
  if (!k) return [];
  const heap = [];
  const rank = (a,b) => compare(a.value,b.value) || a.index-b.index;
  function sink(index) {
    while (index * 2 + 1 < heap.length) {
      let child = index * 2 + 1;
      if (child + 1 < heap.length && rank(heap[child + 1], heap[child]) > 0) child++;
      if (rank(heap[index], heap[child]) >= 0) break;
      [heap[index], heap[child]] = [heap[child], heap[index]];
      index = child;
    }
  }
  let sequence = 0;
  for (const value of items) {
    const item = {value,index:sequence++};
    if (heap.length < k) {
      heap.push(item);
      let index = heap.length - 1;
      while (index > 0) {
        const parent = (index - 1) >> 1;
        if (rank(heap[parent], heap[index]) >= 0) break;
        [heap[parent], heap[index]] = [heap[index], heap[parent]];
        index = parent;
      }
    } else if (rank(item, heap[0]) < 0) {
      heap[0] = item;
      sink(0);
    }
  }
  return heap.sort(rank).map(item=>item.value);
}
