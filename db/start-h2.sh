#!/usr/bin/env bash
# history-book 本地 H2 数据库服务器（H2 2.2.224）
#   - TCP 端口 9092 ：后端通过 jdbc:h2:tcp://localhost:9092/./history-book 连接
#   - Web 控制台 8082：http://localhost:8082 浏览器直接管理数据库
# 数据文件位于本目录（history-book.mv.db），Ctrl+C 停止服务
cd "$(dirname "$0")"
exec java -cp h2-2.2.224.jar org.h2.tools.Server -tcp -tcpPort 9092 -web -webPort 8082 -baseDir . -ifNotExists
