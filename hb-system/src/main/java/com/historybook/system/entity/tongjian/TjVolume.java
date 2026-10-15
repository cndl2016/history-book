package com.historybook.system.entity.tongjian;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 读通鉴-卷 tj_volume
 */
@Data
public class TjVolume implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 卷ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long volumeId;

    /** 源站卷标识 */
    private String tongjianId;

    /** 卷名 */
    private String title;

    /** 纪名 */
    private String ji;

    /** 排序号 */
    private Integer sortOrder;

    /** 纪年子节点（目录树用） */
    private List<TjReign> children;
}
