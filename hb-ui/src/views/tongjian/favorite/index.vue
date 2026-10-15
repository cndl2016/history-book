<!-- 读通鉴-我的收藏 -->
<template>
  <div>
    <div class="tab-container">
      <div class="total-desc">
        <div class="menu-name">我的收藏</div>
        <div class="segment"></div>
        <div class="menu-total">收藏总数：{{ total }}</div>
      </div>
    </div>
    <div class="app-container">
      <div class="ncdb-tb">
        <div class="my-table">
          <el-table v-loading="loading" :data="favoriteList">
            <el-table-column label="卷" prop="content.volumeTitle" width="150" :show-overflow-tooltip="true">
              <template #default="scope">{{ scope.row.content && scope.row.content.volumeTitle }}</template>
            </el-table-column>
            <el-table-column label="纪年" width="200" :show-overflow-tooltip="true">
              <template #default="scope">{{ scope.row.content && scope.row.content.reignTitle }}</template>
            </el-table-column>
            <el-table-column label="段" width="60">
              <template #default="scope">{{ scope.row.content && scope.row.content.number }}</template>
            </el-table-column>
            <el-table-column label="内容">
              <template #default="scope">
                <div class="snippet">{{ scope.row.content && (scope.row.content.simplified || scope.row.content.original) }}</div>
                <div class="snippet-fanyi" v-if="scope.row.content && scope.row.content.translation">{{ scope.row.content.translation }}</div>
              </template>
            </el-table-column>
            <el-table-column label="收藏时间" prop="createTime" width="170">
              <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="scope">
                <el-button size="small" type="primary" link icon="Reading" @click="gotoRead(scope.row)">阅读</el-button>
                <el-button size="small" type="danger" link icon="Delete" v-hasPermi="['tongjian:favorite:remove']" @click="handleRemove(scope.row)">取消收藏</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && favoriteList.length === 0" description="还没有收藏，阅读时点击段落左侧的星标即可收藏" />
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

<script setup name="TjFavorite">
import { listFavorite, delFavorite } from '@/api/tongjian'

const router = useRouter()
const { proxy } = getCurrentInstance()

const loading = ref(true)
const total = ref(0)
const favoriteList = ref([])
const queryParams = ref({ pageNum: 1, pageSize: 10 })

function getList() {
  loading.value = true
  listFavorite(queryParams.value).then(res => {
    favoriteList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}

function gotoRead(row) {
  if (!row.content) return
  router.push({ path: '/tongjian/read', query: { reignId: row.content.reignId, number: row.content.number } })
}

function handleRemove(row) {
  proxy.$modal.confirm('确认取消收藏该段落吗？').then(() => {
    return delFavorite(row.contentId)
  }).then(() => {
    proxy.$modal.msgSuccess('已取消收藏')
    getList()
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.snippet {
  line-height: 1.7;
  font-size: 13.5px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.snippet-fanyi {
  line-height: 1.6;
  font-size: 12.5px;
  color: #78716c;
  margin-top: 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
