<!-- 读通鉴-在线阅读 版式对齐 dutongjian.com：面包屑 + 章节题名 + 原文/译文左右分栏 + 三级目录树 -->
<template>
  <div class="tj-read">
    <!-- 左侧目录树：卷 -> 帝王 -> 纪年 -->
    <div class="tj-catalog">
      <div class="catalog-header">
        <span class="catalog-title">资治通鉴</span>
        <el-input v-model="filterText" placeholder="筛选卷/帝王/纪年" size="small" clearable prefix-icon="Search" />
      </div>
      <el-scrollbar class="catalog-tree">
        <el-tree
          ref="treeRef"
          :data="catalogTree"
          :props="treeProps"
          node-key="nodeKey"
          :filter-node-method="filterNode"
          highlight-current
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <span class="tree-node" :class="'node-' + data.level">
              <span class="node-label">{{ data.label }}</span>
              <span v-if="data.level === 'reign' && data.synced !== '1'" class="node-unsynced">未导入</span>
            </span>
          </template>
        </el-tree>
      </el-scrollbar>
    </div>

    <!-- 右侧阅读区 -->
    <div class="tj-reader">
      <!-- 面包屑 + 工具栏 -->
      <div class="reader-toolbar">
        <div class="breadcrumb" v-if="currentReign">
          <span class="crumb">资治通鉴</span>
          <span class="crumb-sep">/</span>
          <span class="crumb">{{ currentReign.volumeTitle }}<template v-if="currentReign.ji">（{{ currentReign.ji }}）</template></span>
          <span class="crumb-sep">/</span>
          <span class="crumb">{{ currentReign.emperorName }}</span>
          <span class="crumb-sep">/</span>
          <span class="crumb current">{{ currentReign.reignName }}<template v-if="currentReign.yearInt">（{{ formatYear(currentReign.yearInt) }}）</template></span>
        </div>
        <div class="breadcrumb" v-else><span class="crumb">资治通鉴</span></div>
        <div class="toolbar-right">
          <span class="nav-link" :class="{ disabled: !prevReign }" @click="gotoReign(prevReign)">‹ 上一纪</span>
          <span class="nav-link" :class="{ disabled: !nextReign }" @click="gotoReign(nextReign)">下一纪 ›</span>
          <el-divider direction="vertical" />
          <span class="toggle" :class="{ on: showSimplified }" @click="showSimplified = !showSimplified">简体</span>
          <span class="toggle" :class="{ on: showTranslation }" @click="showTranslation = !showTranslation">译文</span>
          <span class="toggle" :class="{ on: showNotes }" @click="showNotes = !showNotes">注释</span>
        </div>
      </div>

      <!-- 正文 -->
      <el-scrollbar class="reader-body" v-loading="loading">
        <div class="page">
          <!-- 章节题名 -->
          <div v-if="currentReign" class="chapter-head">
            <div class="chapter-title">{{ currentReign.reignName }}</div>
            <div class="chapter-sub">{{ currentReign.yearAnno }}</div>
          </div>

          <div v-if="contents.length > 0" class="paragraph-list">
            <div
              v-for="item in contents"
              :key="item.contentId"
              :id="'para-' + item.number"
              class="paragraph"
              :class="{ 'para-target': targetNumber === item.number }"
            >
              <!-- 段落元信息行 -->
              <div class="para-meta">
                <el-tooltip :content="item.favored ? '取消收藏' : '收藏本段'" placement="top">
                  <el-icon class="meta-icon" :class="{ favored: item.favored }" @click="toggleFavorite(item)">
                    <StarFilled v-if="item.favored" /><Star v-else />
                  </el-icon>
                </el-tooltip>
                <el-tooltip content="标记读到这里" placement="top">
                  <el-icon class="meta-icon" @click="markProgress(item)"><Flag /></el-icon>
                </el-tooltip>
                <span class="guben-btn" @click="openGuben(item)">古本</span>
                <template v-if="item.tags">
                  <span class="meta-sep">标签</span>
                  <span v-for="tag in item.tags.split(',')" :key="tag" class="tag-chip">{{ tag }}</span>
                </template>
                <span class="para-number">{{ item.number }} / {{ contents.length }}</span>
              </div>

              <!-- 原文 / 译文 左右分栏 -->
              <div class="para-body" :class="{ single: !showTranslation || !item.translation }">
                <div class="para-original">{{ showSimplified ? (item.simplified || item.original) : (item.original || item.simplified) }}</div>
                <div v-if="showTranslation && item.translation" class="para-translation">{{ item.translation }}</div>
              </div>

              <!-- 注释 -->
              <div v-if="showNotes && noteList(item).length > 0" class="para-notes">
                <div v-for="(note, idx) in noteList(item)" :key="idx" class="note-item">
                  <span class="note-type">〔{{ note.type }}〕</span>{{ showSimplified && note.textJt ? note.textJt : note.text }}
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else-if="currentReign && !loading" description="该纪年正文尚未导入，请到「数据同步」页面启动导入">
            <el-button type="primary" @click="$router.push('/tongjian/sync')">去同步</el-button>
          </el-empty>
          <el-empty v-else-if="!loading" description="从左侧目录选择一卷一纪开始读通鉴" />
        </div>
      </el-scrollbar>
    </div>

    <!-- 古本查看器 -->
    <GubenViewer v-model:visible="gubenVisible" :paragraph="gubenParagraph" />
  </div>
