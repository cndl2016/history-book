<!-- 读通鉴-沙盘态势：段落地名地图（Leaflet + CHGIS 点位）+ 地点介绍 + 关联文档 -->
<template>
  <teleport to="body">
    <div v-if="visible" class="sandbox-viewer">
      <!-- 顶部控制条 -->
      <div class="sv-top">
        <div class="sv-title">
          <span class="sv-brand">沙盘态势</span>
          <span v-if="reignTitle" class="sv-reign">{{ reignTitle }}</span>
          <span v-if="year != null" class="sv-year">{{ formatYear(year) }}</span>
        </div>
        <div class="sv-actions">
          <button type="button" class="sv-btn" title="缩放至全部点位" @click="fitAll"><el-icon><Aim /></el-icon></button>
          <button type="button" class="sv-btn" title="全屏" @click="toggleFullscreen"><el-icon><FullScreen /></el-icon></button>
          <button type="button" class="sv-btn sv-close" title="关闭" @click="close"><el-icon><Close /></el-icon></button>
        </div>
      </div>

      <!-- 地点介绍 -->
      <div v-if="selected" class="sv-intro">
        <span class="sv-intro-name">{{ selected.name }}<span class="sv-intro-type">（{{ selected.typeCh || '地名' }}）</span></span>
        <span class="sv-intro-desc">{{ introText }}</span>
      </div>

      <!-- 主区：左侧关联文档 + 右侧地图 -->
      <div class="sv-stage">
        <!-- 关联文档 -->
        <div class="sv-docs" :class="{ collapsed: docsCollapsed }">
          <div class="sv-docs-head" @click="docsCollapsed = !docsCollapsed">
            <span>关联文档<template v-if="selected"> · {{ selected.tag }}</template></span>
            <span class="sv-docs-toggle">{{ docsCollapsed ? '展开' : '收起' }}</span>
          </div>
          <template v-if="!docsCollapsed">
            <el-scrollbar class="sv-docs-body" v-loading="relatedLoading">
              <div v-if="relatedList.length > 0">
                <div
                  v-for="item in relatedList"
                  :key="item.contentId"
                  class="sv-doc"
                  @click="locateRelated(item)"
                >
                  <div class="sv-doc-crumb">
                    <span class="sv-doc-volume">{{ item.volumeTitle }}</span>
                    <span class="sv-doc-sep">/</span>
                    <span>{{ item.reignTitle }}</span>
                    <span class="sv-doc-sep">/</span>
                    <span class="sv-doc-number">第 {{ item.number }} 段</span>
                    <span class="sv-doc-locate">定位 ›</span>
                  </div>
                  <div class="sv-doc-text">
                    <template v-for="(seg, i) in splitByTag(item.original || item.simplified || '')" :key="i">
                      <mark v-if="seg.hit" class="sv-doc-mark">{{ seg.text }}</mark>
                      <span v-else>{{ seg.text }}</span>
                    </template>
                  </div>
                </div>
              </div>
              <div v-else-if="!relatedLoading" class="sv-docs-empty">没有找到关联段落</div>
            </el-scrollbar>
            <div class="sv-docs-foot">
              <pagination
                v-show="relatedTotal > 0"
                :total="relatedTotal"
                v-model:page="relatedQuery.pageNum"
                v-model:limit="relatedQuery.pageSize"
                layout="prev, pager, next"
                @pagination="loadRelated"
              />
            </div>
          </template>
        </div>

        <!-- 地图 -->
        <div class="sv-map-wrap">
          <div ref="mapRef" class="sv-map"></div>
          <div v-if="loading" class="sv-status">点位加载中…</div>
          <div v-else-if="errorMsg" class="sv-status">{{ errorMsg }}</div>
          <!-- 点位清单 -->
          <div v-if="!loading && places.length > 0" class="sv-list" :class="{ collapsed: listCollapsed }">
            <div class="sv-list-head" @click="listCollapsed = !listCollapsed">
              <span>地名 {{ places.length }} 处</span>
              <span class="sv-list-toggle">{{ listCollapsed ? '展开' : '收起' }}</span>
            </div>
            <el-scrollbar v-show="!listCollapsed" class="sv-list-body">
              <div
                v-for="(p, i) in places"
                :key="i"
                class="sv-place"
                :class="{ active: selected === p }"
                @click="selectPlace(p)"
              >
                <span class="sv-place-name">{{ p.name }}</span>
                <span class="sv-place-meta">{{ p.typeCh }}<template v-if="p.presLoc"> · {{ p.presLoc }}</template></span>
              </div>
            </el-scrollbar>
          </div>
        </div>
      </div>

      <!-- 底部说明 -->
      <div class="sv-bottom">
        <span v-if="tileNote" class="sv-note sv-note-warn">{{ tileNote }} · </span>
        <span class="sv-note">地名坐标：CHGIS V6（哈佛大学/复旦大学，学术使用）· 按纪年公元年份匹配始置/废置年代</span>
      </div>
    </div>
  </teleport>
