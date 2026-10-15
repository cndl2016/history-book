package com.historybook.system.service;

import com.historybook.system.entity.tongjian.TjSyncState;

/**
 * 读通鉴-内容同步 服务层
 * 从 dutongjian.com 公开 API 限速、断点续传地导入目录与正文。
 */
public interface ITjSyncService
{
    /** 启动后台同步（已在运行返回false） */
    boolean start();

    /** 请求停止（当前纪年完成后停止） */
    void stop();

    /** 当前同步状态与进度 */
    TjSyncState status();
}
