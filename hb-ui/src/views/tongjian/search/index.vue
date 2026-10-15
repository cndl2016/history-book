<!-- 读通鉴-全文搜索 -->
<template>
  <div>
    <div class="tab-container">
      <div class="total-desc">
        <div class="menu-name">全文搜索</div>
        <div class="segment"></div>
        <div class="menu-total">结果总数：{{ total }}</div>
      </div>
    </div>
    <div class="app-container">
      <div class="ncdb-tb">
        <div class="top">
          <el-form :model="queryParams" :inline="true" @submit.prevent>
            <el-form-item>
              <el-input
                v-model="queryParams.searchValue"
                placeholder="搜索原文/译文/标签，如：三家分晋、司马光、晋阳"
                style="width: 340px"
                clearable
                suffix-icon="Search"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </el-form-item>
            <el-form-item>
              <el-select v-model="queryParams.volumeId" placeholder="全部卷" clearable filterable style="width: 220px" @change="handleQuery">
                <el-option v-for="v in volumeOptions" :key="v.volumeId" :label="v.title + '（' + v.ji + '）'" :value="v.volumeId" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="RefreshLeft" type="primary" link @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
        <div class="my-table">
          <el-table v-loading="loading" :data="resultList" @row-click="gotoRead" row-class-name="pointer-row">
            <el-table-column label="卷" prop="volumeTitle" width="150" :show-overflow-tooltip="true" />
            <el-table-column label="纪年" prop="reignTitle" width="220" :show-overflow-tooltip="true" />
            <el-table-column label="段" prop="number" width="60" />
            <el-table-column label="内容" :show-overflow-tooltip="false">
              <template #default="scope">
                <div class="snippet" v-html="highlight(scope.row)"></div>
                <div v-if="scope.row.translation" class="snippet-fanyi" v-html="highlightText(scope.row.translation)"></div>
              </template>
            </el-table-column>
            <el-table-column label="标签" width="180">
              <template #default="scope">
                <el-tag v-for="tag in (scope.row.tags || '').split(',').filter(t => t)" :key="tag" size="small" effect="plain" style="margin-right:4px">{{ tag }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && searched && resultList.length === 0" description="未找到匹配内容（仅已同步的正文可搜索）" />
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

<script setup name="TjSearch">
import { searchContent, getCatalogTree } from '@/api/tongjian'

const router = useRouter()

const loading = ref(false)
const searched = ref(false)
const total = ref(0)
const resultList = ref([])
const volumeOptions = ref([])

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  searchValue: '',
  volumeId: null
})

function escapeHtml(s) {
  return (s || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}

function highlightText(text) {
  const kw = (queryParams.value.searchValue || '').trim()
  const safe = escapeHtml(text)
  if (!kw) return safe.length > 120 ? safe.slice(0, 120) + '…' : safe
  const escaped = kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const html = safe.replace(new RegExp(escaped, 'g'), m => `<mark>${m}</mark>`)
  return html.length > 600 ? html.slice(0, 600) + '…' : html
}

function highlight(row) {
  return highlightText(row.simplified || row.original)
}

function getList() {
  if (!(queryParams.value.searchValue || '').trim()) {
    resultList.value = []
    total.value = 0
    return
  }
  loading.value = true
  searched.value = true
  searchContent(queryParams.value).then(res => {
    resultList.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.value = { pageNum: 1, pageSize: 10, searchValue: '', volumeId: null }
  getList()
}

function gotoRead(row) {
  router.push({ path: '/tongjian/read', query: { reignId: row.reignId, number: row.number } })
}

onMounted(() => {
  getCatalogTree().then(res => {
    volumeOptions.value = (res.data || []).map(v => ({ volumeId: v.volumeId, title: v.title, ji: v.ji }))
  })
})
</script>

<style scoped>
.snippet {
  line-height: 1.7;
  font-size: 13.5px;
}
.snippet-fanyi {
  line-height: 1.6;
  font-size: 12.5px;
  color: #78716c;
  margin-top: 4px;
}
:deep(mark) {
  background: #fde68a;
  color: #92400e;
  padding: 0 1px;
}
:deep(.pointer-row) {
  cursor: pointer;
}
</style>
