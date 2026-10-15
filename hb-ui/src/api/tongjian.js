// 读通鉴模块 API
import request from '@/utils/request'

// 目录树：卷 -> 纪年
export function getCatalogTree() {
  return request({
    url: '/tongjian/catalog/tree',
    method: 'get'
  })
}

// 纪年详情
export function getReign(reignId) {
  return request({
    url: '/tongjian/catalog/reign/' + reignId,
    method: 'get'
  })
}

// 某纪年下的全部正文段落
export function listByReign(reignId) {
  return request({
    url: '/tongjian/content/listByReign/' + reignId,
    method: 'get'
  })
}

// 段落详情
export function getContent(contentId) {
  return request({
    url: '/tongjian/content/' + contentId,
    method: 'get'
  })
}

// 全文搜索
export function searchContent(query) {
  return request({
    url: '/tongjian/content/search',
    method: 'get',
    params: query
  })
}

// 我的收藏列表
export function listFavorite(query) {
  return request({
    url: '/tongjian/favorite/list',
    method: 'get',
    params: query
  })
}

// 收藏段落
export function addFavorite(contentId) {
  return request({
    url: '/tongjian/favorite/' + contentId,
    method: 'post'
  })
}

// 取消收藏
export function delFavorite(contentId) {
  return request({
    url: '/tongjian/favorite/' + contentId,
    method: 'delete'
  })
}

// 我的阅读记录
export function listHistory(query) {
  return request({
    url: '/tongjian/history/list',
    method: 'get',
    params: query
  })
}

// 记录阅读进度
export function recordHistory(reignId, lastNumber) {
  return request({
    url: '/tongjian/history/record/' + reignId + '/' + lastNumber,
    method: 'post'
  })
}

// 删除阅读记录
export function delHistory(historyIds) {
  return request({
    url: '/tongjian/history/' + historyIds,
    method: 'delete'
  })
}

// 同步状态
export function getSyncStatus() {
  return request({
    url: '/tongjian/sync/status',
    method: 'get'
  })
}

// 启动同步
export function startSync() {
  return request({
    url: '/tongjian/sync/start',
    method: 'post'
  })
}

// 停止同步
export function stopSync() {
  return request({
    url: '/tongjian/sync/stop',
    method: 'post'
  })
}

// 古本查看（anchor 定位 / page 翻页）
export function viewGuben(data) {
  return request({
    url: '/tongjian/guben/view',
    method: 'post',
    data
  })
}

// 古本回填状态
export function getGubenCrawlStatus() {
  return request({
    url: '/tongjian/guben/crawl/status',
    method: 'get'
  })
}

// 启动古本回填
export function startGubenCrawl() {
  return request({
    url: '/tongjian/guben/crawl/start',
    method: 'post'
  })
}

// 停止古本回填
export function stopGubenCrawl() {
  return request({
    url: '/tongjian/guben/crawl/stop',
    method: 'post'
  })
}
