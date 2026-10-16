# History Book 历史书管理系统

基于 dameng 项目框架迁移的前后端空模板，仅保留最基础的系统管理模块，数据库使用轻量级 H2（本地服务器模式）。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Spring Boot 2.6.15 + Java 17 + MyBatis / MyBatis-Plus + PageHelper + Spring Security(JWT) |
| 数据库 | H2 2.2.224（本地服务器模式，数据文件固定在 `db/history-book.mv.db`，持久保存） |
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

### 0. 先启动本地 H2 数据库（TCP 端口 9092 / Web 控制台 8082）

双击运行 `db/start-h2.bat`（Linux/Mac 用 `db/start-h2.sh`），窗口保持开着，数据库服务就在后台运行。
数据文件为 `db/history-book.mv.db`，由 H2 服务器统一管理；`db/h2-2.2.224.jar` 为官方 H2 发行包，已随项目提供，无需额外安装。

- 独立 Web 控制台：`http://localhost:8082`（JDBC URL：`jdbc:h2:tcp://localhost:9092/./history-book`，用户 `sa`，空密码）

### 后端（端口 1081）

```bash
# 构建
mvn clean install -DskipTests
# 运行
java -jar hb-admin/target/hb-admin.jar
```

后端通过 `jdbc:h2:tcp://localhost:9092/./history-book` 连接上一步启动的 H2 服务器。数据库为空时首次启动自动执行 `hb-admin/src/main/resources/sql/schema.sql`、`data.sql` 初始化（脚本幂等，重复启动不会重复写入），之后数据一直持久保存在 `db/` 下。

- 应用内 H2 控制台：`http://localhost:1081/h2-console`（JDBC URL 同上：`jdbc:h2:tcp://localhost:9092/./history-book`，用户 `sa`，空密码）

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

- **在线阅读**：卷 → 帝王 → 纪年三级目录树，繁体原文 / 简体正文 / 白话译文左右分栏并读，胡注、陈注等注释与人物/地点/官职标签展示
- **古本对照**：段落级「古本」按钮打开元刊本扫描页查看器，支持定位高亮、滚轮缩放、拖拽、翻页、全屏（数据来自 dutongjian.com `/api/guben/view`，需其账号令牌，在「参数设置」配置 `tongjian.guben.token`）
- **全文搜索**：按关键词检索原文、译文与标签，可按卷过滤，点击结果跳转到对应段落
- **我的收藏**：段落级收藏与取消收藏
- **阅读记录**：自动记录最近阅读的纪年与进度，支持继续阅读
- **数据同步**：从 dutongjian.com 公开只读接口限速导入正文（每 5 秒一个请求，尊重 429 退避），按纪年 checkpoint 断点续传，可随时停止/继续

内置数据：全量目录（294 卷 / 1405 纪年）由 `data.sql` 初始化，卷一（周纪一）全部 30 个纪年共 112 段正文直接随 `db/history-book.mv.db` 数据文件分发，开箱即可阅读；其余纪年在「数据同步」页面一键导入。

表结构：`TJ_VOLUME`（卷）、`TJ_REIGN`（纪年）、`TJ_CONTENT`（正文段落）、`TJ_FAVORITE`（收藏）、`TJ_HISTORY`（阅读记录）、`TJ_SYNC_STATE`（同步 checkpoint），首次启动自动建表并写入种子数据。

## 说明

- 设备管理、定时任务、TDB 运维等原项目模块已移除
- 域用户的"自动同步"依赖定时任务模块，当前版本不支持开启（手动同步可用）
