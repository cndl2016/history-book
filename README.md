# History Book 历史书管理系统

基于 dameng 项目框架迁移的前后端空模板，仅保留最基础的系统管理模块，数据库使用轻量级 H2（嵌入式，免安装）。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Spring Boot 2.6.15 + Java 17 + MyBatis / MyBatis-Plus + PageHelper + Spring Security(JWT) |
| 数据库 | H2 2.2.224（文件模式 `D:/project/history-book/db/history-book`（绝对路径），首次启动自动建表和初始化数据） |
| 前端 | Vue 3 + Vite 5 + Element Plus + Pinia |

## 项目结构

```
history-book/
├── hb-admin/        # Web 入口、Controller 聚合、配置文件
├── hb-system/       # 系统管理（用户/角色/菜单/部门/字典/参数/日志）
├── hb-common/       # 通用工具、常量、异常、安全
├── hb-ui/           # 前端（Vue 3 + Vite）
└── pom.xml          # 父 POM
```

## 启动方式

### 后端（端口 1081）

```bash
# 构建
mvn clean install -DskipTests
# 运行
java -jar hb-admin/target/hb-admin.jar
```

首次启动自动在项目根目录 `db/` 下创建 H2 数据库并执行 `hb-admin/src/main/resources/sql/schema.sql`、`data.sql` 初始化（脚本幂等，重复启动不会重复写入）。

- H2 控制台：`http://localhost:1081/h2-console`（JDBC URL：`jdbc:h2:file:D:/project/history-book/db/history-book`，用户 `sa`，空密码）

### 前端（端口 80）

```bash
cd hb-ui
npm install
npm run dev
```

访问 `http://localhost`，默认账号：`manager / 123456`。

## 已内置的系统管理功能

- 用户管理、角色管理、菜单管理、部门管理
- 字典管理、参数设置、域管理（LDAP）
- 操作日志、登录日志
- 登录认证（JWT）、验证码开关、按钮级权限 `v-hasPermi`

## 读通鉴模块

迁移自 [Dutongjian](https://github.com/kxxhhh/Dutongjian) 项目与 [dutongjian.com](https://www.dutongjian.com/) 的核心功能：

- **在线阅读**：卷 → 纪年 → 段落三级目录树，繁体原文 / 简体正文 / 白话译文并读，胡注、陈注等注释与人物/地点/官职标签展示
- **全文搜索**：按关键词检索原文、译文与标签，可按卷过滤，点击结果跳转到对应段落
- **我的收藏**：段落级收藏与取消收藏
- **阅读记录**：自动记录最近阅读的纪年与进度，支持继续阅读
- **数据同步**：从 dutongjian.com 公开只读接口限速导入正文（每 5 秒一个请求，尊重 429 退避），按纪年 checkpoint 断点续传，可随时停止/继续

内置种子数据：全量目录（294 卷 / 1405 纪年）+ 卷一（周纪一）全部 30 个纪年共 112 段正文，开箱即可阅读；其余纪年在「数据同步」页面一键导入。

表结构：`TJ_VOLUME`（卷）、`TJ_REIGN`（纪年）、`TJ_CONTENT`（正文段落）、`TJ_FAVORITE`（收藏）、`TJ_HISTORY`（阅读记录）、`TJ_SYNC_STATE`（同步 checkpoint），首次启动自动建表并写入种子数据。

## 说明

- 设备管理、定时任务、TDB 运维等原项目模块已移除
- 域用户的"自动同步"依赖定时任务模块，当前版本不支持开启（手动同步可用）
