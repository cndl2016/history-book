package com.historybook.system.entity.tongjian;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.historybook.common.core.web.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 读通鉴-阅读记录 tj_history
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TjHistory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long historyId;

    /** 用户ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 纪年ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reignId;

    /** 最近读到的段落序号 */
    private Integer lastNumber;

    /** 最近阅读时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date accessTime;

    /** 帝王名（关联查询） */
    private String emperorName;

    /** 纪年名（关联查询） */
    private String reignName;

    /** 干支与公元标注（关联查询） */
    private String yearAnno;

    /** 所属卷ID（关联查询） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long volumeId;

    /** 所属卷名（关联查询） */
    private String volumeTitle;
}
