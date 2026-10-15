package com.historybook.controller.tongjian;

import com.historybook.common.core.web.controller.BaseController;
import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.common.core.web.page.TableDataInfo;
import com.historybook.common.security.annotation.RequiresPermissions;
import com.historybook.common.security.utils.SecurityUtils;
import com.historybook.system.entity.tongjian.TjHistory;
import com.historybook.system.service.ITjHistoryService;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 读通鉴-阅读记录
 */
@RestController
@RequestMapping("/tongjian/history")
public class TjHistoryController extends BaseController
{
    @Resource
    private ITjHistoryService historyService;

    /**
     * 我的阅读记录
     */
    @RequiresPermissions("tongjian:history:list")
    @GetMapping("/list")
    @ApiOperation(value = "我的阅读记录")
    public TableDataInfo list()
    {
        startPage();
        List<TjHistory> list = historyService.selectMyHistory(SecurityUtils.getUserId());
        return getDataTable(list);
    }

    /**
     * 记录阅读进度（打开纪年/翻段时调用）
     */
    @RequiresPermissions("tongjian:read:list")
    @PostMapping("/record/{reignId}/{lastNumber}")
    @ApiOperation(value = "记录阅读进度")
    public AjaxResult record(@PathVariable Long reignId, @PathVariable Integer lastNumber)
    {
        historyService.record(SecurityUtils.getUserId(), reignId, lastNumber);
        return AjaxResult.success();
    }

    /**
     * 删除阅读记录
     */
    @RequiresPermissions("tongjian:history:list")
    @DeleteMapping("/{historyIds}")
    @ApiOperation(value = "删除阅读记录")
    public AjaxResult remove(@PathVariable Long[] historyIds)
    {
        return toAjax(historyService.deleteByIds(historyIds, SecurityUtils.getUserId()));
    }
}
