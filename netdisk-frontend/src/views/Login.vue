<template>
   <div class="login-container">
    <div class="login-box">
      <div class="header">
        <div class="title">{{ isLogin ? '欢迎回来' : '创建账户' }}</div>
        <div class="sub-title">{{ isLogin ? '登录 Netdisk 开始您的云端之旅' : '免费注册，私有云存储触手可及' }}</div>
      </div>

      <transition name="fade" mode="out-in">
        <!-- 登录表单 -->
        <div v-if="isLogin" key="login" class="form-wrapper">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" size="large">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-button type="primary" class="w-100 submit-btn" @click="handleLogin" :loading="loading" round>立即登录</el-button>
            <div class="links">
              <span @click="isLogin = false">没有账号？<span class="link-highlight">去注册</span></span>
            </div>
          </el-form>
        </div>

        <!-- 注册表单 -->
        <div v-else key="register" class="form-wrapper">
          <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" size="large">
            <el-form-item prop="username">
              <el-input v-model="registerForm.username" placeholder="用户名 (4-20字符)" prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="密码 (6-20字符, 含字母数字)" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item prop="email">
              <el-input v-model="registerForm.email" placeholder="电子邮箱" prefix-icon="Message" />
            </el-form-item>

            <el-form-item prop="captchaAnswer">
              <div class="captcha-row">
                <div class="captcha-img" @click="refreshCaptcha" :title="captchaLoading ? '加载中…' : '点击刷新验证码'">
                  <img v-if="captchaUrl" :src="captchaUrl" alt="captcha" />
                  <div v-else class="captcha-placeholder">{{ captchaLoading ? '加载中…' : '点击获取验证码' }}</div>
                </div>
                <div class="captcha-input">
                  <el-input v-model="registerForm.captchaAnswer" placeholder="请输入运算结果" />
                  <div class="captcha-actions">
                    <el-button size="small" @click="refreshCaptcha" :loading="captchaLoading">刷新</el-button>
                  </div>
                </div>
              </div>
            </el-form-item>

            <el-button type="success" class="w-100 submit-btn" @click="handleRegister" :loading="loading" round>立即注册</el-button>
            <div class="links">
              <span @click="isLogin = true">已有账号？<span class="link-highlight">去登录</span></span>
            </div>
          </el-form>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { User, Lock, Message } from '@element-plus/icons-vue'
import request from '../utils/request'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
const isLogin = ref(true)
const loading = ref(false)

const captchaLoading = ref(false)
const captchaId = ref('')
const captchaUrl = ref('')

// 登录相关
const loginFormRef = ref(null)
const loginForm = reactive({ username: '', password: '' })
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = () => {
  loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const res = await request.post('/user/login', loginForm)
        // 登录成功
        ElMessage.success('登录成功')
        localStorage.setItem('token', res.data) // 存储 Token
        // TODO: 跳转到主页，暂时跳不到哪里去
        router.push('/')
      } catch (e) {
        // error handled in request.js
      } finally {
        loading.value = false
      }
    }
  })
}

// 注册相关
const registerFormRef = ref(null)
const registerForm = reactive({ username: '', password: '', email: '', captchaAnswer: '' })
const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '长度在 4 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  captchaAnswer: [
    { required: true, message: '请输入验证码结果', trigger: 'blur' }
  ]
}

const refreshCaptcha = async () => {
  captchaLoading.value = true
  try {
    const res = await request.get('/captcha/new')
    captchaId.value = res.data.id
    captchaUrl.value = res.data.url
    registerForm.captchaAnswer = ''
  } catch (e) {
    // handled in request.js
  } finally {
    captchaLoading.value = false
  }
}

watch(isLogin, async (val) => {
  if (val === false) {
    await refreshCaptcha()
  }
})

const handleRegister = () => {
  registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await request.post('/user/register', {
          username: registerForm.username,
          password: registerForm.password,
          email: registerForm.email,
          captchaId: captchaId.value,
          captchaAnswer: registerForm.captchaAnswer
        })
        ElMessage.success('注册成功，请登录')
        isLogin.value = true
      } catch (e) {
        // handled
        await refreshCaptcha()
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  /* 更加清新的背景 */
  background: linear-gradient(-20deg, #e9defa 0%, #fbfcdb 100%);
  background-size: 400% 400%;
  animation: gradientBG 15s ease infinite;
}

@keyframes gradientBG {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

.login-box {
  width: 380px;
  padding: 40px;
  /* 玻璃拟态效果 */
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.18);
  transition: transform 0.3s, box-shadow 0.3s;
}

.login-box:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 40px rgba(31, 38, 135, 0.2);
}

.header {
  text-align: center;
  margin-bottom: 30px;
}

.title {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  margin-bottom: 10px;
  letter-spacing: 1px;
}

.sub-title {
  font-size: 14px;
  color: #909399;
}

.w-100 {
  width: 100%;
}

.submit-btn {
  font-weight: bold;
  letter-spacing: 2px;
  margin-top: 10px;
  transition: all 0.3s;
}

.submit-btn:hover {
  transform: scale(1.02);
}

.links {
  margin-top: 20px;
  text-align: center;
  font-size: 14px;
  color: #606266;
}

.links span {
  cursor: pointer;
  transition: color 0.3s;
}

.link-highlight {
  color: #409eff;
  font-weight: 600;
  margin-left: 5px;
}

.link-highlight:hover {
  color: #66b1ff;
  text-decoration: underline;
}

/* 切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateX(10px);
}

.captcha-row {
  width: 100%;
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: 12px;
  align-items: center;
}

.captcha-img {
  height: 46px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(0,0,0,0.06);
  background: rgba(255,255,255,0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.captcha-img:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(17, 24, 39, 0.10);
}

.captcha-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.captcha-placeholder {
  font-size: 12px;
  color: #909399;
}

.captcha-input {
  display: flex;
  gap: 10px;
  align-items: center;
}

.captcha-actions {
  flex: 0 0 auto;
}
</style>

