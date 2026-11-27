import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', name: 'explore', component: () => import('@/views/ExploreView.vue') },
  { path: '/plans', name: 'plans', component: () => import('@/views/PlanListView.vue'), meta: { auth: true } },
  { path: '/plans/new', name: 'plan-new', component: () => import('@/views/PlanEditView.vue'), meta: { auth: true } },
  { path: '/plans/:id', name: 'plan-edit', component: () => import('@/views/PlanEditView.vue'), meta: { auth: true } },
  { path: '/mates', name: 'mates', component: () => import('@/views/MatesView.vue') },
  { path: '/culture', name: 'culture', component: () => import('@/views/CultureView.vue') },
  { path: '/hotplaces', name: 'hotplaces', component: () => import('@/views/HotplaceView.vue') },
  { path: '/board', name: 'board', component: () => import('@/views/BoardView.vue') },
  { path: '/news', name: 'news', component: () => import('@/views/NewsView.vue') },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
  { path: '/join', name: 'join', component: () => import('@/views/JoinView.vue') },
  { path: '/find-password', name: 'find-password', component: () => import('@/views/FindPasswordView.vue') },
  { path: '/mypage', name: 'mypage', component: () => import('@/views/MyPageView.vue'), meta: { auth: true } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.meta.auth && !useAuthStore().isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})

export default router
