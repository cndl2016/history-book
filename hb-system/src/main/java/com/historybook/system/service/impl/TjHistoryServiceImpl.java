package com.historybook.system.service.impl;

import com.historybook.system.entity.tongjian.TjHistory;
import com.historybook.system.mapper.TjHistoryMapper;
import com.historybook.system.service.ITjHistoryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 读通鉴-阅读记录 服务层处理
 */
@Service
public class TjHistoryServiceImpl implements ITjHistoryService
{
    @Resource
    private TjHistoryMapper historyMapper;

    @Override
    public void record(Long userId, Long reignId, Integer lastNumber)
    {
        if (userId == null || reignId == null)
        {
            return;
        }
        int number = lastNumber == null ? 0 : lastNumber;
        TjHistory exist = historyMapper.selectByUserAndReign(userId, reignId);
        if (exist == null)
        {
            TjHistory history = new TjHistory();
            history.setUserId(userId);
            history.setReignId(reignId);
            history.setLastNumber(number);
            historyMapper.insertHistory(history);
        }
        else if (number >= (exist.getLastNumber() == null ? 0 : exist.getLastNumber()))
        {
            exist.setLastNumber(number);
            historyMapper.updateHistory(exist);
        }
        else
        {
            // 仅刷新阅读时间
            historyMapper.updateHistory(exist);
        }
    }

    @Override
    public List<TjHistory> selectMyHistory(Long userId)
    {
        return historyMapper.selectHistoryList(userId);
    }

    @Override
    public int deleteByIds(Long[] historyIds, Long userId)
    {
        return historyMapper.deleteHistoryByIds(historyIds, userId);
    }
}
