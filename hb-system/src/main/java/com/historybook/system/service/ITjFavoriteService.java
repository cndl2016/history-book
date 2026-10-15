package com.historybook.system.service;

import com.historybook.system.entity.tongjian.TjFavorite;

import java.util.List;

/**
 * 读通鉴-收藏 服务层
 */
public interface ITjFavoriteService
{
    /** 收藏段落（重复收藏返回false） */
    boolean addFavorite(Long userId, Long contentId);

    int removeFavorite(Long userId, Long contentId);

    /** 我的收藏列表 */
    List<TjFavorite> selectMyFavorites(Long userId);
}
