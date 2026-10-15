<!-- 读通鉴-阅读记录（古典纸面卡片列表，风格对齐阅读页） -->
<template>
  <div>
    <div class="tab-container">
      <div class="total-desc">
        <div class="menu-name">阅读记录</div>
        <div class="segment"></div>
        <div class="menu-total">记录总数：{{ total }}</div>
      </div>
    </div>
    <div class="app-container history-page" v-loading="loading">
      <div class="record-list" v-if="historyList.length > 0">
        <div
          v-for="item in historyList"
          :key="item.historyId"
          class="record-card"
          @click="gotoRead(item)"
        >
          <!-- 左侧：卷与纪年 -->
          <div class="record-main">
            <div class="record-reign">
              {{ item.emperorName }} · {{ item.reignName }}
              <span class="record-anno">{{ item.yearAnno }}</span>
            </div>
            <div class="record-volume">{{ item.volumeTitle }}</div>
          </div>
          <!-- 中间：进度 -->
          <div class="record-progress">
            <span class="progress-label">读至</span>
            <span class="progress-value">{{ item.lastNumber > 0 ? `第 ${item.lastNumber} 段` : '卷首' }}</span>
          </div>
          <!-- 右侧：时间与操作 -->
          <div class="record-side">
            <div class="record-time">{{ friendlyTime(item.accessTime) }}</div>
            <div class="record-actions" @click.stop>
              <el-button size="small" type="primary" link icon="Reading" @click="gotoRead(item)">继续阅读</el-button>
              <el-button size="small" type="danger" link icon="Delete" @click="handleDelete(item)">删除</el-button>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && historyList.length === 0" description="还没有阅读记录，去读一卷通鉴吧" />
      <pagination
        v-show="total > 10"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </div>
  </div>
</template>

<script setup name="TjHistory">
import { listHistory, delHistory } from '@/api/tongjian'

const router = useRouter()
const { proxy } = getCurrentInstance()

const loading = ref(true)
const total = ref(0)
const historyList = ref([])
const queryParams = ref({ pageNum: 1, pageSize: 10 })

function getList() {
  loading.value = true
  listHistory(queryParams.value).then(res => {
    historyList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}

function gotoRead(row) {
  router.push({ path: '/tongjian/read', query: { reignId: row.reignId, number: row.lastNumber || undefined } })
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除该条阅读记录吗？').then(() => {
    return delHistory(row.historyId)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

/** 友好时间：今天/昨天显示时分，更早显示日期 */
function friendlyTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const sameDay = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  if (sameDay(d, now)) return `今天 ${hm}`
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (sameDay(d, yesterday)) return `昨天 ${hm}`
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`
}

getList()
</script>

<style scoped>
.history-page {
  background: #f6f4ef;
  min-height: calc(100vh - 140px);
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 960px;
}

.record-card {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #fdfcfa;
  border: 1px solid #e9e4da;
  border-radius: 6px;
  padding: 16px 22px;
  cursor: pointer;
  transition: border-color .15s ease, box-shadow .15s ease, transform .15s ease;
}

.record-card:hover {
  border-color: #d6c9b4;
  box-shadow: 0 4px 16px rgba(120, 100, 70, .08);
  transform: translateY(-1px);
}

.record-main {
  flex: 1;
  min-width: 0;
}

.record-reign {
  font-family: 'Songti SC', 'SimSun', serif;
  font-size: 16px;
  font-weight: 600;
  color: #292524;
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.record-anno {
  margin-left: 8px;
  font-size: 12.5px;
  font-weight: 400;
  color: #a8a29e;
  letter-spacing: 0;
}

.record-volume {
  margin-top: 6px;
  font-size: 12.5px;
  color: #b45309;
  letter-spacing: 1px;
}

.record-progress {
  flex-shrink: 0;
  width: 110px;
  text-align: center;
  border-left: 1px solid #efe9df;
  border-right: 1px solid #efe9df;
  padding: 4px 0;
}

.progress-label {
  display: block;
  font-size: 12px;
  color: #a8a29e;
}

.progress-value {
  display: block;
  margin-top: 2px;
  font-size: 14px;
  color: #44403c;
  font-family: 'Songti SC', 'SimSun', serif;
}

.record-side {
  flex-shrink: 0;
  width: 170px;
  text-align: right;
}

.record-time {
  font-size: 12.5px;
  color: #78716c;
}

.record-actions {
  margin-top: 6px;
}

@media (max-width: 768px) {
  .record-card {
    flex-wrap: wrap;
    gap: 10px;
  }
  .record-progress {
    border: none;
    width: auto;
    text-align: left;
  }
  .record-side {
    width: auto;
    margin-left: auto;
  }
}
</style>