</template>

<script setup name="TjRead">
import { getCatalogTree, getReign, listByReign, addFavorite, delFavorite, recordHistory } from '@/api/tongjian'
import { Star, StarFilled, Flag } from '@element-plus/icons-vue'
import GubenViewer from './GubenViewer.vue'

const route = useRoute()
const { proxy } = getCurrentInstance()

const filterText = ref('')
const treeRef = ref(null)
const catalogTree = ref([])
const reignSequence = ref([])   // 扁平化的纪年序列（用于上一纪/下一纪）
const treeProps = { label: 'label', children: 'children' }

const currentReign = ref(null)
const contents = ref([])
const loading = ref(false)
const targetNumber = ref(null)

const showSimplified = ref(true)
const showTranslation = ref(true)
const showNotes = ref(true)

// 古本查看器
const gubenVisible = ref(false)
const gubenParagraph = ref(null)

const currentIndex = computed(() => reignSequence.value.findIndex(r => currentReign.value && r.reignId === currentReign.value.reignId))
const prevReign = computed(() => currentIndex.value > 0 ? reignSequence.value[currentIndex.value - 1] : null)
const nextReign = computed(() => currentIndex.value >= 0 && currentIndex.value < reignSequence.value.length - 1 ? reignSequence.value[currentIndex.value + 1] : null)

watch(filterText, val => treeRef.value && treeRef.value.filter(val))

function filterNode(value, data) {
  if (!value) return true
  return data.label && data.label.includes(value)
}

function noteList(item) {
  if (!item.notes) return []
  try { return JSON.parse(item.notes) } catch (e) { return [] }
}

/** -403 -> 公元前403年；960 -> 公元960年 */
function formatYear(yearInt) {
  if (yearInt == null) return ''
  return yearInt < 0 ? `公元前${-yearInt}年` : `公元${yearInt}年`
}

/** 目录树：卷 -> 帝王 -> 纪年 */
function loadCatalog(selectReignId) {
  getCatalogTree().then(res => {
    const seq = []
    catalogTree.value = (res.data || []).map(v => {
      // 按帝王分组，保持原有顺序
      const emperorMap = new Map()
      ;(v.children || []).forEach(r => {
        if (!emperorMap.has(r.emperorName)) emperorMap.set(r.emperorName, [])
        emperorMap.get(r.emperorName).push(r)
      })
      let ei = 0
      const emperorNodes = [...emperorMap.entries()].map(([emperor, reigns]) => {
        ei++
        return {
          nodeKey: `e${v.volumeId}-${ei}`,
          label: emperor,
          level: 'emperor',
          children: reigns.map(r => {
            seq.push(r)
            return {
              nodeKey: 'r' + r.reignId,
              label: r.reignName + (r.yearInt != null ? `（${formatYear(r.yearInt)}）` : ''),
              level: 'reign',
              reignId: r.reignId,
              synced: r.synced
            }
          })
        }
      })
      return { nodeKey: 'v' + v.volumeId, label: `${v.title}（${v.ji}）`, level: 'volume', children: emperorNodes }
    })
    reignSequence.value = seq
    if (selectReignId) {
      const node = seq.find(r => String(r.reignId) === String(selectReignId))
      if (node) openReign(node)
    } else if (seq.length > 0) {
      openReign(seq[0])
    }
  })
}

