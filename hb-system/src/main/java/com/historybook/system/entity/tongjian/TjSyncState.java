package com.historybook.system.entity.tongjian;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 读通鉴-同步状态 tj_sync_state
 */
@Data
public class TjSyncState implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 固定为1 */
    private Long id;

    /** 纪年总数 */
    private Integer totalReigns;

    /** 已完成纪年数 */
    private Integer completedReigns;

    /** 已入库段落总数 */
    private Integer contentCount;

    /** 状态（0空闲 1运行中 2已停止） */
    private String status;

    /** 最近消息 */
    private String message;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 当前是否有同步线程在运行（非表字段） */
    private Boolean running;
}
