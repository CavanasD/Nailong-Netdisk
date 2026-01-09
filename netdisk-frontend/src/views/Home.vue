<template>
  <div class="home">
    <h1>欢迎来到 Netdisk 网盘系统</h1>
    <div v-if="user" class="user-info-card">
      <h2>你好，{{ user.username }} (ID: {{ user.userId }})</h2>
      <p>当前邮箱: {{ user.email || '未设置' }}</p>

      <div class="update-section">
        <el-input v-model="newEmail" placeholder="输入新邮箱" style="width: 250px; margin-right: 10px;"></el-input>
        <el-button type="primary" @click="updateEmail">修改邮箱</el-button>
      </div>
    </div>
    <el-button type="danger" @click="logout" style="margin-top: 20px;">退出登录</el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const user = ref(null)
const newEmail = ref('')

const fetchUser = async () => {
  try {
    const res = await request.get('/user/me')
    user.value = res.data
    newEmail.value = res.data.email || ''
  } catch (e) {
    console.error('获取用户信息失败', e)
    ElMessage.error('获取用户信息失败，请重新登录')
    router.push('/login')
  }
}

onMounted(fetchUser)

const updateEmail = async () => {
  if (!newEmail.value) {
    ElMessage.warning('请输入邮箱地址')
    return
  }
  try {
    // 这里是关键，我们把当前用户的 userId 和新 email 发给后端
    await request.put('/user/update', {
      userId: user.value.userId,
      email: newEmail.value
    })
    ElMessage.success('邮箱更新成功！')
    // 重新获取用户信息以刷新显示
    await fetchUser()
  } catch (e) {
    // 错误已在 request.js 中处理
    console.error('更新失败', e)
  }
}

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
.user-info-card {
  background-color: #f9f9f9;
  border-radius: 8px;
  padding: 20px;
  max-width: 500px;
  margin: 20px auto;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1);
}
.update-section {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>

