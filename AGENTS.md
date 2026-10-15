# AGENTS.md

本文件记录在本项目中工作必须遵守的规则。

## 测试结束后必须释放端口（血泪教训）

任何联调、验证、测试过程中启动的进程，**测试完成后必须立即关闭**，绝不留给用户。

- 后端：`java -jar hb-admin/target/hb-admin.jar`，占用 **1081** 端口
- 前端：`npm run dev`（hb-ui 目录，vite），占用 **80** 端口

每次交付前必须执行以下检查：

```bash
netstat -ano | grep -E ':(1081|80) ' | grep LISTENING
```

如有残留进程，用 `taskkill //PID <pid> //F` 关闭，并用 `tasklist //FI "PID eq <pid>"` 或
PowerShell `Get-CimInstance Win32_Process` 确认进程归属本项目后再杀，避免误杀用户自己的服务。

**禁止**在最终回复时仍挂着自己启动的后端 / 前端进程。用户会在 IDEA 中自行启动
`HistoryBookApplication`，端口被占会导致启动失败（"Port 1081 was already in use"）。

## 数据库连接注意

后端运行时会锁住 H2 数据库文件。需要用脚本直接连库（如修改 `SYS_CONFIG`）前，
必须先停掉后端进程，操作完成后再由用户或经确认后重启。

## 临时文件

验证用的临时目录（如 `.verify`、`.tj-tmp`）用完即删，不留在仓库里。
