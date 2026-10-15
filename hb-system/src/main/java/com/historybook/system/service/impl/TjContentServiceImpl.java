package com.historybook.system.service.impl;

import com.historybook.system.entity.tongjian.TjContent;
import com.historybook.system.mapper.TjContentMapper;
import com.historybook.system.mapper.TjFavoriteMapper;
import com.historybook.system.service.ITjContentService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 读通鉴-正文 服务层处理
 */
@Service
public class TjContentServiceImpl implements ITjContentService
{
    @Resource
    private TjContentMapper contentMapper;

    @Resource
    private TjFavoriteMapper favoriteMapper;

    @Override
    public List<TjContent> selectByReignId(Long reignId, Long userId)
    {
        List<TjContent> list = contentMapper.selectByReignId(reignId);
        if (userId != null && !list.isEmpty())
        {
            Set<Long> favored = new HashSet<>(favoriteMapper.selectFavoredContentIds(userId, reignId));
            for (TjContent content : list)
            {
                content.setFavored(favored.contains(content.getContentId()));
            }
        }
        return list;
    }

    @Override
    public TjContent selectById(Long contentId)
    {
        return contentMapper.selectById(contentId);
    }

    @Override
    public List<TjContent> searchList(TjContent query)
    {
        return contentMapper.searchList(query);
    }
}
