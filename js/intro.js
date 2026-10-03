const params = new URLSearchParams(location.search);
const next = params.get('next');
const target = next && /^(index|planner|companions|companion-detail|community|post-detail|post-write|signup|mypage)\.html(?:\?[^#]*)?$/.test(next) ? `./${next}` : './index.html';
document.querySelectorAll('[data-enter]').forEach(link => {
  if (next) link.href = target;
  link.addEventListener('click', () => { try { localStorage.setItem('neorang-intro-seen-v1','1'); } catch {} });
});
// Once a visitor has seen this page, subsequent app visits go straight to their destination.
try { localStorage.setItem('neorang-intro-seen-v1','1'); } catch {}
