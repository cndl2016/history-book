package com.historybook.system.mapper;

import com.historybook.system.entity.tongjian.TjReign;
import com.historybook.system.entity.tongjian.TjVolume;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 读通鉴-目录（卷/纪年） 数据层
 */
public interface TjCatalogMapper
{
    /** 全部卷（按排序号） */
    List<TjVolume> selectVolumeList();

    /** 全部纪年（按排序号） */
    List<TjReign> selectReignList();

    /** 按ID查纪年（含卷名） */
    TjReign selectReignById(@Param("reignId") Long reignId);

    /** 按源站标识查卷 */
    TjVolume selectVolumeByTongjianId(@Param("tongjianId") String tongjianId);

    /** 按源站标识查纪年 */
    TjReign selectReignByTongjianId(@Param("tongjianId") String tongjianId);

    int insertVolume(TjVolume volume);

    int updateVolume(TjVolume volume);

    int insertReign(TjReign reign);

    int updateReign(TjReign reign);

    /** 标记纪年正文已同步 */
    int markReignSynced(@Param("reignId") Long reignId);

    /** 统计纪年总数/已同步数 */
    int countReigns();

    int countSyncedReigns();
}
