package com.historybook.controller.tongjian;

import com.historybook.common.core.web.controller.BaseController;
import com.historybook.common.core.web.domain.AjaxResult;
import com.historybook.common.security.annotation.RequiresPermissions;
import com.historybook.system.service.ITjGubenService;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 读通鉴-古本（元刊本扫描页查看）
 */
@RestController
@RequestMapping("/tongjian/guben")
public class TjGubenController extends BaseController
{
    @Resource
    private ITjGubenService gubenService;

    /**
     * 古本定位/翻页
     */
    @RequiresPermissions("tongjian:read:list")
    @PostMapping("/view")
    @ApiOperation(value = "古本查看")
    public AjaxResult view(@RequestBody Map<String, Object> payload)
    {
        return gubenService.view(payload);
    }

    /**
     * 启动全量回填（批量缓存锚点与扫描页到本地）
     */
    @RequiresPermissions("tongjian:read:list")
    @PostMapping("/crawl/start")
    @ApiOperation(value = "启动古本回填")
    public AjaxResult crawlStart()
    {
        return gubenService.startCrawl();
    }

    /**
     * 停止回填
     */
    @RequiresPermissions("tongjian:read:list")
    @PostMapping("/crawl/stop")
    @ApiOperation(value = "停止古本回填")
    public AjaxResult crawlStop()
    {
        return gubenService.stopCrawl();
    }

    /**
     * 回填进度与本地缓存统计
     */
    @RequiresPermissions("tongjian:read:list")
    @GetMapping("/crawl/status")
    @ApiOperation(value = "古本回填进度")
    public AjaxResult crawlStatus()
    {
        return gubenService.crawlStatus();
    }
}
