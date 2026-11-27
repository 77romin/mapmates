import http from '@/api/http'

/** '1박 2일' 처럼 코스 길이를 말로 */
export const courseSpan = (days) => (days > 1 ? `${days - 1}박 ${days}일` : '당일')

/** 코스의 하루치 방문지를 자리(오전·점심·오후·저녁·숙박)별로 묶는다: [{ slot, stops }] */
export function daySlots(course, dayNo) {
  const groups = []
  for (const s of course.stops) {
    if (s.dayNo !== dayNo) continue
    const last = groups[groups.length - 1]
    if (last && last.slot === (s.slot || '')) last.stops.push(s)
    else groups.push({ slot: s.slot || '', stops: [s] })
  }
  return groups
}

/** 점심·저녁처럼 끼니 자리는 플랜 메모에 남겨, 플랜 보드에서도 어떤 식사인지 보이게 한다 */
const mealMemo = (s) => (s.slot === '점심' || s.slot === '저녁' ? s.slot : '')

/**
 * AI 가 짠 코스를 일차를 유지한 채 플랜으로 만든다.
 * 로그인 상태면 바로 저장하고 그 플랜을 연다. 아니면 일차를 유지해 담아 두고 로그인 뒤 새 플랜 화면으로 보낸다.
 * 만든 플랜 번호는 course.planId 에 남겨, 같은 코스로 두 번 만들지 않게 한다.
 */
export async function planFromCourse(course, { auth, draft, router }) {
  if (course.planId) return router.push(`/plans/${course.planId}`)
  if (!auth.isLogin) {
    draft.addCourse(course.stops)
    return router.push('/plans/new') // 로그인 화면을 거쳐 돌아온다
  }
  const created = await http.post('/plans', {
    title: course.title, startDate: null, endDate: null, budget: 0, memo: '', isPublic: false,
    items: course.stops.map((s) => ({ attractionNo: s.no, dayNo: s.dayNo, memo: mealMemo(s), cost: 0 })),
  })
  course.planId = created.id
  return router.push(`/plans/${created.id}`)
}