function handleNodeClick(data) {
  if (data.level === 'reign') openReign({ reignId: data.reignId })
}

function gotoReign(reign) {
  if (reign) openReign(reign)
}

/** 打开一个纪年：加载正文并记录阅读行为 */
function openReign(reign) {
  loading.value = true
  targetNumber.value = null
  Promise.all([getReign(reign.reignId), listByReign(reign.reignId)]).then(([reignRes, contentRes]) => {
    currentReign.value = reignRes.data
    contents.value = contentRes.data || []
    loading.value = false
    treeRef.value && treeRef.value.setCurrentKey('r' + reign.reignId)
    // 记录阅读进度（打开即记录，进度由“标记读到这里”更新）
    recordHistory(reign.reignId, 0)
    // 从搜索/收藏跳入时定位段落
    const num = Number(route.query.number)
    if (num) {
      nextTick(() => {
        targetNumber.value = num
        const el = document.getElementById('para-' + num)
        el && el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
    }
  }).catch(() => { loading.value = false })
}

function toggleFavorite(item) {
  if (item.favored) {
    delFavorite(item.contentId).then(() => {
      item.favored = false
      proxy.$modal.msgSuccess('已取消收藏')
    })
  } else {
    addFavorite(item.contentId).then(() => {
      item.favored = true
      proxy.$modal.msgSuccess('收藏成功')
    })
  }
}

function markProgress(item) {
  recordHistory(currentReign.value.reignId, item.number).then(() => {
    proxy.$modal.msgSuccess(`已记录进度：第 ${item.number} 段`)
  })
}

/** 打开古本：段落的源站ID + 段内序号（0基）+ 原文用于扫描页定位 */
function openGuben(item) {
  gubenParagraph.value = {
    tongjianId: item.tongjianId,
    startIndex: (item.number || 1) - 1,
    selectedText: item.original || item.simplified || ''
  }
  gubenVisible.value = true
}

onMounted(() => {
  loadCatalog(route.query.reignId)
})
</script>

<style scoped>
/* ===== 整体：宣纸底色 + 衬线书版 ===== */
.tj-read {
  display: flex;
  height: calc(100vh - 84px);
  background: #f6f4ef;
  color: #292524;
}

/* ===== 左侧目录 ===== */
.tj-catalog {
  width: 264px;
  flex-shrink: 0;
  background: #fdfcfa;
  border-right: 1px solid #e9e4da;
  display: flex;
  flex-direction: column;
}
.catalog-header {
  padding: 14px 14px 10px;
  border-bottom: 1px solid #efe9df;
}
.catalog-title {
  display: block;
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 10px;
  font-family: 'Songti SC', 'SimSun', serif;
  letter-spacing: 2px;
}
.catalog-tree {
  flex: 1;
  padding: 6px 4px;
}
.catalog-tree :deep(.el-tree) {
  background: transparent;
  --el-tree-node-hover-bg-color: #f3efe6;
  --el-tree-current-node-bg-color: #eef2f7;
  color: #44403c;
}
.catalog-tree :deep(.el-tree-node__content) {
  height: 30px;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}
.node-volume .node-label {
  font-weight: 600;
  font-size: 13.5px;
  font-family: 'Songti SC', 'SimSun', serif;
}
.node-emperor .node-label {
  font-size: 13px;
  color: #57534e;
}
.node-reign .node-label {
  font-size: 13px;
  color: #44403c;
}
.node-unsynced {
  font-size: 11px;
  color: #b8b0a4;
  border: 1px solid #e9e4da;
  border-radius: 3px;
  padding: 0 4px;
  flex-shrink: 0;
}

/* ===== 右侧阅读区 ===== */
.tj-reader {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  container-type: inline-size;
}
.reader-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  height: 46px;
  background: #fdfcfa;
  border-bottom: 1px solid #e9e4da;
  flex-wrap: wrap;
}
.breadcrumb {
  font-size: 13px;
  color: #78716c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.crumb-sep {
  margin: 0 6px;
  color: #d6d3d1;
}
.crumb.current {
  color: #292524;
  font-weight: 500;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
}
.nav-link {
  color: #57534e;
  cursor: pointer;
  user-select: none;
}
.nav-link:hover {
  color: #1c4ed8;
}
.nav-link.disabled {
  color: #d6d3d1;
  cursor: default;
}
.toggle {
  cursor: pointer;
  color: #a8a29e;
  user-select: none;
  padding-bottom: 1px;
  border-bottom: 2px solid transparent;
}
.toggle.on {
  color: #1c4ed8;
  border-bottom-color: #1c4ed8;
}

/* ===== 书页 ===== */
.reader-body {
  flex: 1;
}
.page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 28px 36px 60px;
}
.chapter-head {
  text-align: center;
  margin-bottom: 30px;
}
.chapter-title {
  font-size: 30px;
  font-weight: 600;
  font-family: 'Songti SC', 'SimSun', serif;
  letter-spacing: 6px;
}
.chapter-sub {
  margin-top: 8px;
  font-size: 14px;
  color: #a8a29e;
  font-family: 'Songti SC', 'SimSun', serif;
}