</template>

<script setup>
import { Aim, FullScreen, Close } from '@element-plus/icons-vue'
import { getParagraphSandbox, listContentByTag } from '@/api/tongjian'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const props = defineProps({
  visible: { type: Boolean, default: false },
  contentId: { type: [String, Number], default: null }
})
const emit = defineEmits(['update:visible', 'locate'])

const mapRef = ref(null)
const loading = ref(false)
const errorMsg = ref('')
const places = ref([])
const year = ref(null)
const reignTitle = ref('')
const listCollapsed = ref(false)

// 当前选中地名
const selected = ref(null)

// 关联文档
const docsCollapsed = ref(false)
const relatedLoading = ref(false)
const relatedList = ref([])
const relatedTotal = ref(0)
const relatedQuery = ref({ pageNum: 1, pageSize: 10 })

let map = null
let markerLayer = null
let tileLayer = null
let activeProvider = null
let tileErrorCount = 0
let tileLoadCount = 0

/** 底图源（按优先级）：高德国内可达性好；OSM 备用。gcj02 标记需要做火星坐标转换 */
const TILE_PROVIDERS = [
  {
    name: '高德地图',
    url: 'https://wprd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    subdomains: '1234',
    gcj02: true,
    attribution: '&copy; 高德地图'
  },
  {
    name: 'OpenStreetMap',
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    subdomains: 'abc',
    gcj02: false,
    attribution: '&copy; OpenStreetMap'
  }
]

const tileNote = ref('')

/** 地点介绍文案：由字典字段组合（今址 + 年代 + 关联段数） */
const introText = computed(() => {
  const p = selected.value
  if (!p) return ''
  const parts = []
  if (p.presLoc) {
    parts.push(p.presLoc.endsWith('。') ? p.presLoc : p.presLoc + '。')
  }
  if (p.begYr != null || p.endYr != null) {
    const beg = p.begYr != null ? formatYear(p.begYr) : '不详'
    const end = p.endYr != null ? formatYear(p.endYr) : '不详'
    parts.push(`年代：${beg} 至 ${end}。`)
  }
  parts.push(`关联段落 ${relatedTotal.value} 段。`)
  return parts.join(' ')
})

/** -403 -> 公元前403年；960 -> 公元960年 */
function formatYear(y) {
  return y < 0 ? `公元前${-y}年` : `公元${y}年`
}

watch(() => props.visible, val => {
  if (val) openViewer()
})

function close() {
  emit('update:visible', false)
}

function destroyMap() {
  if (map) {
    map.remove()
    map = null
    markerLayer = null
  }
}

async function openViewer() {
  loading.value = true
  errorMsg.value = ''
  places.value = []
  year.value = null
  reignTitle.value = ''
  listCollapsed.value = false
  docsCollapsed.value = false
  selected.value = null
  relatedList.value = []
  relatedTotal.value = 0
  try {
    const res = await getParagraphSandbox(props.contentId)
    const data = res.data || {}
    places.value = data.places || []
    year.value = data.year
    reignTitle.value = data.reignTitle || ''
    if (places.value.length === 0) {
      const msg = res.msg
      errorMsg.value = (msg && msg !== 'success' && msg !== '操作成功') ? msg : '该段落未匹配到可定位的历史地名'
      loading.value = false
      return
    }
    loading.value = false
    await nextTick()
    // 先探测哪个底图源可达，再渲染地图
    const providerIdx = await probeProvider()
    renderMap(providerIdx)
    // 默认选中第一个地名：顶部介绍 + 关联文档
    selectPlace(places.value[0], { fly: false })
  } catch (e) {
    loading.value = false
    errorMsg.value = '沙盘数据加载失败'
  }
}

