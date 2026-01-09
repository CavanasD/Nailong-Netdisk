<template>
  <div class="home">
    <h1>欢迎来到 Netdisk 网盘系统</h1>
    <el-button type="danger" @click="logout">退出登录</el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const logout = () => {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<style scoped>
.home {
  padding: 50px;
  text-align: center;
}
</style>

