package com.historybook.system.service;

import com.historybook.system.entity.tongjian.TjHistory;

import java.util.List;

/**
 * 读通鉴-阅读记录 服务层
 */
public interface ITjHistoryService
{
    /** 记录/刷新某纪年的阅读进度 */
    void record(Long userId, Long reignId, Integer lastNumber);

    /** 我的阅读记录 */
    List<TjHistory> selectMyHistory(Long userId);

    int deleteByIds(Long[] historyIds, Long userId);
}
