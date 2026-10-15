<!-- 读通鉴-古本查看器：元刊本扫描页，支持定位高亮、缩放、拖拽、翻页、全屏（版式对齐 dutongjian.com） -->
<template>
  <teleport to="body">
    <div v-if="visible" class="guben-viewer" ref="viewerRef" @wheel.prevent="onWheel">
      <!-- 顶部控制条 -->
      <div class="gv-top">
        <div class="gv-controls">
          <button type="button" class="gv-btn" title="放大" @click="zoomBy(1.25)"><el-icon><ZoomIn /></el-icon></button>
          <button type="button" class="gv-btn" title="缩小" @click="zoomBy(0.8)"><el-icon><ZoomOut /></el-icon></button>
          <button type="button" class="gv-btn gv-btn-text" title="原始大小" @click="resetZoom">1:1</button>
          <button type="button" class="gv-btn" title="全屏" @click="toggleFullscreen"><el-icon><FullScreen /></el-icon></button>
        </div>
        <button type="button" class="gv-btn gv-close" title="关闭" @click="close"><el-icon><Close /></el-icon></button>
      </div>

      <!-- 图像区 -->
      <div class="gv-stage" @mousedown="onDragStart" @mousemove="onDragMove" @mouseup="onDragEnd" @mouseleave="onDragEnd">
        <div v-if="loading" class="gv-status">古本定位中…</div>
        <div v-else-if="errorMsg" class="gv-status-card">
          <div class="gv-error-text">{{ errorMsg }}</div>
          <div v-if="needToken" class="gv-error-tip">
            古本扫描页来自 dutongjian.com（元刊本）。已缓存到本地的段落可离线查看；<br>
            该段落尚未缓存，需管理员在「系统管理 → 参数设置」配置 <code>tongjian.guben.token</code>
            后启动回填（POST /tongjian/guben/crawl/start），回填完成后查看不再需要令牌。
          </div>
        </div>
        <div
          v-else-if="page.imageUrl"
          class="gv-canvas"
          :style="{ transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})` }"
          :class="{ dragging }"
        >
          <img :src="page.imageUrl" class="gv-image" @load="onImageLoad" draggable="false" alt="古本扫描页" />
          <div v-if="anchorStyle" class="gv-anchor" :style="anchorStyle"></div>
        </div>
      </div>

      <!-- 底部翻页（传统书版右起：左侧为下一页） -->
      <div class="gv-bottom" v-if="!loading && !errorMsg && page.imageUrl">
        <button type="button" class="gv-nav" :disabled="!page.hasNext || switching" @click="turnPage('next')">〈 下一页</button>
        <span class="gv-brand">读通鉴</span>
        <button type="button" class="gv-nav" :disabled="!page.hasPrev || switching" @click="turnPage('prev')">上一页 〉</button>
      </div>
    </div>
  </teleport>
</template>

<script setup>
import { ZoomIn, ZoomOut, FullScreen, Close } from '@element-plus/icons-vue'
import { viewGuben } from '@/api/tongjian'

const props = defineProps({
  visible: { type: Boolean, default: false },
  // 段落定位参数
  paragraph: { type: Object, default: null }
})
const emit = defineEmits(['update:visible'])

const viewerRef = ref(null)
const loading = ref(false)
const errorMsg = ref('')
const needToken = ref(false)
const switching = ref(false)
const page = ref({ imageName: '', imageUrl: '', anchorBox: null, hasPrev: false, hasNext: false })

const scale = ref(1)
const pan = ref({ x: 0, y: 0 })
const dragging = ref(false)
let dragStart = null
let naturalSize = { w: 0, h: 0 }

const baseApi = import.meta.env.VITE_APP_BASE_API

/** 本地缓存图片以 / 开头（/profile/guben/...），需拼上接口前缀；上游签名 URL 原样使用 */
function resolveImageUrl(data) {
  if (data && data.imageUrl && data.imageUrl.startsWith('/')) {
    data.imageUrl = baseApi + data.imageUrl
  }
  return data
}

/** 锚点高亮框（兼容像素坐标与 0-1 比例两种返回） */
const anchorStyle = computed(() => {
  const box = page.value.anchorBox
  if (!box || !naturalSize.w || !naturalSize.h) return null
  let { locationTop: t, locationLeft: l, locationWidth: w, locationHeight: h } = box
  if ([t, l, w, h].some(v => v == null)) return null
  // 判断是否为比例坐标
  if (Math.max(t, l, w, h) <= 1.0001) {
    return { top: (t * 100) + '%', left: (l * 100) + '%', width: (w * 100) + '%', height: (h * 100) + '%' }
  }
  return {
    top: (t / naturalSize.h * 100) + '%',
    left: (l / naturalSize.w * 100) + '%',
    width: (w / naturalSize.w * 100) + '%',
    height: (h / naturalSize.h * 100) + '%'
  }
})

watch(() => props.visible, val => {
  if (val) openViewer()
})

