package com.historybook.system.service;

import com.historybook.system.entity.tongjian.TjContent;

import java.util.List;

/**
 * 读通鉴-正文 服务层
 */
public interface ITjContentService
{
    /** 某纪年下的全部段落（带当前用户收藏标记） */
    List<TjContent> selectByReignId(Long reignId, Long userId);

    /** 段落详情 */
    TjContent selectById(Long contentId);

    /** 全文搜索（分页由 PageHelper 处理） */
    List<TjContent> searchList(TjContent query);
}
