package com.historybook.controller.tongjian;

import com.historybook.common.core.web.controller.BaseController;
import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.common.log.annotation.Log;
import com.historybook.common.log.enums.BusinessType;
import com.historybook.common.security.annotation.RequiresPermissions;
import com.historybook.system.service.ITjSyncService;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 读通鉴-内容同步
 * 从 dutongjian.com 公开接口限速导入，断点续传。
 */
@RestController
@RequestMapping("/tongjian/sync")
public class TjSyncController extends BaseController
{
    @Resource
    private ITjSyncService syncService;

    /**
     * 同步状态与进度
     */
    @RequiresPermissions("tongjian:sync:list")
    @GetMapping("/status")
    @ApiOperation(value = "同步状态")
    public AjaxResult status()
    {
        return AjaxResult.success(syncService.status());
    }

    /**
     * 启动后台同步
     */
    @RequiresPermissions("tongjian:sync:start")
    @Log(title = "读通鉴同步", businessType = BusinessType.OTHER)
    @PostMapping("/start")
    @ApiOperation(value = "启动同步")
    public AjaxResult start()
    {
        boolean ok = syncService.start();
        return ok ? AjaxResult.success("同步已启动，多线程导入中，可离开页面稍后查看")
                  : AjaxResult.error("同步正在运行中");
    }

    /**
     * 请求停止同步
     */
    @RequiresPermissions("tongjian:sync:stop")
    @Log(title = "读通鉴同步", businessType = BusinessType.OTHER)
    @PostMapping("/stop")
    @ApiOperation(value = "停止同步")
    public AjaxResult stop()
    {
        syncService.stop();
        return AjaxResult.success("已请求停止，当前纪年完成后即停");
    }
}