function openViewer() {
  resetZoom()
  errorMsg.value = ''
  needToken.value = false
  page.value = { imageName: '', imageUrl: '', anchorBox: null, hasPrev: false, hasNext: false }
  const p = props.paragraph
  if (!p || !p.tongjianId) {
    errorMsg.value = '该段落缺少古本定位参数'
    return
  }
  loading.value = true
  viewGuben({
    mode: 'anchor',
    paragraphTongjianId: p.tongjianId,
    startIndex: p.startIndex,
    selectedText: p.selectedText
  }).then(res => {
    loading.value = false
    if (res.code === 500 || !res.data) {
      errorMsg.value = res.msg || '加载古本失败'
      needToken.value = (res.msg || '').includes('tongjian.guben.token')
      return
    }
    page.value = resolveImageUrl(res.data)
  }).catch(err => {
    loading.value = false
    errorMsg.value = '加载古本失败：' + (err.message || '')
  })
}

function turnPage(direction) {
  if (!page.value.imageName) return
  switching.value = true
  viewGuben({ mode: 'page', imageName: page.value.imageName, direction }).then(res => {
    switching.value = false
    if (res.code === 500 || !res.data) return
    // 翻页后清掉旧锚点
    page.value = { ...resolveImageUrl(res.data), anchorBox: null }
    resetZoom()
  }).catch(() => { switching.value = false })
}

function onImageLoad(e) {
  naturalSize = { w: e.target.naturalWidth || 0, h: e.target.naturalHeight || 0 }
}

function zoomBy(factor) {
  scale.value = Math.min(6, Math.max(0.2, scale.value * factor))
}

function resetZoom() {
  scale.value = 1
  pan.value = { x: 0, y: 0 }
}

function onWheel(e) {
  if (!page.value.imageUrl) return
  zoomBy(e.deltaY > 0 ? 0.9 : 1.1)
}

function onDragStart(e) {
  if (!page.value.imageUrl) return
  dragging.value = true
  dragStart = { x: e.clientX - pan.value.x, y: e.clientY - pan.value.y }
}

function onDragMove(e) {
  if (!dragging.value || !dragStart) return
  pan.value = { x: e.clientX - dragStart.x, y: e.clientY - dragStart.y }
}

function onDragEnd() {
  dragging.value = false
  dragStart = null
}

function toggleFullscreen() {
  const el = viewerRef.value
  if (!el) return
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    el.requestFullscreen && el.requestFullscreen()
  }
}

function close() {
  emit('update:visible', false)
}

function onKeydown(e) {
  if (!props.visible) return
  if (e.key === 'Escape') close()
  if (e.key === 'ArrowLeft') turnPage('next')
  if (e.key === 'ArrowRight') turnPage('prev')
  if (e.key === '+' || e.key === '=') zoomBy(1.25)
  if (e.key === '-') zoomBy(0.8)
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.guben-viewer {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(12, 10, 9, .92);
  display: flex;
  flex-direction: column;
  user-select: none;
}
.gv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
}
.gv-controls {
  display: flex;
  gap: 8px;
}
.gv-btn {
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, .25);
  border-radius: 4px;
  background: transparent;
  color: #e7e5e4;
  font-size: 16px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.gv-btn:hover {
  background: rgba(255, 255, 255, .12);
}
.gv-btn-text {
  font-size: 12px;
  font-weight: 600;
}
.gv-stage {
  flex: 1;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
}
.gv-canvas {
  transform-origin: center center;
  transition: transform .08s linear;
}
.gv-canvas.dragging {
  transition: none;
}
.gv-image {
  display: block;
  max-width: 78vw;
  max-height: 78vh;
  box-shadow: 0 8px 40px rgba(0, 0, 0, .6);
}
.gv-anchor {
  position: absolute;
  border: 2px solid #f59e0b;
  background: rgba(245, 158, 11, .18);
  border-radius: 2px;
  pointer-events: none;
  animation: anchorFlash 1.6s ease-in-out 2;
}
@keyframes anchorFlash {
  50% { background: rgba(245, 158, 11, .38); }
}
.gv-status {
  color: #d6d3d1;
  font-size: 15px;
  letter-spacing: 4px;
}
.gv-status-card {
  max-width: 480px;
  background: rgba(255, 255, 255, .06);
  border: 1px solid rgba(255, 255, 255, .15);
  border-radius: 8px;
  padding: 24px 28px;
  color: #e7e5e4;
  font-size: 14px;
  line-height: 1.9;
}
.gv-error-tip {
  margin-top: 10px;
  font-size: 12.5px;
  color: #a8a29e;
}
.gv-error-tip code {
  color: #fbbf24;
}
.gv-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 22px;
}
.gv-nav {
  border: 1px solid rgba(255, 255, 255, .25);
  border-radius: 4px;
  background: transparent;
  color: #e7e5e4;
  font-size: 13px;
  padding: 6px 14px;
  cursor: pointer;
}
.gv-nav:hover:not(:disabled) {
  background: rgba(255, 255, 255, .12);
}
.gv-nav:disabled {
  opacity: .35;
  cursor: default;
}
.gv-brand {
  color: #b45309;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 6px;
  font-family: 'Songti SC', 'SimSun', serif;
}
</style>
