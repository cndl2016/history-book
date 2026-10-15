/**
 * TDB实例状态枚举
 */
export const INSTANCE_STATUS = {
  UNENABLED: '0',
  DEPLOYING: '1',
  HEALTHY: '2',
  ABNORMAL: '3',
  STOPPED: '4'
}

export const INSTANCE_STATUS_MAP = {
  '0': '未启用',
  '1': '部署中',
  '2': '健康',
  '3': '异常',
  '4': '已停止'
}

export const INSTANCE_STATUS_COLOR_MAP = {
  '0': 'info',
  '1': 'warning',
  '2': 'success',
  '3': 'danger',
  '4': 'info'
}

/**
 * TDB部署模式枚举
 */
export const DEPLOY_MODE = {
  SINGLE: '0',
  CLUSTER: '1'
}

export const DEPLOY_MODE_MAP = {
  '0': '单机',
  '1': '分布式'
}

/**
 * TDB任务状态枚举
 */
export const TASK_STATUS = {
  WAITING: '0',
  RUNNING: '1',
  SUCCESS: '2',
  FAILED: '3'
}

export const TASK_STATUS_MAP = {
  '0': '等待中',
  '1': '进行中',
  '2': '成功',
  '3': '失败'
}

export const TASK_STATUS_COLOR_MAP = {
  '0': 'info',
  '1': 'warning',
  '2': 'success',
  '3': 'danger'
}

/**
 * TDB节点状态枚举
 */
export const NODE_STATUS = {
  STOPPED: '0',
  RUNNING: '1',
  ABNORMAL: '2'
}

export const NODE_STATUS_MAP = {
  '0': '未启动',
  '1': '运行中',
  '2': '异常'
}

export const NODE_STATUS_COLOR_MAP = {
  '0': 'info',
  '1': 'success',
  '2': 'danger'
}

/**
 * TDB告警项枚举
 */
export const ALARM_ITEM = {
  CONNECTION_STATUS: 'connection_status',
  CPU: 'cpu',
  MEMORY: 'memory',
  ROLLBACK_RATE: 'rollback_rate',
  TOTAL_CONNECTIONS: 'total_connections',
  LOCK_WAIT_TIME: 'lock_wait_time',
  CACHE_HIT_RATE: 'cache_hit_rate'
}

export const ALARM_ITEM_MAP = {
  'connection_status': '连接状态',
  'cpu': 'CPU使用率',
  'memory': '内存使用率',
  'rollback_rate': '事务回滚率',
  'total_connections': '总连接数',
  'lock_wait_time': '锁等待时间',
  'cache_hit_rate': '缓存命中率'
}

/**
 * TDB告警级别枚举
 */
export const ALARM_LEVEL = {
  WARNING: 'warning',
  CRITICAL: 'critical',
  FATAL: 'fatal'
}

export const ALARM_LEVEL_MAP = {
  'warning': '警告',
  'critical': '严重',
  'fatal': '致命'
}

export const ALARM_LEVEL_COLOR_MAP = {
  'warning': 'warning',
  'critical': 'danger',
  'fatal': 'danger'
}