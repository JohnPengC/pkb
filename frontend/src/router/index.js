import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/docs' },
    { path: '/import', component: () => import('../views/ImportView.vue') },
    { path: '/docs', component: () => import('../views/DocsView.vue') },
    { path: '/docs/:id', component: () => import('../views/DocDetailView.vue') },
    { path: '/chat', component: () => import('../views/ChatView.vue') }
  ]
})

export default router
