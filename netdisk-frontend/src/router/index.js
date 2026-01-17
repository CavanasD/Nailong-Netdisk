import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Home from '../views/Home.vue'
import WafAlert from '../views/WafAlert.vue'
import AdminPanel from '../views/AdminPanel.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/waf',
    name: 'WafAlert',
    component: WafAlert
  },
  {
    path: '/admin',
    name: 'AdminPanel',
    component: AdminPanel
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Router 守卫，检查 Token
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  // WafAlert 页面不需要登录
  if (to.name === 'WafAlert') {
    next()
    return
  }
  // 如果去的是 Home 页面且没有 Token，转去登录
  if (to.name !== 'Login' && !token) {
    next({ name: 'Login' })
  } else {
    next()
  }
})

export default router
