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

      <!-- 古本同步面板：把元刊本扫描页与段落锚点缓存到本地，之后查看古本不再需要令牌 -->
      <el-card shadow="never" style="margin-top: 16px">
        <template #header>
          <div class="guben-header">
            <span class="guben-title">古本同步</span>
            <el-tag :type="guben.running ? 'warning' : 'info'" size="small">
              {{ guben.running ? '同步中' : '空闲' }}
            </el-tag>
          </div>
        </template>
        <el-row :gutter="16">
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-num">{{ guben.anchorCount || 0 }}</div>
              <div class="stat-label">已缓存锚点</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-num">{{ guben.localPageCount || 0 }}</div>
              <div class="stat-label">已本地化扫描页</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-num">{{ gubenSizeText }}</div>
              <div class="stat-label">本地图片占用</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-num">{{ gubenPercent }}%</div>
              <div class="stat-label">同步进度</div>
            </div>
          </el-col>
        </el-row>
        <div class="progress-row" style="margin-top: 16px">
          <el-progress :percentage="gubenPercent" :stroke-width="18" style="flex:1" />
        </div>
        <div class="status-line">
          <span class="message">{{ guben.message || '尚未开始同步' }}</span>
          <span v-if="guben.failed" class="guben-failed">失败 {{ guben.failed }} 条（可再次启动续跑）</span>
        </div>
        <div class="actions">
          <el-button type="primary" icon="VideoPlay" :disabled="guben.running" v-hasPermi="['tongjian:read:list']" @click="handleGubenStart">启动同步</el-button>
          <el-button type="danger" icon="VideoPause" :disabled="!guben.running" v-hasPermi="['tongjian:read:list']" @click="handleGubenStop">停止</el-button>
          <el-button icon="Refresh" @click="refreshGuben">刷新状态</el-button>
        </div>
        <el-alert type="info" :closable="false" style="margin-top: 16px">
          <p>1. 古本同步把段落的古本定位（锚框坐标）和元刊本扫描页图片缓存到本地，已缓存的内容查看时完全离线，不再需要 dutongjian.com 令牌。</p>
          <p>2. 同步需要在「系统管理 → 参数设置」中配置 <code>tongjian.guben.token</code>（dutongjian.com 账号令牌，免费账号即可）；令牌有效期内同步一次即可长期使用。</p>
          <p>3. 限速 300ms/次并含图片下载，可随时停止；再次启动自动跳过已缓存段落。只处理已同步到本地的正文段落，正文增加后可重复同步。</p>
        </el-alert>
      </el-card>
    </div>
  </div>
</template>

<script setup name="TjSync">
import { getSyncStatus, startSync, stopSync, getGubenCrawlStatus, startGubenCrawl, stopGubenCrawl } from '@/api/tongjian'

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

// ---------------- 古本同步 ----------------

const guben = ref({})
let gubenTimer = null

const gubenPercent = computed(() => {
  if (!guben.value.total) return 0
  return Math.floor(((guben.value.done || 0) + (guben.value.failed || 0)) * 100 / guben.value.total)
})

const gubenSizeText = computed(() => {
  const bytes = guben.value.localBytes || 0
  if (bytes >= 1024 * 1024 * 1024) return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
  if (bytes >= 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return Math.floor(bytes / 1024) + ' KB'
})

function refreshGuben() {
  getGubenCrawlStatus().then(res => {
    guben.value = res.data || {}
    if (guben.value.running) {
      startGubenPolling()
    } else {
      stopGubenPolling()
    }
  })
}

function startGubenPolling() {
  if (!gubenTimer) {
    gubenTimer = setInterval(refreshGubenStatusOnly, 3000)
  }
}

function stopGubenPolling() {
  if (gubenTimer) {
    clearInterval(gubenTimer)
    gubenTimer = null
  }
}

function refreshGubenStatusOnly() {
  getGubenCrawlStatus().then(res => {
    guben.value = res.data || {}
    if (!guben.value.running) stopGubenPolling()
  })
}

function handleGubenStart() {
  startGubenCrawl().then(res => {
    if (res.code === 500) return
    proxy.$modal.msgSuccess('同步已启动')
    refreshGuben()
  })
}

function handleGubenStop() {
  proxy.$modal.confirm('确认停止同步吗？已缓存的段落会保留，下次继续。').then(() => {
    return stopGubenCrawl()
  }).then(() => {
    proxy.$modal.msgSuccess('已请求停止')
    setTimeout(refreshGuben, 1000)
  }).catch(() => {})
}

onMounted(() => {
  refresh()
  refreshGuben()
})
onUnmounted(() => {
  stopPolling()
  stopGubenPolling()
})
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
.guben-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.guben-title {
  font-size: 15px;
  font-weight: 600;
  color: #44403c;
}
.guben-failed {
  font-size: 12.5px;
  color: #b91c1c;
}
</style>
