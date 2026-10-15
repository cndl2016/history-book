package com.historybook.system.service;

import com.historybook.system.entity.tongjian.TjReign;
import com.historybook.system.entity.tongjian.TjVolume;

import java.util.List;

/**
 * 读通鉴-目录 服务层
 */
public interface ITjCatalogService
{
    /** 目录树：卷 -> 纪年 */
    List<TjVolume> selectCatalogTree();

    /** 纪年详情（含卷名） */
    TjReign selectReignById(Long reignId);
}
