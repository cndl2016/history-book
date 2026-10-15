package com.historybook.system.entity.tongjian;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.historybook.common.core.web.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 读通鉴-古本扫描页缓存 tj_guben_page
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TjGubenPage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pageId;

    /** 扫描页文件名 */
    private String imageName;

    /** 上一页文件名（书版方向） */
    private String prevName;

    /** 下一页文件名 */
    private String nextName;

    /** 上游标记是否有上一页（1是 0否） */
    private Integer hasPrev;

    /** 上游标记是否有下一页（1是 0否） */
    private Integer hasNext;

    /** 本地文件相对 profile 根的路径（guben/xxx.jpg） */
    private String localFile;

    /** 本地文件字节数 */
    private Long fileSize;
}
