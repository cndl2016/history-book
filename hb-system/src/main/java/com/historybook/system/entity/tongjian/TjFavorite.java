package com.historybook.system.entity.tongjian;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.historybook.common.core.web.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 读通鉴-收藏 tj_favorite
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TjFavorite extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 收藏ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long favoriteId;

    /** 用户ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 段落ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 关联展示的段落（非表字段） */
    private TjContent content;
}
