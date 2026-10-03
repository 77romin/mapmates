import { places } from './data.js';
import { escapeHtml, saveState } from './shared.js';
import { TripRoute, tripDays, dayDate, renderDayWeather } from './trip-map.js';

export function mountWorkspace(state, getMap, onChange) {
  const panel = document.querySelector('#map-workspace');
  const explore = document.querySelector('#explore-panel-content');
  const status = document.createElement('p');
  status.id = 'workspace-route-status';
  status.className = 'workspace-map-status';
  status.hidden = true;
  document.querySelector('#map-stage').append(status);
  const route = new TripRoute(getMap, document.querySelector('#route-layer'), status);
  let tab = 'explore', selected = null, day = 1, mode = 'straight';

  const companionFor = plan => state.companions.find(item => item.tripId === plan.id);
  const normalize = plan => ({
    ...plan.trip, id: plan.id, ownerId: plan.ownerId,
    region: plan.trip.region || companionFor(plan)?.region || '기타',
    description: plan.trip.description || companionFor(plan)?.description || '',
    image: companionFor(plan)?.image || Object.values(plan.itineraryPlaces || {})[0]?.image,
    schedule: plan.tripSchedule || [], places: plan.itineraryPlaces || {},
  });
  const findPlace = (id, record) => record.places?.[id] || state.itineraryPlaces?.[id] || places.find(place => place.id === Number(id));
  const entries = () => (selected?.schedule || [])
    .filter(entry => Number(entry.day || 1) === day)
    .sort((a, b) => String(a.time).localeCompare(String(b.time)))
    .map(entry => ({ ...entry, place: findPlace(entry.placeId, selected) }));

  function draw() {
    if (tab === 'explore' || !selected) { route.clear(); status.hidden = true; return; }
    status.hidden = false;
    const stops = entries();
    route.draw(stops, mode);
    const weather = panel.querySelector('#workspace-weather');
    if (weather) renderDayWeather(weather, stops[0]?.place || (selected.schedule[0] && findPlace(selected.schedule[0].placeId, selected)), dayDate(selected, day), selected.region);
  }

  function openPlanner(record) {
    if (record) {
      state.trip = { ...record };
      delete state.trip.schedule; delete state.trip.places;
      state.tripSchedule = structuredClone(record.schedule || []);
      state.itinerary = state.tripSchedule.map(entry => entry.placeId);
      state.itineraryPlaces = structuredClone(record.places || {});
      saveState(state);
    }
    location.href = './planner.html';
  }

  function render() {
    const heading = tab === 'browse' ? '둘러보기' : '내 여행';
    panel.innerHTML = `<p class="eyebrow">${tab === 'browse' ? 'TRAVEL TOGETHER' : 'MY JOURNEYS'}</p>
      <div class="section-title-row"><h2>${heading}</h2><button type="button" class="icon-button mobile-panel-close" id="workspace-close" aria-label="목록 접고 지도 보기">×</button></div>
      <div id="workspace-content"></div>`;
    panel.querySelector('#workspace-close').onclick = () => document.querySelector('.search-panel').classList.remove('is-open');
    const content = panel.querySelector('#workspace-content');
    if (!selected) {
      const records = tab === 'browse' ? state.companions : state.loggedIn ? state.plans.filter(plan => plan.ownerId === state.user.id).map(normalize) : [];
      content.innerHTML = `${tab === 'mine' ? '<a class="button button-primary full-width" id="workspace-new" href="./planner.html?new=1">＋ 새 여행 작성</a><button type="button" class="text-button" id="workspace-resume">작성 중인 여행 이어쓰기</button>' : ''}
        <div id="workspace-cards" class="workspace-card-grid">${records.map(record => `
          <button type="button" class="workspace-trip-card" data-select-trip="${escapeHtml(record.id)}">
            <img src="${escapeHtml(record.image || './assets/sunset-clouds.png')}" alt="" loading="lazy">
            <span class="workspace-card-body"><span class="workspace-card-region">${escapeHtml(record.region || '여행')}</span><strong>${escapeHtml(record.title)}</strong><span class="workspace-card-dates">${escapeHtml(record.startDate || '')} — ${escapeHtml(record.endDate || '')}</span><span class="workspace-card-meta">${tab === 'browse' ? escapeHtml(`${record.author || '여행자'} · ${record.people || ''}`) : `${tripDays(record).length}일 여행 · ${(record.schedule || []).length}개 장소`}</span></span>
          </button>`).join('') || `<p class="helper-text">${tab === 'mine' && !state.loggedIn ? '로그인하면 내 여행을 저장하고 공유할 수 있어요.' : '등록된 여행이 없어요.'}</p>`}</div>`;
      content.querySelectorAll('[data-select-trip]').forEach(button => button.onclick = () => {
        selected = records.find(record => String(record.id) === button.dataset.selectTrip);
        day = 1; mode = 'straight'; render(); panel.scrollTop = 0; draw();
      });
      content.querySelector('#workspace-resume')?.addEventListener('click', () => openPlanner());
      content.querySelectorAll('img').forEach(img => img.addEventListener('error', () => { if (!img.dataset.fallback) { img.dataset.fallback = '1'; img.src = './assets/sunset-clouds.png'; } }));
      return;
    }
    content.innerHTML = `<button class="text-button workspace-back" id="workspace-back" type="button">← ${heading} 목록</button>
      <article class="workspace-selected-trip"><img class="workspace-selected-photo" src="${escapeHtml(selected.image || './assets/sunset-clouds.png')}" alt=""><p class="workspace-card-region">${escapeHtml(selected.region || '여행')}</p><h3>${escapeHtml(selected.title)}</h3><p class="helper-text">${escapeHtml(selected.startDate)} — ${escapeHtml(selected.endDate)}${tab === 'browse' ? ` · ${escapeHtml(selected.people || '')}` : ''}</p>${selected.description ? `<p class="workspace-trip-description">${escapeHtml(selected.description)}</p>` : ''}</article>
      <div class="workspace-route-toggle" role="group" aria-label="여행 경로 선택"><button type="button" data-workspace-mode="straight" aria-pressed="${mode === 'straight'}">직선</button><button type="button" data-workspace-mode="car" aria-pressed="${mode === 'car'}">차량경로</button></div>
      <div class="trip-day-buttons">${tripDays(selected).map(value => `<button type="button" data-workspace-day="${value}" aria-pressed="${value === day}">DAY ${value}</button>`).join('')}</div>
      <p id="workspace-weather" class="trip-day-weather" role="status"></p>
      <ol class="workspace-stops">${entries().map(entry => `<li><strong>${escapeHtml(entry.place?.title || '장소')}</strong><span>${escapeHtml(entry.time || '')}</span></li>`).join('') || '<li>이 날짜의 장소가 없어요.</li>'}</ol>
      ${tab === 'browse' ? `<a class="button button-primary full-width workspace-detail-button" href="./companion-detail.html?id=${encodeURIComponent(selected.id)}">세부내용 보기</a>` : '<button type="button" class="button button-primary full-width workspace-detail-button" id="workspace-plan-detail">세부내용 보기</button>'}`;
    content.querySelector('#workspace-back').onclick = () => { selected = null; render(); panel.scrollTop = 0; draw(); };
    content.querySelectorAll('[data-workspace-mode]').forEach(button => button.onclick = () => { mode = button.dataset.workspaceMode; render(); draw(); });
    content.querySelectorAll('[data-workspace-day]').forEach(button => button.onclick = () => { day = Number(button.dataset.workspaceDay); render(); draw(); });
    content.querySelector('#workspace-plan-detail')?.addEventListener('click', () => openPlanner(selected));
    const photo = content.querySelector('.workspace-selected-photo');
    photo.addEventListener('error', () => { if (!photo.dataset.fallback) { photo.dataset.fallback = '1'; photo.src = './assets/sunset-clouds.png'; } });
  }

  function switchTab(next) {
    tab = next; selected = null;
    explore.hidden = tab !== 'explore'; panel.hidden = tab === 'explore';
    document.querySelectorAll('[data-workspace]').forEach(button => button.classList.toggle('is-active', button.dataset.workspace === tab));
    document.querySelector('.search-panel').classList.add('is-open');
    render(); panel.scrollTop = 0; draw(); onChange(); getMap()?.relayout();
  }
  document.querySelectorAll('[data-workspace]').forEach(button => button.addEventListener('click', event => { event.preventDefault(); switchTab(button.dataset.workspace); }));
  return { refresh: draw, show: switchTab, resume: () => openPlanner(), get tab() { return tab; } };
}
