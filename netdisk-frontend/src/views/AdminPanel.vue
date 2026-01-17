<template>
  <div class="admin-panel">
    <h1>Security Admin Panel</h1>
    <div class="nav-tabs">
      <button :class="{ active: currentTab === 'logs' }" @click="currentTab = 'logs'">Security Logs</button>
      <button :class="{ active: currentTab === 'users' }" @click="currentTab = 'users'">User Management</button>
    </div>

    <div v-if="currentTab === 'logs'" class="tab-content">
      <h2>WAF Security Events</h2>
      <el-button @click="fetchLogs">Refresh Logs</el-button>
      <el-table :data="logs" style="width: 100%" stripe>
        <el-table-column prop="timestamp" label="Time" width="180" />
        <el-table-column prop="secId" label="SecID" width="220" show-overflow-tooltip/>
        <el-table-column prop="summary" label="Reason" />
        <el-table-column label="Actions" width="100">
          <template #default="scope">
            <el-button size="small" @click="showDetails(scope.row)">Details</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="detailsVisible" title="Request Details">
      <div v-if="currentLog">
        <p><strong>Time:</strong> {{ currentLog.timestamp }}</p>
        <p><strong>SecID:</strong> {{ currentLog.secId }}</p>
        <h3>Detailed Info:</h3>
        <pre class="json-viewer">{{ JSON.stringify(currentLog.details, null, 2) }}</pre>
      </div>
    </el-dialog>

    <div v-if="currentTab === 'users'" class="tab-content">
      <h2>User Management</h2>
      <el-button @click="fetchUsers">Refresh Users</el-button>
      <el-table :data="users" style="width: 100%" stripe>
        <el-table-column prop="userId" label="ID" width="80" />
        <el-table-column prop="username" label="Username" />
        <el-table-column prop="email" label="Email" />
        <el-table-column prop="role" label="Role">
          <template #default="scope">
            <el-tag :type="getRoleType(scope.row.role)">{{ scope.row.role || 'USER' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions">
          <template #default="scope">
            <el-button
              v-if="scope.row.role !== 'ADMIN' && scope.row.role !== 'SUPER_ADMIN'"
              size="small"
              type="danger"
              @click="promoteUser(scope.row)"
            >Promote to Admin</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage } from 'element-plus'

const currentTab = ref('logs')
const logs = ref([])
const users = ref([])
const detailsVisible = ref(false)
const currentLog = ref(null)

onMounted(() => {
  fetchLogs()
  fetchUsers()
})

const fetchLogs = async () => {
  try {
    const res = await request.get('/admin/logs')
    logs.value = res.data
  } catch (e) {
    console.error(e)
  }
}

const showDetails = (row) => {
  currentLog.value = row
  detailsVisible.value = true
}

const fetchUsers = async () => {
  try {
    const res = await request.get('/admin/users')
    users.value = res.data
  } catch (e) {
    console.error(e)
  }
}

const promoteUser = async (user) => {
  try {
    await request.post('/admin/promote', { userId: user.userId })
    ElMessage.success(`Promoted ${user.username} to Admin`)
    fetchUsers()
  } catch (e) {
    ElMessage.error('Failed to promote user')
  }
}

const getRoleType = (role) => {
  if (role === 'SUPER_ADMIN') return 'danger'
  if (role === 'ADMIN') return 'warning'
  return 'info'
}
</script>

<style scoped>
.admin-panel {
  padding: 20px;
}
.nav-tabs {
  margin-bottom: 20px;
}
.nav-tabs button {
  padding: 10px 20px;
  margin-right: 10px;
  cursor: pointer;
  background: #f0f0f0;
  border: none;
  border-radius: 4px;
}
.nav-tabs button.active {
  background: #409EFF;
  color: white;
}
.tab-content {
  background: white;
  padding: 20px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1);
}
.json-viewer {
  background: #f4f4f4;
  padding: 10px;
  border-radius: 4px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
