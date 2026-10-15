package com.historybook.system.mapper;

import com.historybook.system.entity.tongjian.TjContent;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 读通鉴-正文段落 数据层
 */
public interface TjContentMapper
{
    /** 按纪年查询全部段落（按序号） */
    List<TjContent> selectByReignId(@Param("reignId") Long reignId);

    TjContent selectById(@Param("contentId") Long contentId);

    TjContent selectByTongjianId(@Param("tongjianId") String tongjianId);

    /** 全文搜索（原文/简体/译文/标签） */
    List<TjContent> searchList(TjContent query);

    /** 按标签查关联段落（精确匹配逗号分隔标签） */
    List<TjContent> selectByTag(@Param("tag") String tag);

    int insertContent(TjContent content);

    int insertContentBatch(@Param("list") List<TjContent> list);

    int deleteByReignId(@Param("reignId") Long reignId);

    int countContents();
}
