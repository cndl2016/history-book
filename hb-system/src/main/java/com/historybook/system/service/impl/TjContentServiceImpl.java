package com.historybook.system.service.impl;

import com.historybook.system.entity.tongjian.TjContent;
import com.historybook.system.mapper.TjContentMapper;
import com.historybook.system.mapper.TjFavoriteMapper;
import com.historybook.system.service.ITjContentService;
import com.historybook.system.service.ITjSandboxService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
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

    @Resource
    private ITjSandboxService sandboxService;

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
        // 标注哪些段落有可上图的地名（决定阅读页是否显示沙盘按钮）
        markSandboxable(list);
        return list;
    }

    /** 批量判断段落标签能否命中地名字典，结果写入 sandboxable */
    private void markSandboxable(List<TjContent> list)
    {
        Set<String> allTags = new HashSet<>();
        for (TjContent c : list)
        {
            if (c.getTags() != null)
            {
                allTags.addAll(Arrays.asList(c.getTags().split(",")));
            }
        }
        Set<String> matchable = allTags.isEmpty() ? new HashSet<>() : sandboxService.matchableTags(allTags);
        for (TjContent c : list)
        {
            boolean ok = false;
            if (c.getTags() != null)
            {
                for (String t : c.getTags().split(","))
                {
                    if (matchable.contains(t.trim()))
                    {
                        ok = true;
                        break;
                    }
                }
            }
            c.setSandboxable(ok);
        }
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

    @Override
    public List<TjContent> selectByTag(String tag)
    {
        return contentMapper.selectByTag(tag);
    }
}
