<!-- 读通鉴-数据同步 从 dutongjian.com 公开接口限速导入，断点续传 -->
<template>
  <div>
    <div class="tab-container">
      <div class="total-desc">
        <div class="menu-name">数据同步</div>
        <div class="segment"></div>
        <div class="menu-total">迁移自 Dutongjian 项目的公开内容导入能力</div>
      </div>
    </div>
    <div class="app-container">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-num">{{ state.totalReigns || 0 }}</div>
            <div class="stat-label">纪年总数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-num">{{ state.completedReigns || 0 }}</div>
            <div class="stat-label">已同步纪年</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-num">{{ state.contentCount || 0 }}</div>
            <div class="stat-label">已入库段落</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-num">{{ percent }}%</div>
            <div class="stat-label">完成度</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" style="margin-top: 16px">
        <div class="progress-row">
          <el-progress :percentage="percent" :stroke-width="18" :status="progressStatus" style="flex:1" />
        </div>
        <div class="status-line">
          <el-tag :type="statusTagType" size="small">{{ statusText }}</el-tag>
          <span class="message">{{ state.message }}</span>
        </div>
        <div class="actions">
          <el-button type="primary" icon="VideoPlay" :disabled="state.running" v-hasPermi="['tongjian:sync:start']" @click="handleStart">开始同步</el-button>
          <el-button type="danger" icon="VideoPause" :disabled="!state.running" v-hasPermi="['tongjian:sync:stop']" @click="handleStop">停止</el-button>
          <el-button icon="Refresh" @click="refresh">刷新状态</el-button>
        </div>
        <el-alert type="info" :closable="false" style="margin-top: 16px">
          <p>1. 数据来源于 dutongjian.com 的公开只读接口（/api/table_of_contents、/api/reign），不登录、不绕过任何权限。</p>
          <p>2. 同步严格限速（每 5 秒一个请求），全本约 1405 个纪年，预计需要 2 小时左右；可随时停止，再次启动会自动跳过已完成的纪年（断点续传）。</p>
          <p>3. 系统已内置「卷一（周纪一）」全部正文作为种子数据，未同步的纪年在目录中标记为「未导入」。</p>
        </el-alert>
      </el-card>
    </div>
  </div>
</template>

<script setup name="TjSync">
import { getSyncStatus, startSync, stopSync } from '@/api/tongjian'

const { proxy } = getCurrentInstance()

const state = ref({})
let timer = null

const percent = computed(() => {
  if (!state.value.totalReigns) return 0
  return Math.floor((state.value.completedReigns || 0) * 100 / state.value.totalReigns)
})

const progressStatus = computed(() => {
  if (percent.value >= 100) return 'success'
  return undefined
})

const statusText = computed(() => {
  if (state.value.running) return '同步中'
  if (state.value.status === '2') return '已停止'
  if (percent.value >= 100) return '已完成'
  return '空闲'
})

const statusTagType = computed(() => {
  if (state.value.running) return 'warning'
  if (percent.value >= 100) return 'success'
  return 'info'
})

function refresh() {
  getSyncStatus().then(res => {
    state.value = res.data || {}
    if (state.value.running) {
      startPolling()
    } else {
      stopPolling()
    }
  })
}

function startPolling() {
  if (!timer) {
    timer = setInterval(refreshStatusOnly, 3000)
  }
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function refreshStatusOnly() {
  getSyncStatus().then(res => {
    state.value = res.data || {}
    if (!state.value.running) stopPolling()
  })
}

function handleStart() {
  startSync().then(() => {
    proxy.$modal.msgSuccess('同步已启动')
    refresh()
  })
}

function handleStop() {
  proxy.$modal.confirm('确认停止同步吗？已完成的纪年会保留，下次继续。').then(() => {
    return stopSync()
  }).then(() => {
    proxy.$modal.msgSuccess('已请求停止')
    setTimeout(refresh, 1000)
  }).catch(() => {})
}

onMounted(refresh)
onUnmounted(stopPolling)
</script>

<style scoped>
.stat-card {
  text-align: center;
}
.stat-num {
  font-size: 30px;
  font-weight: 600;
  color: #b45309;
}
.stat-label {
  font-size: 13px;
  color: #78716c;
  margin-top: 4px;
}
.progress-row {
  display: flex;
  align-items: center;
}
.status-line {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.message {
  font-size: 13px;
  color: #57534e;
}
.actions {
  margin-top: 16px;
}
</style>
