<!-- 读通鉴-标签关联段落对话框：点击段落标签弹出，分页浏览同标签段落并定位跳转 -->
<template>
  <el-dialog
    v-model="visible"
    width="860px"
    :close-on-click-modal="true"
    class="tag-dialog"
    append-to-body
  >
    <template #header>
      <div class="td-header">
        <span class="td-prefix">标签</span>
        <span class="td-title">{{ tag }}</span>
        <span class="td-total">共 {{ total }} 段</span>
      </div>
    </template>

    <div v-loading="loading" class="td-body">
      <div v-if="list.length > 0" class="td-list">
        <div v-for="item in list" :key="item.contentId" class="td-item" @click="locate(item)">
          <div class="td-crumb">
            <span class="td-volume">{{ item.volumeTitle }}</span>
            <span class="td-sep">/</span>
            <span>{{ item.reignTitle }}</span>
            <span class="td-sep">/</span>
            <span class="td-number">第 {{ item.number }} 段</span>
            <span class="td-locate">定位 ›</span>
          </div>
          <div class="td-text">
            <template v-for="(seg, i) in splitByTag(item.original || item.simplified || '')" :key="i">
              <mark v-if="seg.hit" class="td-mark">{{ seg.text }}</mark>
              <span v-else>{{ seg.text }}</span>
            </template>
          </div>
          <div v-if="item.tags" class="td-tags" @click.stop>
            <span
              v-for="t in item.tags.split(',')"
              :key="t"
              class="td-tag"
              :class="{ active: t === tag }"
              @click="switchTag(t)"
            >{{ t }}</span>
          </div>
        </div>
      </div>
      <el-empty v-else-if="!loading" description="没有找到该标签的关联段落" :image-size="80" />
    </div>

    <template #footer>
      <div class="td-footer">
        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          layout="prev, pager, next"
          @pagination="getList"
        />
        <el-button text bg round size="small" @click="visible = false">关 闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { listContentByTag } from '@/api/tongjian'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  tag: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'update:tag', 'locate'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const loading = ref(false)
const list = ref([])
const total = ref(0)
const queryParams = ref({ pageNum: 1, pageSize: 10 })

watch(() => [props.modelValue, props.tag], ([v]) => {
  if (v && props.tag) {
    queryParams.value.pageNum = 1
    getList()
  }
})

function getList() {
  loading.value = true
  listContentByTag({ tag: props.tag, ...queryParams.value }).then(res => {
    list.value = res.rows || []
    total.value = res.total || 0
    loading.value = false
  }).catch(() => { loading.value = false })
}

/** 切换标签（点条目里的其他标签） */
function switchTag(t) {
  if (t && t !== props.tag) {
    emit('update:tag', t)
  }
}

/** 定位到段落：通知父组件跳转并关闭 */
function locate(item) {
  emit('locate', item)
  visible.value = false
}

/** 把正文按当前标签拆分，用于高亮 */
function splitByTag(text) {
  if (!text) return []
  if (!props.tag || !text.includes(props.tag)) return [{ text, hit: false }]
  const segs = []
  const parts = text.split(props.tag)
  parts.forEach((p, i) => {
    if (p) segs.push({ text: p, hit: false })
    if (i < parts.length - 1) segs.push({ text: props.tag, hit: true })
  })
  return segs
}
</script>

<style scoped>
/* 对话框内 Element Plus 主色换成琥珀色（分页、按钮统一古典暖调） */
.tag-dialog {
  --el-color-primary: #b45309;
  --el-color-primary-light-3: #c77a3a;
  --el-color-primary-light-5: #d99c64;
  --el-color-primary-light-7: #e8bd92;
  --el-color-primary-light-8: #efcfb0;
  --el-color-primary-light-9: #f5e8da;
  --el-color-primary-dark-2: #92400e;
}

/* 对话框整体：纸面底、圆角、暖色阴影（el-dialog 本体不在模板内，用 :deep 穿透） */
.tag-dialog :deep(.el-dialog) {
  background: #f6f4ef;
  border: 1px solid #e9e4da;
  border-radius: 10px;
  box-shadow: 0 12px 48px rgba(80, 66, 45, .25);
  padding: 0;
  overflow: hidden;
  margin: 12vh auto 50px;
}
.tag-dialog :deep(.el-dialog__header) {
  padding: 18px 24px 14px;
  margin-right: 0;
  border-bottom: 1px solid #ece5d6;
  background: #fbf9f4;
}
.tag-dialog :deep(.el-dialog__body) {
  padding: 16px 24px;
}
.tag-dialog :deep(.el-dialog__footer) {
  padding: 12px 24px;
  border-top: 1px solid #ece5d6;
  background: #fbf9f4;
}
.tag-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: #a8a29e;
}
.tag-dialog :deep(.el-dialog__headerbtn:hover .el-dialog__close) {
  color: #b45309;
}

