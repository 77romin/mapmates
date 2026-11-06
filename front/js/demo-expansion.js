import { companions, members } from './data.js';

const ideas = [
  ['제주', '바다를 따라 걷는 동쪽 산책', '자연', '해변 산책과 숲길을 번갈아 즐기는 일정'],
  ['부산', '광안리부터 영도까지 로컬 맛집', '맛집', '바다 전망과 골목 식당을 함께 만나는 일정'],
  ['강원', '강릉 커피와 가을 바다', '맛집', '커피거리에서 쉬고 해변을 걷는 일정'],
  ['서울', '고궁과 숲을 잇는 문화 산책', '문화', '고궁 관람 뒤 숲과 강변에서 쉬는 일정'],
  ['제주', '월정리에서 시작하는 사진 여행', '사진', '오전 빛과 오후 풍경을 천천히 담는 일정'],
  ['부산', '흰여울 골목의 느린 하루', '문화', '골목 풍경과 책방을 둘러보는 일정'],
  ['강원', '오죽헌과 경포의 주말', '문화', '역사 공간과 바다를 함께 둘러보는 일정'],
  ['서울', '서울숲과 한강에서 쉬어가기', '자연', '숲 산책과 강변 피크닉을 즐기는 일정'],
  ['제주', '비자림 그늘 아래 함께 걷기', '자연', '숲길에서 충분히 쉬고 바다를 보는 일정'],
  ['부산', '광안리 아침을 담는 동행', '사진', '아침 해변 풍경과 오후 골목을 담는 일정'],
  ['강원', '안목에서 마시는 첫 커피', '맛집', '커피 한 잔과 해변 산책을 나누는 일정'],
  ['서울', '경복궁에서 시작하는 첫 동행', '문화', '처음 만난 동행과 고궁을 둘러보는 일정'],
  ['제주', '성산 풍경과 바닷바람', '사진', '성산 주변 풍경과 동쪽 해변을 담는 일정'],
  ['부산', '책방과 바다 사이 산책', '문화', '책방에서 취향을 나누고 해변으로 가는 일정'],
  ['강원', '경포에서 보내는 느린 오후', '자연', '해변과 커피거리에서 여유를 찾는 일정'],
  ['서울', '가벼운 배낭으로 떠나는 서울', '자연', '고궁과 숲을 가볍게 걷는 일정'],
  ['제주', '렌터카로 잇는 동쪽 세 장소', '사진', '해변과 숲과 전망을 잇는 일정'],
  ['부산', '영도 골목에서 취향 찾기', '문화', '문화마을과 책방에서 취향을 나누는 일정'],
  ['강원', '혼자 떠났다가 함께 걷는 강릉', '자연', '처음 만난 여행자와 바닷길을 걷는 일정'],
  ['서울', '한강 노을을 기다리는 주말', '사진', '숲에서 출발해 강변의 저녁을 만나는 일정'],
  ['제주', '카메라 없이 즐기는 제주', '자연', '사진보다 대화와 풍경에 집중하는 일정'],
  ['부산', '첫 부산 여행 함께 준비하기', '맛집', '대표 해변과 골목을 차근차근 보는 일정'],
  ['강원', '기차 여행자의 강릉 산책', '문화', '걷는 시간을 넉넉히 잡은 문화 산책 일정'],
  ['서울', '전통과 초록을 만나는 하루', '문화', '고궁과 서울숲을 연결하는 일정'],
  ['제주', '다시 가고 싶은 동쪽 바다', '사진', '좋아했던 해변을 여유 있게 다시 보는 일정'],
  ['부산', '바다를 보며 나누는 여행 이야기', '자연', '해변과 골목에서 대화하며 쉬는 일정'],
  ['강원', '책과 커피를 들고 강릉으로', '문화', '문화 공간과 커피거리에서 쉬는 일정'],
  ['서울', '친구처럼 걷는 서울 주말', '자연', '처음 만난 동행과 부담 없이 걷는 일정'],
  ['제주', '숲과 해변에서 보내는 이틀', '자연', '동쪽 해변과 비자림을 나눠 보는 일정'],
  ['부산', '느긋한 부산 사진 산책', '사진', '해변과 문화마을의 풍경을 담는 일정'],
];
const clone = value => structuredClone(value);
export const additionalCompanions = ideas.map(([region, title, theme, summary], index) => {
  const source = companions.find(item => item.region === region);
  const owner = members[index % members.length];
  const sourcePlaces = source.schedule.map(entry => source.places[entry.placeId]);
  const stops = sourcePlaces;
  const startDate = `2026-11-${String(1 + index).padStart(2, '0')}`;
  const end = new Date(`${startDate}T12:00:00+09:00`); end.setUTCDate(end.getUTCDate() + 1);
  const endDate = end.toISOString().slice(0, 10);
  const capacity = 3 + index % 3;
  const participants = [owner, ...members.filter(member => member.id !== owner.id).slice(0, index % 3)].map(({ id, nickname, photo, gender }) => ({ id, nickname, photo, gender }));
  return { id: 900000000001 + index, demo: true, ownerId: owner.id, title, sourceTrip: title, tripId: `demo-extra-plan-${index + 1}`, region, theme, description: `${summary}입니다. 첫날에는 두 곳을 둘러보고 둘째 날에는 마지막 장소에서 여유롭게 시간을 보내요. 중간 휴식 시간은 충분히 두고, 식사와 이동 방식은 참가자들과 상의하려고 합니다. 궁금한 점은 아래 질문과 답변에 남겨주세요. 처음 동행하는 분도 환영합니다.`, dates: `${startDate.replaceAll('-', '.')} - ${endDate.replaceAll('-', '.')}`, startDate, endDate, people: `${participants.length}/${capacity}명`, participants, author: owner.nickname, avatar: owner.nickname[0], tags: [region, theme, '첫 동행 환영'], image: stops[0].image, schedule: stops.map((place, n) => ({ placeId: place.id, day: n < 2 ? 1 : 2, time: ['10:00', '14:00', '10:30'][n], memo: ['입구에서 만나 함께 둘러보기', '점심 후 천천히 걷고 쉬기', '마지막 풍경을 즐기며 여행 마무리'][n] })), places: Object.fromEntries(stops.map(place => [place.id, clone(place)])), closed: false, status: participants.length >= capacity ? 'closed' : participants.length === capacity - 1 ? 'soon' : 'open' };
});
export const additionalPosts = ideas.map(([region, title, theme, summary], index) => {
  const owner = members[(index + 2) % members.length];
  const trip = additionalCompanions[index];
  const stops = trip.schedule.map(entry => trip.places[entry.placeId].title);
  return { id: 900000000001 + index, demo: true, board: 'travel', category: ['후기', '여행 팁', '추천'][index % 3], ownerId: owner.id, author: owner.nickname, title: `${title} · ${['여행 기록', '준비하며 챙긴 것', '함께 가고 싶은 코스'][index % 3]}`, content: `${region}에서 ${summary}을 준비하며 정리한 체험용 여행 기록입니다.\n\n첫날에는 ${stops[0]}에서 만나 ${stops[1]}까지 둘러보고, 둘째 날은 ${stops[2]}에서 마무리하는 흐름으로 잡았습니다. 장소 사이 이동 시간과 점심 시간을 따로 두니 일정에 여유가 생겼어요.\n\n${theme === '사진' ? '사진을 찍을 때는 한 장소에 조금 더 머무를 시간을 남기고, 다른 동행이 기다리지 않도록 미리 이야기해두려고 합니다.' : theme === '맛집' ? '식당과 카페는 방문 전에 영업시간을 확인하고, 각자의 식사 취향과 예산을 먼저 나누면 선택하기 편하더라고요.' : theme === '문화' ? '관람 시간과 쉬는 시간을 구분해 두고, 관심 있는 전시나 공간은 함께 이야기하며 고르려고 합니다.' : '편한 신발과 물을 챙기고 걷는 속도를 서로 맞추면 처음 만난 동행과도 부담 없이 여행할 수 있어요.'}\n\n여러분은 이 코스에서 어디에 시간을 더 쓰고 싶나요? 댓글과 답글로 여행 팁을 나눠주세요.`, date: `2026.10.${String(1 + index % 3).padStart(2, '0')}`, views: 0, comments: 0 };
});

export function addDemoExpansion(state) {
  if (state.demoExpansionVersion >= 1) return false;
  const append = (existing, entries) => { entries.forEach(entry => { if (!existing.some(item => String(item.id) === String(entry.id))) existing.push(clone(entry)); }); };
  append(state.posts, additionalPosts);
  append(state.companions, additionalCompanions);
  append(state.plans, additionalCompanions.map(item => ({ id: item.tripId, ownerId: item.ownerId, trip: { id: item.tripId, title: item.title, startDate: item.startDate, endDate: item.endDate, people: Number(item.people.split('/')[1].replace('명', '')), budget: 180000, region: item.region, theme: item.theme }, tripSchedule: item.schedule, itinerary: item.schedule.map(entry => entry.placeId), itineraryPlaces: item.places })));
  state.demoExpansionVersion = 1;
  return true;
}