/** 选中地名：更新介绍、关联文档，可选飞到点位 */
function selectPlace(p, opts = {}) {
  const { fly = true } = opts
  if (!p) return
  selected.value = p
  loadRelated()
  if (fly) {
    focusPlace(p)
  }
}

/** 加载选中地名的关联段落 */
function loadRelated() {
  const p = selected.value
  if (!p) return
  relatedLoading.value = true
  listContentByTag({ tag: p.tag, ...relatedQuery.value }).then(res => {
    relatedList.value = res.rows || []
    relatedTotal.value = res.total || 0
    relatedLoading.value = false
  }).catch(() => { relatedLoading.value = false })
}

/** 定位到关联段落：通知父组件跳转并关闭沙盘 */
function locateRelated(item) {
  emit('locate', item)
}

/** 把正文按当前选中地名的标签拆分，用于高亮 */
function splitByTag(text) {
  const tag = selected.value ? selected.value.tag : ''
  if (!text) return []
  if (!tag || !text.includes(tag)) return [{ text, hit: false }]
  const segs = []
  const parts = text.split(tag)
  parts.forEach((p, i) => {
    if (p) segs.push({ text: p, hit: false })
    if (i < parts.length - 1) segs.push({ text: tag, hit: true })
  })
  return segs
}

function renderMap(providerIdx) {
  destroyMap()
  map = L.map(mapRef.value, {
    center: [35, 110],
    zoom: 5,
    zoomControl: true,
    attributionControl: true
  })
  activeProvider = providerIdx
  tileErrorCount = 0
  tileLoadCount = 0
  addTileLayer(providerIdx)

  markerLayer = L.layerGroup().addTo(map)
  places.value.forEach(p => {
    const icon = L.divIcon({
      className: 'sv-marker',
      html: '<span class="sv-dot"></span>',
      iconSize: [14, 14],
      iconAnchor: [7, 7]
    })
    const marker = L.marker(displayCoord(p), { icon })
    marker.on('click', () => selectPlace(p, { fly: false }))
    const years = (p.begYr != null || p.endYr != null)
      ? `<div class="sv-pop-row">年代：${p.begYr != null ? formatYear(p.begYr) : '？'} — ${p.endYr != null ? formatYear(p.endYr) : '？'}</div>`
      : ''
    marker.bindPopup(
      `<div class="sv-pop"><div class="sv-pop-name">${p.name}</div>` +
      `<div class="sv-pop-row">类型：${p.typeCh || '—'}（标签：${p.tag}）</div>` +
      (p.presLoc ? `<div class="sv-pop-row">今址：${p.presLoc}</div>` : '') +
      years + '</div>'
    )
    marker.addTo(markerLayer)
    p._marker = marker
  })
  fitAll()
}

/** 点位显示坐标：底图为火星坐标系（高德）时把 WGS84 转 GCJ-02 */
function displayCoord(p) {
  if (activeProvider != null && TILE_PROVIDERS[activeProvider].gcj02) {
    const [lng, lat] = wgs84ToGcj02(p.x, p.y)
    return [lat, lng]
  }
  return [p.y, p.x]
}

function addTileLayer(idx) {
  if (idx == null || idx >= TILE_PROVIDERS.length) {
    tileNote.value = '在线底图加载失败（网络不可达），仅显示地名点位'
    return
  }
  const p = TILE_PROVIDERS[idx]
  tileNote.value = ''
  tileLayer = L.tileLayer(p.url, {
    maxZoom: 18,
    subdomains: p.subdomains,
    attribution: p.attribution
  })
  tileLayer.on('tileload', () => { tileLoadCount++ })
  tileLayer.on('tileerror', () => {
    tileErrorCount++
    // 连续失败且一张都没加载成功 → 换下一个底图源
    if (tileErrorCount >= 6 && tileLoadCount === 0) {
      switchProvider(idx + 1)
    }
  })
  tileLayer.addTo(map)
}

function switchProvider(nextIdx) {
  if (!map) return
  if (tileLayer) {
    map.removeLayer(tileLayer)
    tileLayer = null
  }
  activeProvider = nextIdx < TILE_PROVIDERS.length ? nextIdx : null
  tileErrorCount = 0
  tileLoadCount = 0
  if (activeProvider != null) {
    addTileLayer(activeProvider)
    // 坐标系变了，重摆所有点位
    places.value.forEach(p => {
      if (p._marker) p._marker.setLatLng(displayCoord(p))
    })
    fitAll()
  } else {
    tileNote.value = '在线底图加载失败（网络不可达），仅显示地名点位'
  }
}

