package com.historybook.system.service;

import com.historybook.common.core.web.domain.AjaxResult;

/**
 * 读通鉴-沙盘态势 服务层
 */
public interface ITjSandboxService
{
    /** 段落沙盘：标出该段涉及的地名点位 */
    AjaxResult paragraph(Long contentId);

    /** 从给定标签集合里筛出能命中地名字典的标签 */
    java.util.Set<String> matchableTags(java.util.Collection<String> tags);
}
