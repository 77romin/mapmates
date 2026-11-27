import http from '@/api/http'

/** '1박 2일' 처럼 코스 길이를 말로 */
export const courseSpan = (days) => (days > 1 ? `${days - 1}박 ${days}일` : '당일')

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
    items: course.stops.map((s) => ({ attractionNo: s.no, dayNo: s.dayNo, memo: '', cost: 0 })),
  })
  course.planId = created.id
  return router.push(`/plans/${created.id}`)
}
