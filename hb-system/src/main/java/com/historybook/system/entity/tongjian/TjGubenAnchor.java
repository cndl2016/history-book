package com.historybook.system.entity.tongjian;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.historybook.common.core.web.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 读通鉴-古本段落锚点缓存 tj_guben_anchor
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TjGubenAnchor extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long anchorId;

    /** 源站段落标识 */
    private String tongjianId;

    /** 段落在纪年内的序号（从0开始） */
    private Integer startIndex;

    /** 所在扫描页文件名 */
    private String imageName;

    /** 锚框顶边（原图像素） */
    private Integer boxTop;

    /** 锚框左边（原图像素） */
    private Integer boxLeft;

    /** 锚框宽（原图像素） */
    private Integer boxWidth;

    /** 锚框高（原图像素） */
    private Integer boxHeight;
}
