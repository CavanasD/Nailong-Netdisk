<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-title">NailongNetdisk</div>
        <div class="brand-sub" v-if="user">{{ user.username }} · {{ formatBytes(storageUsed) }} / {{ formatBytes(storageQuota) }}</div>
      </div>

      <nav class="menu">
        <button class="menu-item" :class="{ active: active === 'dashboard' }" @click="active = 'dashboard'">
          <span class="dot" />
          仪表盘
        </button>
        <button class="menu-item" :class="{ active: active === 'upload' }" @click="active = 'upload'">
          <span class="dot" />
          上传文件
        </button>
        <button class="menu-item" :class="{ active: active === 'files' }" @click="active = 'files'">
          <span class="dot" />
          我的文件
        </button>
        <button class="menu-item" :class="{ active: active === 'profile' }" @click="active = 'profile'">
          <span class="dot" />
          个人设置
        </button>
      </nav>

      <div class="sidebar-footer">
        <el-button class="logout" type="danger" plain @click="logout">退出登录</el-button>
        <el-button
          v-if="user && (user.userId === 8 || user.role === 'ADMIN' || user.role === 'SUPER_ADMIN')"
          class="admin"
          type="warning"
          plain
          @click="goToAdmin"
        >管理员后台</el-button>
      </div>
    </aside>

    <main class="content">
      <div class="topbar">
        <div class="page-title">{{ pageTitle }}</div>
        <div class="user-card" v-if="user" @click="active = 'profile'">
          <div class="avatar" @click.stop="openAvatarDialog">
            <img v-if="avatarSrc" :src="avatarSrc" alt="avatar" />
            <div v-else class="avatar-fallback">{{ (user.username || 'U').slice(0, 1).toUpperCase() }}</div>
            <div class="avatar-hover">
              <el-icon><Camera /></el-icon>
            </div>
          </div>
          <div class="user-meta">
            <div class="user-name">{{ user.username }}</div>
            <div class="user-sub">
              <el-tag size="small" :type="user.role === 'SUPER_ADMIN' ? 'danger' : (user.role === 'ADMIN' ? 'warning' : 'info')">
                {{ user.role || 'USER' }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <transition name="fade-slide" mode="out-in">
        <!-- Dashboard -->
        <section v-if="active === 'dashboard'" key="dashboard" class="panel">
          <div class="grid">
            <div class="card ring-card">
              <div class="card-title">已用空间</div>
              <div class="ring-wrap">
                <svg class="ring" :width="ringSize" :height="ringSize" viewBox="0 0 120 120" aria-label="quota ring">
                  <circle
                    class="ring-bg"
                    cx="60"
                    cy="60"
                    :r="ringRadius"
                    :stroke-width="ringStroke"
                  />
                  <circle
                    class="ring-fg"
                    cx="60"
                    cy="60"
                    :r="ringRadius"
                    :stroke-width="ringStroke"
                    :stroke-dasharray="ringCircumference"
                    :stroke-dashoffset="ringDashOffset"
                  />
                </svg>
                <div class="ring-center">
                  <div class="ring-percent">{{ quotaPercent }}%</div>
                  <div class="ring-text">{{ formatBytes(storageUsed) }}</div>
                </div>
              </div>
              <div class="card-meta">配额 {{ formatBytes(storageQuota) }}</div>
            </div>
            <div class="card">
              <div class="card-title">空间配额</div>
              <div class="card-value">{{ formatBytes(storageQuota) }}</div>
              <div class="card-meta">每个用户默认 200MB</div>
            </div>
            <div class="card">
              <div class="card-title">文件数量</div>
              <div class="card-value">{{ myFiles.length }}</div>
              <div class="card-meta">来自“我的文件”列表</div>
            </div>
          </div>

          <div class="card wide">
            <div class="card-title">最近文件</div>
            <div v-if="recentFiles.length" class="recent">
              <div v-for="f in recentFiles" :key="f.id" class="recent-item">
                <div class="recent-main">
                  <div class="recent-name" :title="f.originalName">{{ f.originalName }}</div>
                  <div class="recent-sub">{{ formatBytes(f.size) }} · {{ formatTime(f.createTime) }}</div>
                </div>
                <el-button size="small" type="primary" plain @click="download(f)">下载</el-button>
              </div>
            </div>
            <div v-else class="empty-hint">暂无文件，先去上传一个吧</div>
          </div>

          <div class="card wide">
            <div class="card-title">快捷操作</div>
            <div class="actions">
              <el-button type="primary" @click="active = 'upload'">去上传</el-button>
              <el-button @click="refreshAll">刷新数据</el-button>
            </div>
          </div>
        </section>

        <!-- Upload -->
        <section v-else-if="active === 'upload'" key="upload" class="panel">
          <div class="card wide">
            <div class="card-title">上传文件</div>
            <el-upload
              drag
              :multiple="false"
              :show-file-list="false"
              :http-request="uploadRequest"
            >
              <el-icon style="font-size: 28px;"><Upload /></el-icon>
              <div class="el-upload__text">拖拽文件到这里，或 <em>点击上传</em></div>
              <template #tip>
                <div class="upload-tip">单用户配额 {{ formatBytes(storageQuota) }}，超额会被后端拒绝</div>
              </template>
            </el-upload>
          </div>
        </section>

        <!-- Files -->
        <section v-else-if="active === 'files'" key="files" class="panel">
          <div class="card wide">
            <div class="card-title">我的文件</div>
            <div class="actions" style="margin-bottom: 12px;">
              <el-button @click="fetchMyFiles">刷新列表</el-button>
            </div>
            <el-table :data="myFiles" style="width: 100%" stripe>
              <el-table-column prop="id" label="#" width="90" />
              <el-table-column prop="originalName" label="文件名" show-overflow-tooltip />
              <el-table-column prop="size" label="大小" width="120">
                <template #default="scope">{{ formatBytes(scope.row.size) }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="上传时间" width="190" />
              <el-table-column label="操作" width="120">
                <template #default="scope">
                  <el-button size="small" type="primary" plain @click="download(scope.row)">下载</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <!-- Profile -->
        <section v-else key="profile" class="panel">
          <div class="card wide">
            <div class="card-title">个人设置</div>
            <div v-if="user" class="profile">
              <div class="row">
                <div class="label">用户名</div>
                <div class="value">{{ user.username }}</div>
              </div>
              <div class="row">
                <div class="label">邮箱</div>
                <div class="value" style="display:flex; gap:10px; align-items:center; flex-wrap:wrap;">
                  <el-input v-model="newEmail" placeholder="输入新邮箱" style="width: 260px;"></el-input>
                  <el-button type="primary" @click="updateEmail">保存</el-button>
                </div>
              </div>
            </div>
          </div>

          <div class="card wide" style="margin-top: 14px;">
            <div class="card-title">用户搜索（演示功能）</div>
            <div class="actions" style="margin-bottom: 12px;">
              <el-input v-model="searchUsername" placeholder="输入用户名进行搜索" style="width: 280px;"></el-input>
              <el-button type="primary" @click="searchUsers">搜索</el-button>
            </div>
            <el-table v-if="searchResults.length" :data="searchResults" style="width: 100%" stripe>
              <el-table-column prop="userId" label="User ID" width="120" />
              <el-table-column prop="username" label="Username" />
              <el-table-column prop="email" label="Email" />
              <el-table-column prop="password" label="Password (Hashed)" />
            </el-table>
          </div>
        </section>
      </transition>

      <el-dialog v-model="avatarDialogVisible" title="更换头像" width="860px" :close-on-click-modal="false" @closed="onAvatarDialogClosed">
        <div class="avatar-dialog">
          <div class="avatar-left">
            <div class="avatar-actions">
              <input ref="avatarFileRef" class="file-input" type="file" accept="image/*" @change="onSelectAvatar" />
              <el-button type="primary" plain @click="triggerAvatarPick">选择图片</el-button>
              <el-button :disabled="!cropperInstance" type="success" @click="confirmAvatar">裁剪并上传</el-button>
            </div>
            <div class="cropper-area">
              <img v-if="cropperImageUrl" ref="cropperImgRef" :src="cropperImageUrl" class="cropper-img" alt="crop" />
              <div v-else class="cropper-placeholder">请选择一张图片</div>
            </div>
          </div>
          <div class="avatar-right">
            <div class="preview-title">预览</div>
            <div class="avatar-preview" />
            <div class="preview-hint">建议上传清晰的人像/图标</div>
          </div>
        </div>
      </el-dialog>
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ref, onMounted, watch, computed, nextTick, onBeforeUnmount } from 'vue'
import request from '../utils/request'
import { ElMessage } from 'element-plus'
import { Upload, Camera } from '@element-plus/icons-vue'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'