/** 探测底图源是否可达（3 秒超时），返回第一个可用的序号 */
function probeProvider() {
  return new Promise(resolve => {
    let settled = false
    let pending = TILE_PROVIDERS.length
    TILE_PROVIDERS.forEach((p, idx) => {
      const url = p.url
        .replace('{s}', p.subdomains[0])
        .replace('{z}', '6').replace('{x}', '50').replace('{y}', '26')
      const img = new Image()
      const timer = setTimeout(() => {
        img.src = ''
        if (!settled && --pending === 0) { settled = true; resolve(null) }
      }, 3000)
      img.onload = () => {
        clearTimeout(timer)
        if (!settled) { settled = true; resolve(idx) }
      }
      img.onerror = () => {
        clearTimeout(timer)
        if (!settled && --pending === 0) { settled = true; resolve(null) }
      }
      img.src = url
    })
  })
}

function fitAll() {
  if (!map || places.value.length === 0) return
  const bounds = L.latLngBounds(places.value.map(p => displayCoord(p)))
  map.fitBounds(bounds.pad(0.25), { maxZoom: 10 })
}

function focusPlace(p) {
  if (!map || !p) return
  map.flyTo(displayCoord(p), Math.max(map.getZoom(), 8), { duration: 0.6 })
  p._marker && p._marker.openPopup()
}

function toggleFullscreen() {
  const el = document.querySelector('.sandbox-viewer')
  if (!document.fullscreenElement) {
    el && el.requestFullscreen && el.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

/* ===== WGS84 -> GCJ-02（火星坐标系）转换 ===== */
const GCJ_A = 6378245.0
const GCJ_EE = 0.00669342162296594323

function transformLat(x, y) {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
  ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
  return ret
}

function transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
  ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
  return ret
}

function wgs84ToGcj02(lng, lat) {
  if (lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271) return [lng, lat]
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = lat / 180.0 * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - GCJ_EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI)
  dLng = (dLng * 180.0) / (GCJ_A / sqrtMagic * Math.cos(radLat) * Math.PI)
  return [lng + dLng, lat + dLat]
}

onBeforeUnmount(destroyMap)
</script>

<style scoped>
.sandbox-viewer {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(20, 18, 14, .55);
  display: flex;
  flex-direction: column;
}

.sv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 48px;
  padding: 0 16px;
  background: #fdfcfa;
  border-bottom: 1px solid #e9e4da;
}
.sv-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}
.sv-brand {
  font-size: 16px;
  font-weight: 600;
  font-family: 'Songti SC', 'SimSun', serif;
  letter-spacing: 3px;
  color: #292524;
}
.sv-reign {
  font-size: 13px;
  color: #78716c;
}
.sv-year {
  font-size: 13px;
  color: #b45309;
}
.sv-actions {
  display: flex;
  gap: 8px;
}
.sv-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid #e9e4da;
  border-radius: 4px;
  background: #fff;
  color: #57534e;
  cursor: pointer;
}
.sv-btn:hover {
  color: #b45309;
  border-color: #d97706;
}
.sv-close:hover {
  background: #b45309;
  color: #fff;
}