/* ===== 段落 ===== */
.paragraph {
  padding: 14px 4px 18px;
  border-bottom: 1px solid #ece7dd;
}
.paragraph.para-target {
  background: #fdf6e7;
  outline: 1px solid #ecd9b0;
  border-radius: 4px;
}
.para-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  min-height: 20px;
}
.meta-icon {
  font-size: 14px;
  color: #c2bab0;
  cursor: pointer;
}
.meta-icon:hover {
  color: #b45309;
}
.meta-icon.favored {
  color: #d97706;
}
.meta-sep {
  font-size: 12px;
  color: #a8a29e;
  margin-left: 6px;
}
.guben-btn {
  font-size: 12px;
  color: #b45309;
  border: 1px solid #d97706;
  border-radius: 3px;
  padding: 0 8px;
  cursor: pointer;
  user-select: none;
  line-height: 18px;
}
.guben-btn:hover {
  background: #b45309;
  color: #fff;
}
.tag-chip {
  font-size: 12px;
  color: #57534e;
  background: #f1ede5;
  border-radius: 3px;
  padding: 1px 7px;
}
.para-number {
  margin-left: auto;
  font-size: 12px;
  color: #b8b0a4;
  font-variant-numeric: tabular-nums;
}

/* 原文 / 译文 左右分栏 */
.para-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
}
.para-body.single {
  grid-template-columns: 1fr;
}
.para-original {
  font-size: 16.5px;
  line-height: 2;
  font-family: 'Songti SC', 'SimSun', serif;
  color: #1c1917;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: justify;
  padding-right: 24px;
}
.para-translation {
  font-size: 14px;
  line-height: 1.95;
  color: #78716c;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: justify;
  border-left: 1px solid #ece7dd;
  padding-left: 24px;
}

/* 注释 */
.para-notes {
  margin-top: 10px;
}
.note-item {
  font-size: 12.5px;
  color: #a8a29e;
  line-height: 1.8;
}
.note-type {
  color: #b45309;
}

@container (max-width: 760px) {
  .para-body {
    grid-template-columns: 1fr;
  }
  .para-original {
    padding-right: 0;
  }
  .para-translation {
    border-left: none;
    padding-left: 0;
    margin-top: 10px;
  }
}
</style>