/* 头部：「标签」小印 + 宋体大名 + 数量胶囊（右侧给关闭按钮留位） */
.td-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-right: 32px;
}
.td-prefix {
  font-size: 12px;
  color: #a8a29e;
  border: 1px solid #e0d8c8;
  border-radius: 3px;
  padding: 2px 6px;
  letter-spacing: 2px;
}
.td-title {
  font-family: 'Songti SC', 'SimSun', serif;
  font-size: 22px;
  font-weight: 600;
  color: #b45309;
  letter-spacing: 2px;
}
.td-total {
  margin-left: auto;
  font-size: 12.5px;
  color: #b45309;
  border: 1px solid #e3cfa8;
  background: rgba(180, 83, 9, .06);
  border-radius: 999px;
  padding: 3px 12px;
  letter-spacing: 1px;
}

/* 列表 */
.td-body {
  min-height: 200px;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}
.td-body::-webkit-scrollbar {
  width: 6px;
}
.td-body::-webkit-scrollbar-thumb {
  background: #ddd5c5;
  border-radius: 3px;
}
.td-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 段落卡片：纸面卡 + 左侧琥珀色批示条 */
.td-item {
  position: relative;
  border: 1px solid #e9e4da;
  border-radius: 6px;
  background: #fdfcfa;
  padding: 14px 18px 12px 22px;
  cursor: pointer;
  transition: border-color .15s ease, box-shadow .15s ease, transform .15s ease;
}
.td-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 2px;
  background: transparent;
  transition: background .15s ease;
}
.td-item:hover {
  border-color: #d6c9b4;
  box-shadow: 0 4px 16px rgba(120, 100, 70, .1);
  transform: translateY(-1px);
}
.td-item:hover::before {
  background: #b45309;
}
.td-item:hover .td-locate {
  color: #b45309;
}

/* 面包屑 */
.td-crumb {
  font-size: 12px;
  color: #78716c;
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: 'Songti SC', 'SimSun', serif;
}
.td-volume {
  color: #b45309;
  letter-spacing: 1px;
}
.td-sep {
  color: #d9d0bf;
}
.td-number {
  color: #44403c;
  background: #f1ede5;
  border-radius: 3px;
  padding: 0 6px;
}
.td-locate {
  margin-left: auto;
  font-size: 12px;
  color: #c7bfb2;
  font-family: inherit;
  transition: color .15s;
}

/* 正文与高亮 */
.td-text {
  margin-top: 8px;
  font-family: 'Songti SC', 'SimSun', serif;
  font-size: 14.5px;
  line-height: 2;
  color: #292524;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.td-mark {
  background: rgba(180, 83, 9, .16);
  color: #92400e;
  padding: 0 2px;
  border-radius: 2px;
  border-bottom: 1px solid rgba(180, 83, 9, .4);
}

/* 标签胶囊 */
.td-tags {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.td-tag {
  font-size: 12px;
  color: #78716c;
  border: 1px solid #e9e4da;
  border-radius: 999px;
  padding: 1px 10px;
  cursor: pointer;
  background: #fff;
  transition: all .15s;
}
.td-tag:hover {
  color: #b45309;
  border-color: #d6c9b4;
}
.td-tag.active {
  color: #fff;
  border-color: #b45309;
  background: #b45309;
}

/* 底部：分页真正居中，关闭按钮靠右（需覆盖全局 .pagination-container 的绝对定位右对齐） */
.td-footer {
  display: flex;
  align-items: center;
}
.td-footer :deep(.pagination-container) {
  position: static;
  height: auto;
  margin-top: 0;
  padding: 0;
  flex: 1;
  display: flex;
  justify-content: center;
}
.td-footer :deep(.pagination-container .el-pagination) {
  position: static;
  right: auto;
}
.td-footer :deep(.el-pager li) {
  font-family: 'Songti SC', 'SimSun', serif;
  border-radius: 4px;
  color: #78716c;
}
.td-footer :deep(.el-pager li:hover) {
  color: #b45309;
}
.td-footer :deep(.el-pager li.is-active) {
  background: #b45309;
  color: #fff;
  font-weight: 600;
}
.td-footer :deep(.btn-prev),
.td-footer :deep(.btn-next) {
  color: #78716c;
}
.td-footer :deep(.btn-prev:hover),
.td-footer :deep(.btn-next:hover) {
  color: #b45309;
}
</style>
