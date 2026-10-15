package com.historybook.system.mapper;

import com.historybook.system.entity.tongjian.TjFavorite;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 读通鉴-收藏 数据层
 */
public interface TjFavoriteMapper
{
    int insertFavorite(TjFavorite favorite);

    int deleteFavorite(@Param("userId") Long userId, @Param("contentId") Long contentId);

    int countByUserAndContent(@Param("userId") Long userId, @Param("contentId") Long contentId);

    /** 我的收藏列表（关联段落/纪年/卷） */
    List<TjFavorite> selectFavoriteList(@Param("userId") Long userId);

    /** 某纪年下当前用户已收藏的段落ID */
    List<Long> selectFavoredContentIds(@Param("userId") Long userId, @Param("reignId") Long reignId);
}
