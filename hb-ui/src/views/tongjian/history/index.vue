<!-- 读通鉴-阅读记录 -->
<template>
  <div>
    <div class="tab-container">
      <div class="total-desc">
        <div class="menu-name">阅读记录</div>
        <div class="segment"></div>
        <div class="menu-total">记录总数：{{ total }}</div>
      </div>
    </div>
    <div class="app-container">
      <div class="ncdb-tb">
        <div class="my-table">
          <el-table v-loading="loading" :data="historyList">
            <el-table-column label="卷" prop="volumeTitle" width="180" :show-overflow-tooltip="true" />
            <el-table-column label="纪年" width="260">
              <template #default="scope">
                {{ scope.row.emperorName }} · {{ scope.row.reignName }} {{ scope.row.yearAnno }}
              </template>
            </el-table-column>
            <el-table-column label="读到" width="120">
              <template #default="scope">
                <span v-if="scope.row.lastNumber > 0">第 {{ scope.row.lastNumber }} 段</span>
                <span v-else>已开始</span>
              </template>
            </el-table-column>
            <el-table-column label="最近阅读时间" prop="accessTime" width="180">
              <template #default="scope">{{ parseTime(scope.row.accessTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="scope">
                <el-button size="small" type="primary" link icon="Reading" @click="gotoRead(scope.row)">继续阅读</el-button>
                <el-button size="small" type="danger" link icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && historyList.length === 0" description="还没有阅读记录，去读一卷通鉴吧" />
        </div>
        <pagination
          v-show="total > 10"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </div>
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

getList()
</script>
