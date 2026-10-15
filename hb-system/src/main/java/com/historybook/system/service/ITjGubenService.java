package com.historybook.system.service;

import com.historybook.common.core.web.domain.AjaxResult;

import java.util.Map;

/**
 * 读通鉴-古本 服务层
 *
 * 本地缓存优先：命中 tj_guben_anchor / tj_guben_page 且图片已落盘时直接返回本地地址，
 * 不请求 dutongjian.com；未命中且配置了 tongjian.guben.token 时回源一次并缓存，
 * 之后同段落/同页永久离线可看。
 */
public interface ITjGubenService
{
    /**
     * 古本查看/定位/翻页
     *
     * @param payload mode=anchor 时含 paragraphTongjianId/startIndex/selectedText；
     *                mode=page 时含 imageName/direction
     */
    AjaxResult view(Map<String, Object> payload);

    /** 启动全量回填（异步，限速），已在运行则返回提示 */
    AjaxResult startCrawl();

    /** 请求停止回填 */
    AjaxResult stopCrawl();

    /** 回填进度与缓存统计 */
    AjaxResult crawlStatus();
}
