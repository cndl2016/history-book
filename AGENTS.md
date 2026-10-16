# AGENTS.md

本文件记录在本项目中工作必须遵守的规则。

## 测试结束后必须释放端口（血泪教训）

任何联调、验证、测试过程中启动的进程，**测试完成后必须立即关闭**，绝不留给用户。

- 数据库：`db/start-h2.bat` 启动的 H2 服务器，占用 **9092**（TCP）和 **8082**（Web 控制台）端口
- 后端：`java -jar hb-admin/target/hb-admin.jar`，占用 **1081** 端口
- 前端：`npm run dev`（hb-ui 目录，vite），占用 **80** 端口

每次交付前必须执行以下检查：

```bash
netstat -ano | grep -E ':(1081|9092|8082|80) ' | grep LISTENING
```

如有残留进程，用 `taskkill //PID <pid> //F` 关闭，并用 `tasklist //FI "PID eq <pid>"` 或
PowerShell `Get-CimInstance Win32_Process` 确认进程归属本项目后再杀，避免误杀用户自己的服务。

**禁止**在最终回复时仍挂着自己启动的 H2 服务器 / 后端 / 前端进程。用户会自行运行
`db/start-h2.bat` 并在 IDEA 中启动 `HistoryBookApplication`，端口被占会导致启动失败
（"Port 1081 was already in use"、"Port 9092 was already in use"）。

## 数据库连接注意

H2 运行在独立服务器模式（`db/start-h2.bat` 启动，数据文件 `db/history-book.mv.db`），
后端通过 `jdbc:h2:tcp://localhost:9092/./history-book` 连接。数据文件由 H2 服务器进程
持有，后端连的是 TCP 端口，所以**后端运行期间可以直接用脚本连库**（走同一个 TCP 端口，
如改 `SYS_CONFIG`），无需停后端；但要替换 / 删除 `db/` 下的数据文件前，必须先停掉
H2 服务器进程。

## 临时文件

验证用的临时目录（如 `.verify`、`.tj-tmp`）用完即删，不留在仓库里。
