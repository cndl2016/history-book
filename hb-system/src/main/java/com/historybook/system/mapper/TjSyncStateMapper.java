package com.historybook.system.mapper;

import com.historybook.system.entity.tongjian.TjSyncState;

/**
 * 读通鉴-同步状态 数据层
 */
public interface TjSyncStateMapper
{
    TjSyncState selectState();

    int insertState(TjSyncState state);

    int updateState(TjSyncState state);
}
