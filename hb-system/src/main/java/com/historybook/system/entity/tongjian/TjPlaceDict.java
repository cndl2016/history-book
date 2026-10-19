package com.historybook.system.entity.tongjian;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

/**
 * 读通鉴-历史地名字典 tj_place_dict（来源 CHGIS V6）
 */
@Data
public class TjPlaceDict implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long placeId;

    /** 繁体地名 */
    private String nameFt;

    /** 简体地名 */
    private String nameCh;

    /** 拼音名 */
    private String namePy;

    /** 经度（WGS84） */
    private Double xCoor;

    /** 纬度（WGS84） */
    private Double yCoor;

    /** 政区类型（州/县/军等） */
    private String typeCh;

    /** 今所在地 */
    private String presLoc;

    /** 始置年（公元前为负） */
    private Integer begYr;

    /** 废置年 */
    private Integer endYr;

    /** 数据来源 */
    private String source;
}
