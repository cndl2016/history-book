package com.historybook.system.mapper;

import com.historybook.system.entity.tongjian.TjPlaceDict;
import com.historybook.system.entity.tongjian.TjReign;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 读通鉴-沙盘态势 数据层
 */
public interface TjSandboxMapper
{
    /** 段落的标签 */
    String selectParagraphTags(@Param("contentId") Long contentId);

    /** 段落所属纪年（含公元年份） */
    TjReign selectReignByContentId(@Param("contentId") Long contentId);

    /** 按名称查地名字典（精确 + 通名带政区后缀的前缀匹配） */
    List<TjPlaceDict> selectPlacesByName(@Param("name") String name);

    /** 字典全部地名（简体+繁体，用于内存匹配缓存） */
    List<String> selectAllDictNames();
}
