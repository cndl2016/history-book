package com.historybook.system.service.impl;

import com.historybook.system.entity.tongjian.TjFavorite;
import com.historybook.system.mapper.TjFavoriteMapper;
import com.historybook.system.service.ITjFavoriteService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 读通鉴-收藏 服务层处理
 */
@Service
public class TjFavoriteServiceImpl implements ITjFavoriteService
{
    @Resource
    private TjFavoriteMapper favoriteMapper;

    @Override
    public boolean addFavorite(Long userId, Long contentId)
    {
        if (favoriteMapper.countByUserAndContent(userId, contentId) > 0)
        {
            return false;
        }
        TjFavorite favorite = new TjFavorite();
        favorite.setUserId(userId);
        favorite.setContentId(contentId);
        return favoriteMapper.insertFavorite(favorite) > 0;
    }

    @Override
    public int removeFavorite(Long userId, Long contentId)
    {
        return favoriteMapper.deleteFavorite(userId, contentId);
    }

    @Override
    public List<TjFavorite> selectMyFavorites(Long userId)
    {
        return favoriteMapper.selectFavoriteList(userId);
    }
}
