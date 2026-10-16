@echo off
rem ============================================================
rem  history-book 本地 H2 数据库服务器（H2 2.2.224）
rem  启动后：
rem    - TCP 端口 9092 ：后端通过 jdbc:h2:tcp://localhost:9092/./history-book 连接
rem    - Web 控制台 8082：http://localhost:8082 浏览器直接管理数据库
rem  数据文件位于本目录（history-book.mv.db），关闭窗口即停止服务
rem ============================================================
cd /d %~dp0
java -cp h2-2.2.224.jar org.h2.tools.Server -tcp -tcpPort 9092 -web -webPort 8082 -baseDir . -ifNotExists
