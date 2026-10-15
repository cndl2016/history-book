<template>
  <div class="welcome-page">
    <el-card class="welcome-card" shadow="never">
      <h1 class="title">欢迎使用 {{ title }}</h1>
      <p class="subtitle">History Book —— 记录历史，启迪未来</p>
      <el-divider />
      <p class="tip">当前版本 v{{ version }}，左侧菜单进入系统管理功能。</p>
    </el-card>
  </div>
</template>

<script setup name="Index">
import defaultSettings from '@/settings'

const title = defaultSettings.title
const version = ref('1.0.0')
</script>

<style scoped lang="scss">
.welcome-page {
  margin: 16px;

  .welcome-card {
    padding: 40px;
    text-align: center;

    .title {
      font-size: 28px;
      color: #303133;
      margin-bottom: 8px;
    }

    .subtitle {
      font-size: 16px;
      color: #909399;
    }

    .tip {
      color: #606266;
    }
  }
}
</style>
