package com.historybook.system.mapper;

import com.historybook.system.entity.tongjian.TjHistory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 读通鉴-阅读记录 数据层
 */
public interface TjHistoryMapper
{
    TjHistory selectByUserAndReign(@Param("userId") Long userId, @Param("reignId") Long reignId);

    int insertHistory(TjHistory history);

    int updateHistory(TjHistory history);

    /** 我的阅读记录（关联纪年/卷，按时间倒序） */
    List<TjHistory> selectHistoryList(@Param("userId") Long userId);

    int deleteHistoryByIds(@Param("historyIds") Long[] historyIds, @Param("userId") Long userId);
}
