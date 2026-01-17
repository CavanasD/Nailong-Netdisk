<template>
  <div class="defender-container slide-up">
    <div class="defender-card">
      <div class="icon-warning">⚠</div>

      <h1>访问已被控制</h1>
      <h2>NailongDefender已检测到潜在的威胁</h2>
      <p>您的请求已被拦截</p>

      <div class="info-grid">
        <div class="info-item">
          <span class="label">拦截ID：</span>
          <span class="value sec-id">{{ secId }}</span>
        </div>
        <div class="info-item">
          <span class="label">UID:</span>
          <span class="value">{{ userId }}</span>
        </div>
        <div class="info-item">
          <span class="label">风险类型:</span>
          <span class="value risk-name">{{ riskName }}</span>
        </div>
      </div>

      <button class="back-btn" @click="goBack">返回</button>
    </div>

    <div class="footer-support">
      技术支持：NailongSec
    </div>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { ref, onMounted } from 'vue'

const router = useRouter()
const route = useRoute()
const secId = ref('')
const userId = ref('')
const riskName = ref('')

onMounted(() => {
  secId.value = route.query.secId || 'Unknown'
  userId.value = route.query.userId || 'Unknown'
  riskName.value = route.query.riskName || 'HEUR/Unknown.Gen'
})

const goBack = () => {
  router.push('/')
}
</script>

<style scoped>
.defender-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  /* 纯色背景，严肃风格，类似红屏警告 */
  background-color: #d32f2f;
  display: flex;
  justify-content: center;
  align-items: center;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  color: white;
}

/* Slide Up Animation */
.slide-up {
  animation: slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes slideUp {
  0% { transform: translateY(100%); opacity: 0; }
  100% { transform: translateY(0); opacity: 1; }
}

.defender-card {
  text-align: center;
  max-width: 600px;
  width: 90%;
  padding: 40px;
  /* 如果不需要卡片背景，可以去掉下面的 background 和 shadow，直接显示在红底上 */
  /* 这里保留一点微弱的卡片感，或者直接融入背景 */
}

.icon-warning {
  font-size: 80px;
  margin-bottom: 20px;
  color: #fff;
  /* 禁止选择 */
  user-select: none;
}

h1 {
  font-size: 3rem;
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 2px;
  font-weight: 900;
}

h2 {
  font-size: 1.5rem;
  margin: 10px 0 30px;
  font-weight: 400;
  opacity: 0.9;
}

p {
  font-size: 1.2rem;
  margin-bottom: 30px;
}

.info-grid {
  background: rgba(0, 0, 0, 0.2);
  padding: 20px;
  border-left: 5px solid #fff;
  text-align: left;
  margin-bottom: 30px;
}

.info-item {
  margin-bottom: 15px;
  font-family: 'Consolas', monospace;
  font-size: 1.1rem;
  display: flex;
  flex-direction: column; /* 垂直排列 */
  align-items: flex-start;
}

.info-item:last-child {
  margin-bottom: 0;
}

.label {
  font-weight: bold;
  margin-bottom: 5px;
  opacity: 0.8;
  display: inline-block;
}

.value {
  display: inline-block;
  width: 100%;
}

.sec-id {
  word-break: break-all; /* 防止长ID溢出 */
  line-height: 1.4;
}

.risk-name {
  color: #ffeb3b; /* 黄色高亮显示病毒名 */
  font-weight: bold;
}

.back-btn {
  background: transparent;
  border: 2px solid white;
  color: white;
  padding: 10px 40px;
  font-size: 1.2rem;
  cursor: pointer;
  transition: all 0.3s;
  text-transform: uppercase;
  font-weight: bold;
}

.back-btn:hover {
  background: white;
  color: #d32f2f;
}

.footer-support {
  position: absolute;
  bottom: 20px;
  left: 20px;
  font-family: sans-serif;
  font-size: 0.9rem;
  opacity: 0.6;
}
</style>