const router = useRouter()
const user = ref(null)
const newEmail = ref('')
const searchUsername = ref('')
const searchResults = ref([])
const active = ref('dashboard')
const myFiles = ref([])

const storageUsed = ref(0)
const storageQuota = ref(200 * 1024 * 1024)

const pageTitle = ref('仪表盘')

const avatarUrl = ref('')
const avatarVersion = ref(Date.now())
const avatarSrc = computed(() => {
  if (!avatarUrl.value) return ''
  return `/api${avatarUrl.value}?v=${avatarVersion.value}`
})

const updateTitle = () => {
  pageTitle.value =
    active.value === 'dashboard' ? '仪表盘' :
    active.value === 'upload' ? '上传文件' :
    active.value === 'files' ? '我的文件' :
    '个人设置'
}

const fetchUser = async () => {
  try {
    const res = await request.get('/user/me')
    user.value = res.data // 请确保后端返回了 role 字段
    newEmail.value = res.data.email || ''

    storageUsed.value = res.data.storageUsed || 0
    storageQuota.value = res.data.storageQuota || (200 * 1024 * 1024)

    avatarUrl.value = res.data.avatarUrl || ''
  } catch (e) {
    console.error('获取用户信息失败', e)
    ElMessage.error('获取用户信息失败，请重新登录')
    router.push('/login')
  }
}