/* 地点介绍条 */
.sv-intro {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 8px 16px;
  background: #fbf6ec;
  border-bottom: 1px solid #eadfc8;
  overflow: hidden;
  white-space: nowrap;
}
.sv-intro-name {
  font-size: 15px;
  font-weight: 600;
  font-family: 'Songti SC', 'SimSun', serif;
  color: #92400e;
  flex-shrink: 0;
}
.sv-intro-type {
  font-size: 12px;
  font-weight: 400;
  color: #b45309;
}
.sv-intro-desc {
  font-size: 12.5px;
  color: #78716c;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 主区 */
.sv-stage {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 左侧关联文档 */
.sv-docs {
  width: 330px;
  flex-shrink: 0;
  background: #f6f4ef;
  border-right: 1px solid #e9e4da;
  display: flex;
  flex-direction: column;
}
.sv-docs.collapsed {
  width: auto;
}
.sv-docs-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 9px 14px;
  font-size: 13px;
  font-weight: 600;
  color: #44403c;
  font-family: 'Songti SC', 'SimSun', serif;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid #ece5d6;
  background: #fbf9f4;
  white-space: nowrap;
}
.sv-docs-toggle {
  font-size: 12px;
  font-weight: 400;
  color: #a8a29e;
}
.sv-docs-body {
  flex: 1;
  padding: 10px;
}
.sv-docs-empty {
  padding: 30px 0;
  text-align: center;
  font-size: 13px;
  color: #a8a29e;
}
.sv-doc {
  border: 1px solid #e9e4da;
  border-radius: 6px;
  background: #fdfcfa;
  padding: 10px 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: border-color .15s ease, box-shadow .15s ease;
}
.sv-doc:hover {
  border-color: #d6c9b4;
  box-shadow: 0 4px 16px rgba(120, 100, 70, .1);
}
.sv-doc:hover .sv-doc-locate {
  color: #b45309;
}
.sv-doc-crumb {
  font-size: 12px;
  color: #78716c;
  display: flex;
  align-items: center;
  gap: 5px;
  font-family: 'Songti SC', 'SimSun', serif;
  flex-wrap: wrap;
}
.sv-doc-volume {
  color: #b45309;
}
.sv-doc-sep {
  color: #d9d0bf;
}
.sv-doc-number {
  color: #44403c;
  background: #f1ede5;
  border-radius: 3px;
  padding: 0 6px;
}
.sv-doc-locate {
  margin-left: auto;
  font-size: 12px;
  color: #c7bfb2;
  transition: color .15s;
}
.sv-doc-text {
  margin-top: 6px;
  font-family: 'Songti SC', 'SimSun', serif;
  font-size: 13.5px;
  line-height: 1.9;
  color: #292524;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sv-doc-mark {
  background: rgba(180, 83, 9, .16);
  color: #92400e;
  padding: 0 2px;
  border-radius: 2px;
  border-bottom: 1px solid rgba(180, 83, 9, .4);
}
.sv-docs-foot {
  padding: 6px 10px;
  border-top: 1px solid #ece5d6;
  background: #fbf9f4;
}
.sv-docs-foot :deep(.pagination-container) {
  position: static;
  height: auto;
  margin-top: 0;
  padding: 0;
  justify-content: center;
}

/* 地图 */
.sv-map-wrap {
  position: relative;
  flex: 1;
  min-width: 0;
}
.sv-map {
  position: absolute;
  inset: 0;
}
.sv-status {
  position: absolute;
  inset: 0;
  z-index: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(246, 244, 239, .9);
  color: #78716c;
  font-size: 14px;
  pointer-events: none;
}

/* 点位清单 */
.sv-list {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 500;
  width: 260px;
  max-height: 60%;
  background: rgba(253, 252, 250, .95);
  border: 1px solid #e9e4da;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sv-list.collapsed {
  max-height: none;
}
.sv-list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #44403c;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid #efe9df;
}
.sv-list.collapsed .sv-list-head {
  border-bottom: none;
}
.sv-list-toggle {
  font-size: 12px;
  font-weight: 400;
  color: #a8a29e;
}
.sv-list-body {
  max-height: calc(60vh - 60px);
}
.sv-place {
  padding: 7px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f3efe6;
}
.sv-place:hover {
  background: #f6f1e7;
}
.sv-place.active {
  background: rgba(180, 83, 9, .08);
  border-left: 3px solid #b45309;
  padding-left: 9px;
}
.sv-place-name {
  display: block;
  font-size: 13.5px;
  color: #292524;
  font-family: 'Songti SC', 'SimSun', serif;
}
.sv-place-meta {
  display: block;
  font-size: 12px;
  color: #a8a29e;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sv-bottom {
  padding: 6px 16px;
  background: #fdfcfa;
  border-top: 1px solid #e9e4da;
}
.sv-note {
  font-size: 12px;
  color: #b8b0a4;
}
.sv-note-warn {
  color: #d97706;
}
</style>

<style>
/* Leaflet divIcon 标记点（非 scoped，Leaflet 动态创建 DOM） */
.sv-marker {
  background: transparent;
  border: none;
}
.sv-dot {
  display: block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #b45309;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px rgba(180, 83, 9, .35), 0 1px 4px rgba(0, 0, 0, .4);
}
.sv-pop-name {
  font-size: 15px;
  font-weight: 600;
  font-family: 'Songti SC', 'SimSun', serif;
  margin-bottom: 4px;
}
.sv-pop-row {
  font-size: 12.5px;
  color: #57534e;
  line-height: 1.7;
}
</style>
