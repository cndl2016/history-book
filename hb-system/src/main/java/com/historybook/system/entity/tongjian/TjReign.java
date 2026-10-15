package com.historybook.system.entity.tongjian;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

/**
 * 读通鉴-纪年 tj_reign
 */
@Data
public class TjReign implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 纪年ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reignId;

    /** 源站纪年标识 */
    private String tongjianId;

    /** 所属卷ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long volumeId;

    /** 帝王名 */
    private String emperorName;

    /** 纪年名 */
    private String reignName;

    /** 干支与公元标注 */
    private String yearAnno;

    /** 公元年份（公元前为负数） */
    private Integer yearInt;

    /** 排序号 */
    private Integer sortOrder;

    /** 正文是否已同步（0否 1是） */
    private String synced;

    /** 所属卷名（关联查询） */
    private String volumeTitle;

    /** 所属卷纪名（关联查询） */
    private String ji;
}