const fetchMyFiles = async () => {
  try {
    const res = await request.get('/file/list')
    myFiles.value = res.data || []
  } catch (e) {
    console.error('获取文件列表失败', e)
  }
}

const refreshAll = async () => {
  await fetchUser()
  await fetchMyFiles()
}

onMounted(async () => {
  await refreshAll()
  updateTitle()
})

const quotaPercent = ref(0)
const recomputeQuota = () => {
  if (!storageQuota.value) {
    quotaPercent.value = 0
    return
  }
  quotaPercent.value = Math.min(100, Math.round((storageUsed.value / storageQuota.value) * 100))
}

const recentFiles = computed(() => (myFiles.value || []).slice(0, 5))

const formatTime = (t) => {
  if (!t) return '-'
  // backend returns LocalDateTime serialized; keep it simple
  return String(t).replace('T', ' ')
}

const formatBytes = (bytes) => {
  const b = Number(bytes || 0)
  if (b < 1024) return `${b}B`
  const kb = b / 1024
  if (kb < 1024) return `${kb.toFixed(1)}KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(1)}MB`
  const gb = mb / 1024
  return `${gb.toFixed(2)}GB`
}

const uploadRequest = async (options) => {
  try {
    const form = new FormData()
    form.append('file', options.file)

    const res = await request.post('/file/upload', form, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    ElMessage.success('上传成功')
    active.value = 'files'
    updateTitle()
    await refreshAll()
    recomputeQuota()
    options.onSuccess && options.onSuccess(res)
  } catch (e) {
    options.onError && options.onError(e)
  }
}

const download = (file) => {
  const token = localStorage.getItem('token')
  if (!token) {
    router.push('/login')
    return
  }
  const url = `/api/file/download/${file.id}?token=${encodeURIComponent(token)}`
  window.open(url, '_blank')
}

const searchUsers = async () => {
  if (!searchUsername.value) {
    ElMessage.warning('请输入要搜索的用户名')
    return
  }
  try {
    const res = await request.get('/user/search', {
      params: {
        username: searchUsername.value,
        order: 'user_id' // 默认排序字段
      }
    })
    searchResults.value = res.data
    if (!res.data || res.data.length === 0) {
      ElMessage.info('没有找到匹配的用户')
    }
  } catch (e) {
    // WAF 拦截的错误会在这里被捕获，并在 request.js 中统一处理
    console.error('搜索失败', e)
  }
}

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
    recomputeQuota()
  } catch (e) {
    // 错误已在 request.js 中处理
    console.error('更新失败', e)
  }
}

