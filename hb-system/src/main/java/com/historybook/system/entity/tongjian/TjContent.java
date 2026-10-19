package com.historybook.system.entity.tongjian;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.historybook.common.core.web.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 读通鉴-正文段落 tj_content
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TjContent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 段落ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 源站段落标识 */
    private String tongjianId;

    /** 所属纪年ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reignId;

    /** 所属卷ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long volumeId;

    /** 段落序号 */
    private Integer number;

    /** 繁体原文 */
    private String original;

    /** 简体正文 */
    private String simplified;

    /** 白话译文 */
    private String translation;

    /** 注释（JSON数组：胡注/陈注） */
    private String notes;

    /** 标签（逗号分隔） */
    private String tags;

    /** 来源地址 */
    private String sourceUrl;

    /** 当前用户是否已收藏（非表字段） */
    private Boolean favored;

    /** 是否有可上图的地名（非表字段，有则显示沙盘按钮） */
    private Boolean sandboxable;

    /** 所属纪年显示名（关联查询） */
    private String reignTitle;

    /** 所属卷名（关联查询） */
    private String volumeTitle;
}
