(() => {
  try {
    if (!localStorage.getItem('neorang-intro-seen-v1')) {
      const next = location.pathname.split('/').pop() || 'index.html';
      location.replace(`./intro.html?next=${encodeURIComponent(next + location.search)}`);
    }
  } catch { /* A blocked store must not cause an infinite redirect. */ }
})();