const logout = () => {
  localStorage.removeItem('token')
  router.push('/login')
}

const goToAdmin = () => {
  router.push('/admin')
}

watch(active, () => updateTitle())
watch([storageUsed, storageQuota], () => recomputeQuota(), { immediate: true })

// ===== Avatar crop & upload =====
const avatarDialogVisible = ref(false)
const avatarFileRef = ref(null)
const cropperImgRef = ref(null)
const cropperImageUrl = ref('')
const cropperInstance = ref(null)

const triggerAvatarPick = () => {
  avatarFileRef.value?.click?.()
}

const openAvatarDialog = () => {
  avatarDialogVisible.value = true
}

const destroyCropper = () => {
  if (cropperInstance.value) {
    cropperInstance.value.destroy()
    cropperInstance.value = null
  }
}

const revokeObjectUrl = () => {
  if (cropperImageUrl.value) {
    try { URL.revokeObjectURL(cropperImageUrl.value) } catch (e) {}
  }
  cropperImageUrl.value = ''
}

const onAvatarDialogClosed = () => {
  destroyCropper()
  revokeObjectUrl()
  if (avatarFileRef.value) avatarFileRef.value.value = ''
}

const onSelectAvatar = async (e) => {
  const file = e?.target?.files?.[0]
  if (!file) return
  if (!file.type?.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }

  destroyCropper()
  revokeObjectUrl()

  cropperImageUrl.value = URL.createObjectURL(file)
  await nextTick()

  const img = cropperImgRef.value
  if (!img) return

  const init = () => {
    destroyCropper()
    cropperInstance.value = new Cropper(img, {
      aspectRatio: 1,
      viewMode: 1,
      autoCropArea: 1,
      background: false,
      guides: false,
      center: true,
      responsive: true,
      dragMode: 'move',
      cropBoxResizable: true,
      cropBoxMovable: true,
      preview: '.avatar-preview'
    })
  }

  if (img.complete) {
    init()
  } else {
    img.onload = () => init()
  }
}

