package com.historybook.controller.tongjian;

import com.historybook.common.core.web.controller.BaseController;
import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.common.core.web.page.TableDataInfo;
import com.historybook.common.security.annotation.RequiresPermissions;
import com.historybook.common.security.utils.SecurityUtils;
import com.historybook.system.entity.tongjian.TjContent;
import com.historybook.system.service.ITjContentService;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 读通鉴-正文阅读与搜索
 */
@RestController
@RequestMapping("/tongjian/content")
public class TjContentController extends BaseController
{
    @Resource
    private ITjContentService contentService;

    /**
     * 某纪年下的全部段落（原文/简体/译文/注释/标签，带收藏标记）
     */
    @RequiresPermissions("tongjian:read:list")
    @GetMapping("/listByReign/{reignId}")
    @ApiOperation(value = "按纪年查询正文段落")
    public AjaxResult listByReign(@PathVariable Long reignId)
    {
        return AjaxResult.success(contentService.selectByReignId(reignId, SecurityUtils.getUserId()));
    }

    /**
     * 段落详情
     */
    @RequiresPermissions("tongjian:read:list")
    @GetMapping("/{contentId}")
    @ApiOperation(value = "段落详情")
    public AjaxResult getInfo(@PathVariable Long contentId)
    {
        return AjaxResult.success(contentService.selectById(contentId));
    }

    /**
     * 全文搜索（关键词匹配原文/简体/译文/标签，可按卷/纪年过滤）
     */
    @RequiresPermissions("tongjian:search:list")
    @GetMapping("/search")
    @ApiOperation(value = "全文搜索")
    public TableDataInfo search(TjContent query)
    {
        startPage();
        List<TjContent> list = contentService.searchList(query);
        return getDataTable(list);
    }

    /**
     * 按标签查关联段落（点击段落标签弹出）
     */
    @RequiresPermissions("tongjian:read:list")
    @GetMapping("/byTag")
    @ApiOperation(value = "按标签查关联段落")
    public TableDataInfo byTag(String tag)
    {
        startPage();
        List<TjContent> list = contentService.selectByTag(tag);
        return getDataTable(list);
    }
}
