package com.historybook.system.mapper;

import com.historybook.system.entity.tongjian.TjContent;
import com.historybook.system.entity.tongjian.TjGubenAnchor;
import com.historybook.system.entity.tongjian.TjGubenPage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 读通鉴-古本本地缓存 数据层
 */
public interface TjGubenMapper
{
    /** 按文件名查扫描页 */
    TjGubenPage selectPage(@Param("imageName") String imageName);

    int insertPage(TjGubenPage page);

    /** 更新翻页标记（anchor/page 响应中的 has_prev/has_next） */
    int updatePageNavFlags(TjGubenPage page);

    /** 更新前后页链 */
    int updatePageLinks(@Param("imageName") String imageName, @Param("prevName") String prevName,
            @Param("nextName") String nextName);

    /** 记录本地文件信息 */
    int updatePageLocal(@Param("imageName") String imageName, @Param("localFile") String localFile,
            @Param("fileSize") long fileSize);

    /** 按段落查锚点 */
    TjGubenAnchor selectAnchor(@Param("tongjianId") String tongjianId, @Param("startIndex") Integer startIndex);

    int insertAnchor(TjGubenAnchor anchor);

    /** 尚未缓存锚点的段落（用于全量回填） */
    List<TjContent> selectUncachedParagraphs();

    int countAnchors();

    int countPages();

    int countLocalPages();

    Long sumLocalBytes();
}