const confirmAvatar = async () => {
  if (!cropperInstance.value) {
    ElMessage.warning('请先选择图片')
    return
  }

  const canvas = cropperInstance.value.getCroppedCanvas({
    width: 256,
    height: 256,
    imageSmoothingEnabled: true,
    imageSmoothingQuality: 'high'
  })

  if (!canvas) {
    ElMessage.error('裁剪失败')
    return
  }

  const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/png', 0.92))
  if (!blob) {
    ElMessage.error('生成头像失败')
    return
  }

  try {
    const form = new FormData()
    form.append('file', blob, 'avatar.png')
    const res = await request.post('/user/avatar', form, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    ElMessage.success('头像已更新')
    avatarUrl.value = res.data || avatarUrl.value
    avatarVersion.value = Date.now()
    avatarDialogVisible.value = false
    await fetchUser()
  } catch (e) {
    // handled in request.js
  }
}

onBeforeUnmount(() => {
  destroyCropper()
  revokeObjectUrl()
})

// ===== Ring progress =====
const ringRadius = 46
const ringStroke = 10
const ringSize = 120
const ringCircumference = computed(() => 2 * Math.PI * ringRadius)
const ringDashOffset = computed(() => {
  const c = ringCircumference.value
  return c - (c * (quotaPercent.value / 100))
})
</script>

<style scoped>
.layout {
  height: calc(100vh - 0px);
  display: flex;
  background: #f5f7fa;
}

.sidebar {
  width: 240px;
  background: #1f2937;
  color: #fff;
  display: flex;
  flex-direction: column;
  padding: 18px 14px;
}

.brand {
  padding: 10px 10px 14px;
  border-bottom: 1px solid rgba(255,255,255,0.12);
}

.brand-title {
  font-weight: 800;
  letter-spacing: 0.5px;
  font-size: 18px;
}

.brand-sub {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.75;
}

.menu {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.menu-item {
  width: 100%;
  text-align: left;
  border: none;
  border-radius: 10px;
  padding: 10px 12px;
  background: transparent;
  color: rgba(255,255,255,0.88);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: background-color 0.22s ease, transform 0.22s ease;
}

.menu-item:hover {
  background: rgba(255,255,255,0.10);
  transform: translateX(2px);
}

.menu-item.active {
  background: rgba(64,158,255,0.20);
  color: #fff;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(255,255,255,0.55);
}

.menu-item.active .dot {
  background: #409eff;
}

.sidebar-footer {
  margin-top: auto;
  padding: 14px 10px 6px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.logout,
.admin {
  width: 100%;
}

.content {
  flex: 1;
  padding: 18px 20px;
  overflow: auto;
}

.topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 14px;
  padding: 10px 12px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.user-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(17, 24, 39, 0.10);
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 999px;
  overflow: hidden;
  position: relative;
  background: #f3f4f6;
  flex: 0 0 auto;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  color: #111827;
}

.avatar-hover {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(17,24,39,0.45);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.avatar:hover .avatar-hover {
  opacity: 1;
}

.user-meta {
  min-width: 120px;
}

.user-name {
  font-weight: 800;
  color: #111827;
  line-height: 1.1;
}

.user-sub {
  margin-top: 6px;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.card {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  border: 1px solid #eef2f7;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(17, 24, 39, 0.08);
}

.card.wide {
  grid-column: 1 / -1;
}

.card-title {
  font-size: 13px;
  color: #6b7280;
}

.card-value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 800;
  color: #111827;
}

.card-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #9ca3af;
}

.actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
}

.ring-card {
  display: flex;
  flex-direction: column;
}

.ring-wrap {
  margin-top: 8px;
  position: relative;
  width: 120px;
  height: 120px;
}

.ring {
  transform: rotate(-90deg);
}

.ring-bg {
  fill: none;
  stroke: #eef2f7;
  stroke-linecap: round;
}

.ring-fg {
  fill: none;
  stroke: #409eff;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.35s ease;
}

.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ring-percent {
  font-weight: 900;
  font-size: 20px;
  color: #111827;
}

.ring-text {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

.recent {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  background: #fff;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.recent-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(17, 24, 39, 0.06);
}

.recent-main {
  min-width: 0;
}

.recent-name {
  font-weight: 800;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 520px;
}

.recent-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

.empty-hint {
  margin-top: 10px;
  font-size: 13px;
  color: #6b7280;
}

.avatar-dialog {
  display: grid;
  grid-template-columns: 1fr 220px;
  gap: 16px;
}

.avatar-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.file-input {
  display: none;
}

.cropper-area {
  height: 420px;
  border-radius: 12px;
  border: 1px solid #eef2f7;
  background: #f8fafc;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cropper-img {
  max-width: 100%;
  max-height: 100%;
  display: block;
}

.cropper-placeholder {
  color: #6b7280;
}

.avatar-right {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-title {
  font-size: 13px;
  color: #6b7280;
}

.avatar-preview {
  width: 160px;
  height: 160px;
  border-radius: 999px;
  overflow: hidden;
  border: 1px solid #eef2f7;
  background: #f3f4f6;
}

.preview-hint {
  font-size: 12px;
  color: #9ca3af;
}

.profile .row {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.profile .row:last-child {
  border-bottom: none;
}

.profile .label {
  width: 70px;
  color: #6b7280;
  font-size: 13px;
}

.profile .value {
  color: #111827;
  font-weight: 600;
}

/* transitions */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.20s ease, transform 0.20s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .sidebar {
    width: 210px;
  }
  .avatar-dialog {
    grid-template-columns: 1fr;
  }
}
</style>
